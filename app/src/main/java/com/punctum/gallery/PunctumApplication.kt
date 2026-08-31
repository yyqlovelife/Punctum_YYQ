package com.punctum.gallery

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.punctum.gallery.data.PhotoStillFetcher

class PunctumApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(PhotoStillFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.35)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_gallery_cache"))
                    .maxSizePercent(0.06)
                    .build()
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
}
