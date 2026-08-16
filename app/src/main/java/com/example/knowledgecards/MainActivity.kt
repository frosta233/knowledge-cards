package com.example.knowledgecards

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.knowledgecards.data.import.CardImporter
import com.example.knowledgecards.data.import.ImportResult
import com.example.knowledgecards.data.import.SafTreeScanner
import com.example.knowledgecards.domain.AppSettings
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
    SETTINGS("设置")
}

class MainActivity : ComponentActivity() {

    private val browseViewModel: BrowseViewModel by viewModels()
    private val directoryViewModel: DirectoryViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private var importingState by mutableStateOf<ImportingState?>(null)

    private val importTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) importFromTree(uri)
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
            if (cardId > 0) browseViewModel.jumpTo(cardId)
        }
    }

    private fun importFromTree(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        lifecycleScope.launch {
            importingState = ImportingState.RUNNING
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val container = (application as KnowledgeCardsApp).container
                    val files = SafTreeScanner.scan(this@MainActivity, uri)
                    CardImporter(container.cardRepository).import(files)
                }
            }
            importingState = result.fold(
                onSuccess = { ImportingState.DONE(it) },
                onFailure = {
                    ImportingState.DONE(
                        ImportResult(failed = listOf("导入失败" to (it.message ?: "未知错误")))
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
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when (selectedTab) {
                MainTab.FLASHCARDS -> BrowseScreen(
                    viewModel = browseViewModel,
                    onOpenEditor = { editingCardId = it },
                    onImport = { importTreeLauncher.launch(null) }
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

                MainTab.SETTINGS -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onImport = { importTreeLauncher.launch(null) },
                    onExport = { exportTreeLauncher.launch(null) }
                )
                }
            }
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    MainTab.FLASHCARDS -> Icons.Filled.Home
                                    MainTab.DIRECTORY -> Icons.AutoMirrored.Filled.List
                                    MainTab.SETTINGS -> Icons.Filled.Settings
                                },
                                contentDescription = tab.label
                            )
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
                onDismiss = { importingState = null }
            )

            null -> Unit
        }
    }
}

private sealed interface ImportingState {
    data object RUNNING : ImportingState
    data class DONE(val result: ImportResult) : ImportingState
}

@Composable
private fun ImportResultDialog(result: ImportResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入完成") },
        text = {
            Column {
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
