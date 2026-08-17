package com.example.knowledgecards.ui.directory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.knowledgecards.domain.CategoryNode
import com.example.knowledgecards.ui.theme.appTopAppBarColors
import kotlinx.coroutines.delay

/**
 * M3 list reorder animation for rows inserted/removed on expand/collapse.
 * Note: must be called as `Modifier.animateItem(...)` directly inside
 * `item { }` — LazyItemScope is a @DslMarker scope, so a helper extension
 * declared outside the scope cannot resolve its implicit receiver.
 * Specs (M3 standard easing): fade in ~220ms, reflow via non-bouncy spring,
 * fade out ~120ms on collapse.
 */
private const val ExpandMs = 220
private const val CollapseFadeMs = 120
private val EnterSlide = 10.dp

/**
 * Entrance animation for rows that appear when a node expands (M3 list
 * expansion): the row slides down from its parent row while fading in.
 * Removal on collapse is handled by [Modifier.animateItem] on the same
 * container, which fades the row out and glides siblings into place.
 */
@Composable
private fun EnterAnimatingContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = true
    val slidePx = with(LocalDensity.current) { EnterSlide.roundToPx() }
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = slideInVertically(
            animationSpec = tween(ExpandMs, easing = FastOutSlowInEasing),
            initialOffsetY = { -slidePx }
        )
    ) { content() }
}

/**
 * Multi-level category tree with card-set management:
 * tapping a category starts browsing from its first card; the chevron
 * expands/collapses; the ⋮ menu moves, renames or deletes the category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryScreen(
    viewModel: DirectoryViewModel,
    onOpenAll: () -> Unit,
    onOpenCategory: (String, Long) -> Unit,
    onOpenCard: (String, Long) -> Unit
) {
    val tree by viewModel.tree.collectAsStateWithLifecycle()
    val expanded by viewModel.expanded.collectAsStateWithLifecycle()
    val highlightCardId by viewModel.highlightCardId.collectAsStateWithLifecycle()
    val highlightCategoryPath by viewModel.highlightCategoryPath.collectAsStateWithLifecycle()
    val revealCategoryPath by viewModel.revealCategoryPath.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(tree) {
        viewModel.expandRoots(tree)
    }

    // Scroll a search-revealed target into view so it is visible in the tree.
    // Scroll to a card revealed by search (after the expansion animation).
    LaunchedEffect(highlightCardId) {
        if (highlightCardId > 0L) {
            val index = computeCardIndex(tree, expanded, highlightCardId)
            if (index > 0) {
                delay((ExpandMs + 40).toLong())
                listState.animateScrollToItem(index)
            }
        }
    }

    // Scroll to a category revealed by search.
    LaunchedEffect(revealCategoryPath) {
        if (revealCategoryPath.isNotEmpty()) {
            val index = computeCategoryIndex(tree, expanded, revealCategoryPath)
            if (index > 0) {
                delay((ExpandMs + 40).toLong())
                listState.animateScrollToItem(index)
            }
            viewModel.consumeRevealCategory()
        }
    }

    if (searchOpen) {
        DirectorySearchOverlay(
            viewModel = viewModel,
            onClose = {
                viewModel.closeSearch()
                searchOpen = false
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            colors = appTopAppBarColors(),
            title = { Text("目录") },
            actions = {
                IconButton(onClick = { searchOpen = true }) {
                    Icon(Icons.Filled.Search, contentDescription = "检索")
                }
            }
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
        ) {
            item(key = "all") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.clearHighlight()
                            onOpenAll()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "全部卡片",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                    Text(
                        text = "${tree.sumOf { it.totalCards }}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            tree.forEach { node ->
                renderNode(
                    node = node,
                    depth = 0,
                    expanded = expanded,
                    highlightCardId = highlightCardId,
                    highlightCategoryPath = highlightCategoryPath,
                    viewModel = viewModel,
                    onOpenCategory = onOpenCategory,
                    onOpenCard = onOpenCard
                )
            }
            if (tree.isEmpty()) {
                item {
                    Text(
                        text = "（暂无卡片，请先在「闪卡」页或「设置」页导入）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderNode(
    node: CategoryNode,
    depth: Int,
    expanded: Set<String>,
    highlightCardId: Long,
    highlightCategoryPath: String,
    viewModel: DirectoryViewModel,
    onOpenCategory: (String, Long) -> Unit,
    onOpenCard: (String, Long) -> Unit
) {
    val isExpanded = node.fullPath in expanded
    item(key = node.fullPath) {
        val row: @Composable () -> Unit = {
            NodeRow(node, depth, isExpanded, highlightCategoryPath, viewModel, onOpenCategory)
        }
        // Only rows inserted by an expansion animate in; the top-level rows
        // are always present and must not replay their entrance on tab switches.
        if (depth > 0) {
            EnterAnimatingContent(modifier = Modifier.animateItem(
                fadeInSpec = tween(ExpandMs, easing = FastOutSlowInEasing),
                placementSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                fadeOutSpec = tween(CollapseFadeMs, easing = FastOutSlowInEasing)
            )) { row() }
        } else {
            row()
        }
    }
    if (isExpanded) {
        node.children.forEach { child ->
            renderNode(child, depth + 1, expanded, highlightCardId, highlightCategoryPath, viewModel, onOpenCategory, onOpenCard)
        }
        node.cards.forEach { card ->
            item(key = "card-${card.id}") {
                val highlighted = card.id == highlightCardId
                EnterAnimatingContent(modifier = Modifier.animateItem(
                    fadeInSpec = tween(ExpandMs, easing = FastOutSlowInEasing),
                    placementSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    fadeOutSpec = tween(CollapseFadeMs, easing = FastOutSlowInEasing)
                )) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (highlighted) {
                                    Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                viewModel.clearHighlight()
                                onOpenCard(card.path, card.id)
                            }
                            .padding(start = 36.dp + (depth * 20).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = card.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Flat index of a card row inside the tree LazyColumn (0 = the "all cards"
 * row, then nodes depth-first exactly like [renderNode]), or 0 when not found.
 */
