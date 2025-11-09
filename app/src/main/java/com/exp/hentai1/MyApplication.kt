package com.exp.hentai1

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.exp.hentai1.data.SettingsRepository
import com.exp.hentai1.data.cache.AppCache
import java.util.concurrent.Executors

class MyApplication : Application(), ImageLoaderFactory, Configuration.Provider { // 实现 Configuration.Provider 接口

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        AppCache.initialize(this)
    }

    override fun newImageLoader(): ImageLoader {
        val diskCacheSize = settingsRepository.getDiskCacheSize() * 1024 * 1024 // Convert MB to Bytes
        val memoryCachePercent = settingsRepository.getMemoryCachePercent() // Get memory cache percent from settings
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memoryCachePercent.toDouble()) // Use the stored percentage
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(diskCacheSize)
                    .build()
            }
            .build()
    }

    // 实现 WorkManager 的配置提供者
    override val workManagerConfiguration: Configuration // 再次修改为 val 属性
        get() = Configuration.Builder()
            .setExecutor(Executors.newFixedThreadPool(5)) // 限制同时运行的 Worker 数量为 5
            .setMinimumLoggingLevel(android.util.Log.DEBUG) // 可以根据需要调整日志级别
            .build()
}
