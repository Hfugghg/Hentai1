package com.exp.hentai1

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.exp.hentai1.data.SettingsRepository
import com.exp.hentai1.data.cache.AppCache

class MyApplication : Application(), ImageLoaderFactory {

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        AppCache.initialize(this)
    }

    override fun newImageLoader(): ImageLoader {
        val diskCacheSize = settingsRepository.getDiskCacheSize() * 1024 * 1024 // Convert MB to Bytes
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Default is 0.25
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
}
