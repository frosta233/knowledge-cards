package com.example.knowledgecards.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.knowledgecards.R
import com.example.knowledgecards.ui.theme.appTopAppBarColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 书架：every import is one book. Tapping a book selects it and opens its
 * directory; the ⋮ menu renames or removes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    onOpenBook: (Long) -> Unit,
    onImportArchive: () -> Unit
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val books = ui.books

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            colors = appTopAppBarColors(),
            title = {
                Column {
                    Text("书架", style = MaterialTheme.typography.titleMedium)
                    if (books.isNotEmpty()) {
                        Text(
                            text = "共 ${books.size} 本 · ${ui.totalCards} 张卡片",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = onImportArchive) {
                    Icon(Icons.Filled.Add, contentDescription = "导入压缩包")
                }
            }
        )
        if (books.isEmpty()) {
            EmptyShelf(onImportArchive)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(books, key = { it.id }) { book ->
                    BookRow(
                        book = book,
                        isCurrent = book.id == ui.currentBookId,
                        onOpen = { onOpenBook(book.id) },
                        onRename = { viewModel.renameBook(book.id, it) },
                        onDelete = { viewModel.deleteBook(book.id) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun BookRow(
    book: BookItem,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(book.name) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCurrent) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bookshelf),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = book.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${book.cardCount} 张卡片 · ${book.categoryCount} 个目录 · ${formatDate(book.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "当前书",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "管理书架",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("重命名…") },
                    onClick = {
                        menuOpen = false
                        renameValue = book.name
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
            title = { Text("重命名书架") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("书名") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRename = false
                    onRename(renameValue)
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
            title = { Text("删除《${book.name}》") },
            text = {
                Text("这本书里的 ${book.cardCount} 张卡片会一并删除，无法恢复。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    onDelete()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EmptyShelf(onImportArchive: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bookshelf),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = "书架还是空的",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "每次导入一个 .zip / .tar 压缩包（或一个 Markdown 文件夹）" +
                "就是一本新书，目录只显示当前选中的书。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = onImportArchive,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("导入压缩包")
        }
    }
}

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")

private fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(dateFormatter)
