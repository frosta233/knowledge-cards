package com.example.knowledgecards

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.knowledgecards.data.CardRepository
import com.example.knowledgecards.data.import.ArchiveImporter
import com.example.knowledgecards.data.import.BookImporter
import com.example.knowledgecards.data.import.BookImportResult
import com.example.knowledgecards.data.import.ImportResult
import com.example.knowledgecards.domain.AppSettings
import com.example.knowledgecards.domain.ThemeMode
import com.example.knowledgecards.ui.bookshelf.BookshelfScreen
import com.example.knowledgecards.ui.bookshelf.BookshelfViewModel
import com.example.knowledgecards.ui.browse.BrowseScreen
import com.example.knowledgecards.ui.browse.BrowseViewModel
import com.example.knowledgecards.ui.directory.DirectoryScreen
import com.example.knowledgecards.ui.directory.DirectoryViewModel
import com.example.knowledgecards.ui.editor.EditorScreen
import com.example.knowledgecards.ui.editor.EditorViewModel
import com.example.knowledgecards.ui.settings.SettingsScreen
import com.example.knowledgecards.ui.settings.SettingsViewModel
import com.example.knowledgecards.ui.theme.KnowledgeCardsTheme
import com.example.knowledgecards.widget.CardWidgetActions
import com.example.knowledgecards.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MainTab(val label: String) {
    FLASHCARDS("闪卡"),
    DIRECTORY("目录"),
    BOOKSHELF("书架"),
    SETTINGS("设置")
}

class MainActivity : ComponentActivity() {

    private val browseViewModel: BrowseViewModel by viewModels()
    private val directoryViewModel: DirectoryViewModel by viewModels()
    private val bookshelfViewModel: BookshelfViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private var importingState by mutableStateOf<ImportingState?>(null)