private fun computeCardIndex(
    nodes: List<CategoryNode>,
    expanded: Set<String>,
    cardId: Long
): Int {
    var index = 0
    fun walk(list: List<CategoryNode>): Boolean {
        for (node in list) {
            index += 1 // node row
            if (node.fullPath in expanded) {
                if (walk(node.children)) return true
                for (card in node.cards) {
                    index += 1
                    if (card.id == cardId) return true
                }
            }
        }
        return false
    }
    return if (walk(nodes)) index else 0
}

/**
 * Flat index of a category row inside the tree LazyColumn, or 0 when the
 * path is not found (the node must be expanded to be visible).
 */
private fun computeCategoryIndex(
    nodes: List<CategoryNode>,
    expanded: Set<String>,
    path: String
): Int {
    var index = 0
    fun walk(list: List<CategoryNode>): Boolean {
        for (node in list) {
            index += 1 // node row
            if (node.fullPath == path) return true
            if (node.fullPath in expanded) {
                if (walk(node.children)) return true
                index += node.cards.size
            }
        }
        return false
    }
    return if (walk(nodes)) index else 0
}

/**
 * Full-screen search: type a keyword to see matching categories and cards.
 * Tapping a result closes the search and reveals it in the tree itself —
 * a category expands its subtree, a card expands its direct parent and is
 * highlighted, so search results reuse the directory UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectorySearchOverlay(
    viewModel: DirectoryViewModel,
    onClose: () -> Unit
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            colors = appTopAppBarColors(),
            title = { Text("检索") },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setSearchQuery,
            placeholder = { Text("输入目录名或闪卡标题") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Search results: matching categories and matching cards.
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            when {
                query.isBlank() -> item {
                    Text(
                        text = "输入关键词检索目录或闪卡",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                results.categories.isEmpty() && results.cards.isEmpty() -> item {
                    Text(
                        text = "没有匹配「${query.trim()}」的目录或闪卡",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                else -> {
                    if (results.categories.isNotEmpty()) {
                        item(key = "section-categories") {
                            Text(
                                text = "目录",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                            )
                        }
                        results.categories.forEach { hit ->
                            item(key = "cat-${hit.fullPath}") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.revealCategory(hit.fullPath)
                                            onClose()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = hit.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = hit.fullPath,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${hit.totalCards}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (results.cards.isNotEmpty()) {
                        item(key = "section-cards") {
                            Text(
                                text = "闪卡",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                            )
                        }
                        results.cards.forEach { card ->
                            item(key = "card-${card.id}") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.revealCard(card)
                                            onClose()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = card.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = card.path.ifEmpty { "未分类" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeRow(
    node: CategoryNode,
    depth: Int,
    isExpanded: Boolean,
    highlightCategoryPath: String,
    viewModel: DirectoryViewModel,
    onOpenCategory: (String, Long) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(node.name) }
    var showDelete by remember { mutableStateOf(false) }

    val highlighted = node.fullPath == highlightCategoryPath

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                } else {
                    Modifier
                }
            )
            .clickable {
                viewModel.clearHighlight()
                viewModel.firstCardId(node)?.let { onOpenCategory(node.fullPath, it) }
            }
            .padding(start = 12.dp + (depth * 20).dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.children.isNotEmpty() || node.cards.isNotEmpty()) {
            // Chevron rotates 90° (right → down) with M3 standard easing.
            val chevronRotation by animateFloatAsState(
                targetValue = if (isExpanded) 90f else 0f,
                animationSpec = tween(ExpandMs, easing = FastOutSlowInEasing),
                label = "chevronRotation"
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .graphicsLayer { rotationZ = chevronRotation }
                    // Circular press ripple: clip the clickable area to a
                    // circle whose diameter equals the icon bounds (24dp).
                    .clip(CircleShape)
                    .clickable {
                        viewModel.clearHighlight()
                        viewModel.toggle(node.fullPath)
                    }
            )
        } else {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (node.cards.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
        Text(
            text = "${node.totalCards}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
        // The menu must share a Box with its anchor button, otherwise the
        // popup anchors to the enclosing Row and appears at the screen edge.
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "管理分类",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("上移") },
                    onClick = {
                        menuOpen = false
                        viewModel.moveCategory(node.fullPath, -1)
                    }
                )
                DropdownMenuItem(
                    text = { Text("下移") },
                    onClick = {
                        menuOpen = false
                        viewModel.moveCategory(node.fullPath, +1)
                    }
                )
                DropdownMenuItem(
                    text = { Text("重命名…") },
                    onClick = {
                        menuOpen = false
                        renameValue = node.name
                        showRename = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除…") },
                    onClick = {
                        menuOpen = false
                        showDelete = true
                    }
                )
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("重命名分类") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("新名称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRename = false
                    viewModel.renameCategory(node.fullPath, renameValue)
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消") }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除分类") },
            text = { Text("「${node.name}」包含 ${node.totalCards} 张卡片。要如何处理这些卡片？") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deleteCategory(node.fullPath, deleteCards = true)
                }) { Text("删除卡片") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showDelete = false
                        viewModel.deleteCategory(node.fullPath, deleteCards = false)
                    }) { Text("移入未分类") }
                    TextButton(onClick = { showDelete = false }) { Text("取消") }
                }
            }
        )
    }
}
