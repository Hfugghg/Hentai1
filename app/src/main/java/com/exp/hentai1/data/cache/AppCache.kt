package com.exp.hentai1.data.cache

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import java.io.File

data class ClearAllResult(
    val filesDeleted: Int,
    val totalSizeClearedMB: Float
)

@OptIn(ExperimentalCoilApi::class)
object AppCache {
    private lateinit var imageLoader: ImageLoader

    fun initialize(context: Context) {
        this.imageLoader = Coil.imageLoader(context)
    }

    // --- 内存缓存方法 ---

    fun getMemoryCacheSizeInMB(): Float {
        return (imageLoader.memoryCache?.size ?: 0) / 1024f / 1024f
    }

    fun getMaxMemoryCacheSizeInMB(): Float {
        return (imageLoader.memoryCache?.maxSize ?: 0) / 1024f / 1024f
    }

    // --- 磁盘缓存方法 ---

    fun getDiskCacheSizeInMB(): Float {
        return (imageLoader.diskCache?.size ?: 0) / 1024f / 1024f
    }

    // --- 组合方法 ---

    fun clearAllCaches(): ClearAllResult {
        val diskCache = imageLoader.diskCache
        val memoryCache = imageLoader.memoryCache

        var filesCount = 0
        // 将 okio.Path 转换为 java.io.File
        val directory: File? = diskCache?.directory?.toFile()
        if (directory != null && directory.exists()) {
            filesCount = directory.listFiles { file -> file.isFile }?.size ?: 0
        }

        val memorySize = memoryCache?.size ?: 0L
        val diskSize = diskCache?.size ?: 0L
        val totalCleared = (memorySize.toFloat() + diskSize.toFloat()) / 1024f / 1024f

        memoryCache?.clear()
        diskCache?.clear()

        return ClearAllResult(filesCount, totalCleared)
    }
}