    private val importTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) importFromTree(uri)
        }

    private val archiveLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importArchive(uri)
        }

    private val exportTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                settingsViewModel.exportTo(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (application as KnowledgeCardsApp).container
            val settings by container.progressStore.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            // Make the system status/navigation bar icons follow the app theme
            // (the XML theme always requests light icons, which are invisible
            // on the dark accent-colored app bars).
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            KnowledgeCardsTheme(
                themeMode = settings.themeMode,
                accentColor = settings.accentColor
            ) {
                MainNav()
            }
        }
        handleWidgetIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent) {
        if (intent.hasExtra(CardWidgetActions.EXTRA_CARD_ID)) {
            val cardId = intent.getLongExtra(CardWidgetActions.EXTRA_CARD_ID, 0L)
            if (cardId > 0) {
                browseViewModel.jumpTo(cardId)
                // Keep the shared progress in sync so the widget and the
                // foreground-resume alignment both agree on the opened card.
                lifecycleScope.launch {
                    (application as KnowledgeCardsApp).container.progressStore
                        .setLastCardId(cardId)
                }
            }
        }
    }

    private fun importArchive(uri: Uri) {
        if (!ArchiveImporter.supports(uri)) {
            importingState = ImportingState.DONE(
                ImportResult(
                    failed = listOf(
                        uri.lastPathSegment.orEmpty() to ArchiveImporter.unsupportedReason(uri)
                    )
                )
            )
            return
        }
        runImport { BookImporter(it).importArchive(this@MainActivity, uri) }
    }

    private fun importFromTree(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        runImport { BookImporter(it).importTree(this@MainActivity, uri) }
    }

    /**
     * Runs one import (archive or folder) into a book, then makes that book the
     * selected one so 目录 / 闪卡 immediately show what was just imported.
     */
    private fun runImport(block: suspend (CardRepository) -> BookImportResult) {
        lifecycleScope.launch {
            importingState = ImportingState.RUNNING
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val container = (application as KnowledgeCardsApp).container
                    block(container.cardRepository)
                }
            }
            outcome.fold(
                onSuccess = { imported ->
                    if (imported.imported > 0) {
                        withContext(Dispatchers.IO) {
                            (application as KnowledgeCardsApp).container.progressStore
                                .setCurrentBookId(imported.bookId)
                        }
                        browseViewModel.clearScope()
                        directoryViewModel.resetExpansion()
                    }
                    importingState = ImportingState.DONE(
                        result = imported.result,
                        bookName = imported.bookName,
                        createdBook = imported.createdBook
                    )
                },
                onFailure = {
                    importingState = ImportingState.DONE(
                        result = ImportResult(
                            failed = listOf("导入失败" to (it.message ?: "未知错误"))
                        )
                    )
                }
            )
            WidgetUpdater.update(this@MainActivity)
        }
    }

    private fun editorViewModel(cardId: Long): EditorViewModel =
        ViewModelProvider(this, EditorViewModel.factory(cardId))[EditorViewModel::class.java]

    @Composable
    private fun MainNav() {
        var selectedTab by rememberSaveable { mutableStateOf(MainTab.FLASHCARDS) }
        var editingCardId by rememberSaveable { mutableStateOf<Long?>(null) }

        // Full-screen editor (hides the bottom bar).
        val editingId = editingCardId
        if (editingId != null) {
            val editorVm = remember(editingId) { editorViewModel(editingId) }
            EditorScreen(
                viewModel = editorVm,
                onBack = { editingCardId = null }
            )
            return
        }

        // Plain Column instead of Scaffold: each tab screen draws its own
        // TopAppBar with proper status-bar insets; a nested Scaffold would
        // double-apply insets and leave a blank strip on top.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when (selectedTab) {
                MainTab.FLASHCARDS -> BrowseScreen(
                    viewModel = browseViewModel,
                    onOpenEditor = { editingCardId = it },
                    onOpenBookshelf = { selectedTab = MainTab.BOOKSHELF }
                )

                MainTab.DIRECTORY -> DirectoryScreen(
                    viewModel = directoryViewModel,
                    onOpenAll = {
                        browseViewModel.setScope("", 0L)
                        selectedTab = MainTab.FLASHCARDS
                    },
                    onOpenCategory = { scope, firstCardId ->
                        browseViewModel.setScope(scope, firstCardId)
                        selectedTab = MainTab.FLASHCARDS
                    },
                    onOpenCard = { scope, cardId ->
                        browseViewModel.setScope(scope, cardId)
                        selectedTab = MainTab.FLASHCARDS
                    }
                )

                MainTab.BOOKSHELF -> BookshelfScreen(
                    viewModel = bookshelfViewModel,
                    onOpenBook = { bookId ->
                        bookshelfViewModel.selectBook(bookId)
                        browseViewModel.clearScope()
                        directoryViewModel.resetExpansion()
                        selectedTab = MainTab.DIRECTORY
                    },
                    onImportArchive = {
                        archiveLauncher.launch(
                            arrayOf("application/zip", "application/x-tar")
                        )
                    }
                )

                MainTab.SETTINGS -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onImport = { importTreeLauncher.launch(null) },
                    onImportArchive = {
                        archiveLauncher.launch(
                            arrayOf("application/zip", "application/x-tar")
                        )
                    },
                    onExport = { exportTreeLauncher.launch(null) }
                )
                }
            }
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        // Icon-only when unselected; icon + label when selected
                        // (label fades in/out, icon recenters — M3 pattern).
                        alwaysShowLabel = false,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            // The label sits on the nav-bar container (below the
                            // indicator pill), so it uses the content text color
                            // (onSurface) — not onPrimary, which is dark-on-dark
                            // in dark mode and unreadable.
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        ),
                        icon = {
                            when (tab) {
                                MainTab.FLASHCARDS -> Icon(
                                    imageVector = Icons.Filled.Home,
                                    contentDescription = tab.label
                                )
                                MainTab.DIRECTORY -> Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = tab.label
                                )
                                MainTab.BOOKSHELF -> Icon(
                                    painter = painterResource(R.drawable.ic_bookshelf),
                                    contentDescription = tab.label
                                )
                                MainTab.SETTINGS -> Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = tab.label
                                )
                            }
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }

        when (val state = importingState) {
            is ImportingState.RUNNING -> AlertDialog(
                onDismissRequest = {},
                title = { Text("正在导入…") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.size(width = 120.dp, height = 60.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    }
                },
                confirmButton = {}
            )

            is ImportingState.DONE -> ImportResultDialog(
                result = state.result,
                bookName = state.bookName,
                createdBook = state.createdBook,
                onDismiss = { importingState = null }
            )

            null -> Unit
        }
    }
}

private sealed interface ImportingState {
    data object RUNNING : ImportingState
    data class DONE(
        val result: ImportResult,
        /** Book the import went into; null when the import failed outright. */
        val bookName: String? = null,
        val createdBook: Boolean = false
    ) : ImportingState
}

@Composable
private fun ImportResultDialog(
    result: ImportResult,
    bookName: String?,
    createdBook: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入完成") },
        text = {
            Column {
                if (bookName != null) {
                    Text(
                        text = if (createdBook) "已放入新书架《$bookName》" else "已更新书架《$bookName》",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text("新增：${result.newCount} 张")
                Text("更新：${result.updatedCount} 张")
                if (result.failed.isNotEmpty()) {
                    Text(
                        text = "失败：${result.failed.size} 张",
                        color = MaterialTheme.colorScheme.error
                    )
                    result.failed.take(10).forEach { (name, reason) ->
                        Text(
                            text = "· $name：$reason",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (result.failed.size > 10) {
                        Text("… 等共 ${result.failed.size} 项")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("好的") }
        }
    )
}
