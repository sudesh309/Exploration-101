package com.kidlearn.app

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KidLearnApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Install/refresh bundled courses in the background (only after an app install/update).
        appScope.launch { container.data.syncBundledPacks(appVersionCode()) }
    }

    /**
     * Image loading tuned for 3 GB phones: a small memory cache, RGB_565 decoding where the image
     * has no transparency (half the memory of ARGB_8888), and a small disk cache that only
     * matters for packs that reference online images.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(50L * 1024 * 1024).build() }
        .allowRgb565(true)
        .crossfade(false)
        .respectCacheHeaders(false)
        .build()

    private fun appVersionCode(): Long {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        // lastUpdateTime makes debug re-installs with the same versionCode refresh too.
        return code * 31 + info.lastUpdateTime
    }
}
