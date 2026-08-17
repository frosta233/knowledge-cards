package com.example.knowledgecards.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.knowledgecards.domain.AccentColor
import com.example.knowledgecards.domain.ThemeMode
import com.example.knowledgecards.ui.theme.appTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onImport: () -> Unit,
    onImportArchive: () -> Unit,
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
                TextButton(onClick = viewModel::consumeExportResult) { Text("好的") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(colors = appTopAppBarColors(), title = { Text("设置") })
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            SettingSectionTitle("主题")
            Text(
                text = "深色 / 浅色 / 跟随系统",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.label()) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Text(
                text = "强调色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                AccentColor.entries.forEach { color ->
                    FilterChip(
                        selected = settings.accentColor == color,
                        onClick = { viewModel.setAccentColor(color) },
                        label = { Text(color.label()) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 6.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SettingSectionTitle("正文字号 ${settings.fontSizeSp.toInt()}sp")
            Text(
                text = "主界面闪卡正文",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = settings.fontSizeSp,
                onValueChange = viewModel::setFontSize,
                valueRange = 12f..28f,
                steps = 15
            )

            SettingSectionTitle("微件字号 ${settings.widgetFontSizeSp.toInt()}sp")
            Text(
                text = "桌面微件正文",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = settings.widgetFontSizeSp,
                onValueChange = viewModel::setWidgetFontSize,
                valueRange = 12f..28f,
                steps = 15
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SettingSectionTitle("数据")
            Text(
                text = "导入：选择 Markdown 文件夹，或 .zip / .tar 压缩包（卡片分类按文件夹层级生成）。导出：全部卡片备份为文件夹。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)) {
                Button(
                    onClick = onImport,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Text("导入文件夹")
                }
                OutlinedButton(
                    onClick = onImportArchive,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Text("导入压缩包")
                }
                OutlinedButton(
                    onClick = onExport,
                    enabled = !exporting
                ) {
                    Text(if (exporting) "导出中…" else "导出备份")
                }
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

private fun AccentColor.label(): String = when (this) {
    AccentColor.SYSTEM -> "动态取色"
    AccentColor.GREEN -> "绿色"
    AccentColor.BLUE -> "蓝色"
    AccentColor.ORANGE -> "橙色"
    AccentColor.PURPLE -> "紫色"
    AccentColor.SAGE -> "莫兰迪绿"
    AccentColor.DUSTY_BLUE -> "莫兰迪蓝"
    AccentColor.TERRACOTTA -> "莫兰迪陶土"
}
