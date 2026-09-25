package com.zincmusic.app

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.zincmusic.app.data.innertube.InnerTubeClient
import com.zincmusic.app.data.innertube.NewPipeStreamExtractor
import com.zincmusic.app.util.CacheManager
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch

@HiltAndroidApp
class ZincMusicApplication : Application(), ImageLoaderFactory {

    companion object {
        lateinit var instance: ZincMusicApplication
            private set
    }


    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { InnerTubeClient.httpClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Dedicated high-speed memory cache (25% of available application heap)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250 * 1024 * 1024) // 250MB size quota for offline artwork persistent caching
                    .build()
            }
            .crossfade(180) // Light 180ms fade — expressive without adding scroll compositing cost
            .allowHardware(true) // Offload rendering directly to GPU hardware buffers for zero UI thread lag
            .allowRgb565(false) // Disable 16-bit compression to ensure pure ARGB_8888 color resolution on premium displays
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    @SuppressLint("VisibleForTests")
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Proactive Initialization: Connect localized persistence cache to high-speed networking engine
        InnerTubeClient.initialize(this)

        // NOTE: FFmpeg native library now loads lazily on first playback instead of at
        // launch — this keeps the critical startup window free of background CPU contention
        // so the first frames and the very first scroll stay smooth.
        
        // Initialize global theme mode
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES 
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Apply dynamic colors to all activities
        DynamicColors.applyToActivitiesIfAvailable(this)

        // Load saved YouTube Music cookies for logged-in features
        val savedCookie = prefs.getString("yt_cookies", null)
        if (savedCookie != null) {
            InnerTubeClient.cookie = savedCookie
        }

        // Background Warm-up: Initialize NewPipe, OkHttp, and TLS to YouTube CDN
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                // 1. Warm up OkHttp TCP+TLS connection to YouTube CDN
                InnerTubeClient.httpClient.newCall(
                    okhttp3.Request.Builder()
                        .url("https://www.youtube.com/generate_204")
                        .head()
                        .build()
                ).execute().close()
                Log.d("ZincMusicApp", "Successfully warmed OkHttp connection to YouTube CDN")
            } catch (e: Exception) {
                Log.w("ZincMusicApp", "Failed to warm up OkHttp connection: ${e.message}")
            }

            try {
                // 2. Register the stream extractor (fast, purely local setup).
                //    The heavy JavaScript engine now warms up lazily on the first
                //    playback instead of fighting the UI thread during launch.
                NewPipeStreamExtractor.init(this@ZincMusicApplication)
            } catch (e: Exception) {
                Log.w("ZincMusicApp", "Failed to init stream extractor: ${e.message}")
            }
        }

        // Suppress expected Glide warnings for missing album arts
        Glide.init(this, GlideBuilder().setLogLevel(Log.ERROR))

        // Clean up old temp files on app start
        CacheManager.clearOldCache(this)
    }
}
