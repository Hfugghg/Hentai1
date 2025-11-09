package com.exp.hentai1.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider // 使用 HorizontalDivider 替代 Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exp.hentai1.ui.common.CacheUsageBar

@Composable
fun SettingsScreen(
    viewModel: SettingsScreenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDiskCacheDialog by remember { mutableStateOf(false) }
    var newDiskCacheSize by remember { mutableStateOf("") }

    var showMemoryCacheDialog by remember { mutableStateOf(false) } // 新增：控制内存缓存设置弹窗
    var newMemoryCachePercent by remember { mutableStateOf("") } // 新增：内存缓存百分比输入值

    // 屏幕首次组合时刷新缓存状态
    LaunchedEffect(Unit) {
        viewModel.refreshCacheState()
    }

    // 磁盘缓存设置弹窗
    if (showDiskCacheDialog) {
        AlertDialog(
            onDismissRequest = { showDiskCacheDialog = false },
            title = { Text("设置磁盘缓存上限") },
            text = {
                OutlinedTextField(
                    value = newDiskCacheSize,
                    onValueChange = { newDiskCacheSize = it.filter { c -> c.isDigit() } },
                    label = { Text("大小 (MB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDiskCacheSizeChanged(newDiskCacheSize)
                        showDiskCacheDialog = false
                        Toast.makeText(context, "设置已保存，重启应用后生效", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiskCacheDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 内存缓存设置弹窗
    if (showMemoryCacheDialog) {
        AlertDialog(
            onDismissRequest = { showMemoryCacheDialog = false },
            title = { Text("设置内存缓存百分比") },
            text = {
                OutlinedTextField(
                    value = newMemoryCachePercent,
                    onValueChange = { newValue ->
                        newMemoryCachePercent = newValue.filter { it.isDigit() }
                    },
                    label = { Text("百分比 (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val percent = newMemoryCachePercent.toFloatOrNull()
                        if (percent != null && percent >= 1 && percent <= 100) {
                            viewModel.onMemoryCachePercentChanged((percent / 100f).toString()) // 转换为0.0-1.0
                            showMemoryCacheDialog = false
                            Toast.makeText(context, "设置已保存，重启应用后生效", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "请输入1到100之间的有效百分比", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMemoryCacheDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("缓存管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // 内存缓存
        Text("内存缓存 (快速读取)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.clickable { // 添加点击事件
            newMemoryCachePercent = (uiState.memoryCachePercent * 100).toInt().toString() // 预填充当前值
            showMemoryCacheDialog = true
        }) {
            CacheUsageBar(
                currentSizeMB = uiState.memoryCacheSizeMB,
                maxSizeMB = uiState.maxMemoryCacheSizeMB
            )
        }
        Text(
            "用于加速重复打开图片的读取速度，应用关闭后自动清空。点击上方可设置百分比，重启后生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 磁盘缓存
        Text("磁盘缓存 (节省流量)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.clickable {
            newDiskCacheSize = uiState.maxDiskCacheSizeMB.toInt().toString() // 预填充当前值
            showDiskCacheDialog = true
        }) {
            CacheUsageBar(
                currentSizeMB = uiState.diskCacheSizeMB,
                maxSizeMB = uiState.maxDiskCacheSizeMB
            )
        }
        Text(
            "保存已加载的图片，避免重复下载。点击上方可设置上限，重启后生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider() // 使用 HorizontalDivider
        Spacer(modifier = Modifier.height(24.dp))

        // --- 操作 ---
        Button(
            onClick = {
                viewModel.onClearAllCachesClicked()
                Toast.makeText(context, "缓存已清空", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("一键清空所有缓存")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "上次清理: ${uiState.lastClearLog}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
