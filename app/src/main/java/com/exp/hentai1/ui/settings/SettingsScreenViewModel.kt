package com.exp.hentai1.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exp.hentai1.MyApplication
import com.exp.hentai1.data.cache.AppCache
import com.exp.hentai1.data.cache.ClearAllResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CacheUiState(
    val memoryCacheSizeMB: Float = 0f,
    val maxMemoryCacheSizeMB: Float = 0f,
    val memoryCachePercent: Float = 0f, // 新增：内存缓存百分比
    val diskCacheSizeMB: Float = 0f,
    val maxDiskCacheSizeMB: Float = 0f,
    val lastClearLog: String = "从未清理过" // 添加上次清理日志字段
)

class SettingsScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = (application as MyApplication).settingsRepository

    private val _uiState = MutableStateFlow(CacheUiState())
    val uiState: StateFlow<CacheUiState> = _uiState.asStateFlow()

    init {
        refreshCacheState()
    }

    fun onClearAllCachesClicked() {
        viewModelScope.launch {
            val result: ClearAllResult = withContext(Dispatchers.IO) {
                AppCache.clearAllCaches()
            }
            val log = createLogMessage(result)
            settingsRepository.setLastClearLog(log)
            refreshCacheState()
        }
    }

    private fun createLogMessage(result: ClearAllResult): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        return String.format(
            Locale.US,
            "于 %s 清理了 %d 个文件, 释放了 %.2f MB",
            timestamp,
            result.filesDeleted,
            result.totalSizeClearedMB
        )
    }

    fun onDiskCacheSizeChanged(newSizeInMB: String) {
        val size = newSizeInMB.toLongOrNull()
        if (size != null && size > 0) {
            settingsRepository.setDiskCacheSize(size)
            refreshCacheState()
        }
    }

    fun onMemoryCachePercentChanged(newPercent: String) {
        val percent = newPercent.toFloatOrNull()
        if (percent != null && percent in 0.01f..1.00f) { // 限制在 1% 到 100% 之间
            settingsRepository.setMemoryCachePercent(percent)
            refreshCacheState()
        }
    }

    fun refreshCacheState() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = CacheUiState(
                memoryCacheSizeMB = AppCache.getMemoryCacheSizeInMB(),
                maxMemoryCacheSizeMB = AppCache.getMaxMemoryCacheSizeInMB(),
                memoryCachePercent = AppCache.getMemoryCachePercent(), // 获取内存缓存百分比
                diskCacheSizeMB = AppCache.getDiskCacheSizeInMB(),
                maxDiskCacheSizeMB = settingsRepository.getDiskCacheSize().toFloat(),
                lastClearLog = settingsRepository.getLastClearLog() // 从仓库读取并更新
            )
        }
    }
}
