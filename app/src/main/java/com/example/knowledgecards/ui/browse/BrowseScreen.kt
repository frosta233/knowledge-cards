package com.example.knowledgecards.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.knowledgecards.data.Card
import com.example.knowledgecards.ui.theme.appTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onOpenEditor: (Long) -> Unit,
    onOpenBookshelf: () -> Unit
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val cards = ui.cards
    val pagerState = rememberPagerState(pageCount = { cards.size })

    // Cards of the book whose position the pager currently shows. The initial
    // value is set from the first non-empty list, so switching books is what
    // resets the pager — not the first load.
    var activeBookId by rememberSaveable { mutableStateOf(-1L) }
    var initialized by rememberSaveable { mutableStateOf(false) }

    // Initial position: start card or last restored progress. When the screen
    // is recomposed with state already restored (tab switch), still realign
    // with the shared progress — the widget may have flipped cards meanwhile.
    // A book switch instead lands on that book's first card, otherwise the
    // pager would keep an index that means something else in the new book.
    LaunchedEffect(cards, ui.bookId) {
        if (cards.isEmpty()) return@LaunchedEffect
        if (!initialized || ui.bookId != activeBookId) {
            val switchingBook = initialized && ui.bookId != activeBookId
            activeBookId = ui.bookId
            val index = if (switchingBook) 0 else viewModel.initialIndex(cards)
            if (index != pagerState.currentPage) pagerState.scrollToPage(index)
            initialized = true
        }
        viewModel.resumeToSharedProgress(
            cards.getOrNull(pagerState.currentPage)?.id ?: 0L
        )
    }

    // When the sort mode changes, follow the last browsed card to its new
    // position instead of staying on a now-arbitrary index.
    LaunchedEffect(ui.sortMode, cards) {
        if (initialized && cards.isNotEmpty()) {
            val index = viewModel.initialIndex(cards)
            if (index >= 0 && index != pagerState.currentPage) {
                pagerState.scrollToPage(index)
            }
        }
    }

    // Jump requests from the directory screen or the widget.
    val pendingJump by viewModel.pendingJump.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJump, cards) {
        if (pendingJump > 0L && cards.isNotEmpty()) {
            val index = cards.indexOfFirst { it.id == pendingJump }
            if (index >= 0) pagerState.animateScrollToPage(index)
            viewModel.consumeJump()
        }
    }

    // Save progress whenever a page settles.
    LaunchedEffect(pagerState, cards) {
        snapshotFlow { pagerState.settledPage }
            .collect { page -> cards.getOrNull(page)?.let { viewModel.onCardShown(it.id) } }
    }

    // Save progress when leaving the screen (spec: save on leaving browse);
    // on resume, realign with the shared progress (widget may have moved it).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val state = viewModel.uiState.value
                    viewModel.resumeToSharedProgress(
                        state.cards.getOrNull(pagerState.currentPage)?.id ?: 0L
                    )
                }
                Lifecycle.Event.ON_PAUSE -> {
                    cards.getOrNull(pagerState.currentPage)?.let { viewModel.onCardShown(it.id) }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            colors = appTopAppBarColors(),
            title = {
                val current = cards.getOrNull(pagerState.currentPage)
                // "书名 · 分类" — the same header as the widget, so the book you
                // are studying is always visible. Ellipsis only if it overflows.
                val path = current?.path?.ifBlank { "未分类" }
                val title = when {
                    current == null -> ui.bookName.ifEmpty { "闪卡" }
                    ui.bookName.isEmpty() -> path.orEmpty()
                    else -> "${ui.bookName} · $path"
                }
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            actions = {
                if (ui.scopePath.isNotEmpty()) {
                    TextButton(onClick = viewModel::clearScope) {
                        Text("全部")
                    }
                }
            }
        )
        Box(modifier = Modifier.fillMaxSize()) {
            if (cards.isEmpty()) {
                EmptyLibrary(bookName = ui.bookName, onOpenBookshelf = onOpenBookshelf)
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        pageSpacing = 12.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { page ->
                        CardPage(
                            card = cards[page],
                            fontSizeSp = ui.fontSizeSp,
                            onEdit = { onOpenEditor(cards[page].id) }
                        )
                    }
                    Text(
                        text = "第 ${pagerState.currentPage + 1} / 共 ${cards.size} 张",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CardPage(
    card: Card,
    fontSizeSp: Float,
    onEdit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = card.content.ifBlank { "（空卡片）" },
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.55f).sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )
            }
            // Edit entry lives on the card itself.
            FilledTonalIconButton(
                onClick = onEdit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "编辑",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(bookName: String, onOpenBookshelf: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(
            text = if (bookName.isEmpty()) "书架还是空的" else "《$bookName》里还没有卡片",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = "在「书架」页导入 .zip / .tar 压缩包或 Markdown 文件夹，\n每个压缩包就是一本独立的书。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onOpenBookshelf,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("去书架导入")
        }
    }
}
