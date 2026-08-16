package com.example.knowledgecards.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.knowledgecards.data.SortMode
import com.example.knowledgecards.domain.AppSettings
import com.example.knowledgecards.domain.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val exportResult by viewModel.exportResult.collectAsStateWithLifecycle()

    if (exportResult != null) {
        val result = exportResult!!
        AlertDialog(
            onDismissRequest = viewModel::consumeExportResult,
            title = { Text("导出完成") },
            text = { Text("成功导出 ${result.first} 个文件${if (result.second > 0) "，失败 ${result.second} 个" else ""}。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = viewModel::consumeExportResult) { Text("好的") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            SettingSectionTitle("主题")
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.label()) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SettingSectionTitle("浏览顺序")
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = settings.sortMode == SortMode.TITLE,
                    onClick = { viewModel.setSortMode(SortMode.TITLE) }
                )
                Text("标题序（按标题排序）")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = settings.sortMode == SortMode.IMPORT,
                    onClick = { viewModel.setSortMode(SortMode.IMPORT) }
                )
                Text("导入序（按导入先后）")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SettingSectionTitle("字号 ${settings.fontSizeSp.toInt()}sp")
            Slider(
                value = settings.fontSizeSp,
                onValueChange = viewModel::setFontSize,
                valueRange = 12f..28f,
                steps = 15
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SettingSectionTitle("备份")
            Text(
                text = "将全部卡片导出为 Markdown 文件夹，可用导入功能恢复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onExport,
                enabled = !exporting,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            ) {
                Text(if (exporting) "导出中…" else "导出备份（选择目标文件夹）")
            }
        }
    }
}

@Composable
private fun SettingSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}
