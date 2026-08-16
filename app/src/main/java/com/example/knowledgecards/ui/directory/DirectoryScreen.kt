package com.example.knowledgecards.ui.directory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.knowledgecards.domain.CategoryNode

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

    LaunchedEffect(tree) {
        viewModel.expandRoots(tree)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("目录") })
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
        ) {
            item(key = "all") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAll)
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
    viewModel: DirectoryViewModel,
    onOpenCategory: (String, Long) -> Unit,
    onOpenCard: (String, Long) -> Unit
) {
    val isExpanded = node.fullPath in expanded
    item(key = node.fullPath) {
        NodeRow(node, depth, isExpanded, viewModel, onOpenCategory)
    }
    if (isExpanded) {
        node.children.forEach { child ->
            renderNode(child, depth + 1, expanded, viewModel, onOpenCategory, onOpenCard)
        }
        node.cards.forEach { card ->
            item(key = "card-${card.id}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCard(card.path, card.id) }
                        .padding(start = 36.dp + (depth * 20).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
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
    viewModel: DirectoryViewModel,
    onOpenCategory: (String, Long) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(node.name) }
    var showDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.firstCardId(node)?.let { onOpenCategory(node.fullPath, it) } }
            .padding(start = 12.dp + (depth * 20).dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.children.isNotEmpty()) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown
                else Icons.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { viewModel.toggle(node.fullPath) }
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
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "管理分类")
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
