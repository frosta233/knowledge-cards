package com.example.knowledgecards.ui.directory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.knowledgecards.domain.CategoryNode

/**
 * Multi-level category tree. Tapping a category row starts browsing from its
 * first card; tapping the chevron expands/collapses; tapping a leaf card
 * jumps to that card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryScreen(
    viewModel: DirectoryViewModel,
    onOpenCard: (Long) -> Unit
) {
    val tree by viewModel.tree.collectAsStateWithLifecycle()
    val expanded by viewModel.expanded.collectAsStateWithLifecycle()

    LaunchedEffect(tree) {
        viewModel.expandRoots(tree)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("目录") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            tree.forEach { node ->
                renderNode(node, depth = 0, expanded = expanded, viewModel = viewModel, onOpenCard = onOpenCard)
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
    onOpenCard: (Long) -> Unit
) {
    val isExpanded = node.fullPath in expanded
    item(key = node.fullPath) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.firstCardId(node)?.let(onOpenCard) }
                .padding(start = 12.dp + (depth * 20).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
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
        }
    }
    if (isExpanded) {
        node.children.forEach { child ->
            renderNode(child, depth + 1, expanded, viewModel, onOpenCard)
        }
        node.cards.forEach { card ->
            item(key = "card-${card.id}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCard(card.id) }
                        .padding(start = 36.dp + (depth * 20).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = card.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
