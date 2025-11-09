package com.exp.hentai1.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setDiskCacheSize(sizeInMB: Long) {
        prefs.edit().putLong(KEY_DISK_CACHE_SIZE, sizeInMB).apply()
    }

    fun getDiskCacheSize(): Long {
        return prefs.getLong(KEY_DISK_CACHE_SIZE, DEFAULT_DISK_CACHE_SIZE_MB)
    }

    fun setMemoryCachePercent(percent: Float) {
        prefs.edit().putFloat(KEY_MEMORY_CACHE_PERCENT, percent).apply()
    }

    fun getMemoryCachePercent(): Float {
        return prefs.getFloat(KEY_MEMORY_CACHE_PERCENT, DEFAULT_MEMORY_CACHE_PERCENT)
    }

    fun setLastClearLog(log: String) {
        prefs.edit().putString(KEY_LAST_CLEAR_LOG, log).apply()
    }

    fun getLastClearLog(): String {
        return prefs.getString(KEY_LAST_CLEAR_LOG, "从未清理过") ?: "从未清理过"
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_DISK_CACHE_SIZE = "disk_cache_size_mb"
        private const val KEY_MEMORY_CACHE_PERCENT = "memory_cache_percent"
        private const val KEY_LAST_CLEAR_LOG = "last_clear_log"
        const val DEFAULT_DISK_CACHE_SIZE_MB = 500L // 500MB
        const val DEFAULT_MEMORY_CACHE_PERCENT = 0.25f // 可用内存的 25%
    }
}
