package com.zincmusic.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
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
import com.zincmusic.app.push.PushDiagnostics
import com.zincmusic.app.util.CacheManager
import com.google.android.material.color.DynamicColors
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch

@HiltAndroidApp
class ZincMusicApplication : Application(), ImageLoaderFactory {

    companion object {
        private const val TAG = "ZincMusicApp"

        /** Notification channel used by Firebase Cloud Messaging announcements. */
        const val ANNOUNCEMENT_CHANNEL_ID = "zinc_announcements"

        lateinit var instance: ZincMusicApplication
            private set

        /** True while at least one activity of this process is started (app visible). */
        @Volatile
        var isAppInForeground: Boolean = false
            internal set

        private var startedActivityCount = 0
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

        registerForegroundTracker()
        ensureAnnouncementChannel()
        initFirebaseMessaging()
    }

    /**
     * Tracks whether the app is currently visible so FCM announcements can be
     * routed either to the in-app banner (foreground) or a system notification
     * (background). Uses only process-level lifecycle callbacks — zero overhead.
     */
    private fun registerForegroundTracker() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                isAppInForeground = true
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                isAppInForeground = startedActivityCount > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** Creates the channel FCM uses for announcements while the app is backgrounded. */
    private fun ensureAnnouncementChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ANNOUNCEMENT_CHANNEL_ID,
                getString(R.string.notif_channel_announcements),
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /**
     * Subscribes every install to the broadcast topic "all" — target it from the
     * Firebase Console (Engage > Messaging > New campaign > Topic: all) to reach
     * all users. When no Firebase config is committed (no google-services.json)
     * the app stays fully functional and all Firebase features remain dormant.
     */
    private fun initFirebaseMessaging() {
        try {
            if (FirebaseApp.getApps(this).isNotEmpty()) {
                FirebaseMessaging.getInstance().subscribeToTopic("all")
                // Actively fetch the Installation ID and the registration token
                // so both are available in Settings → Notifications for Firebase
                // Console test messages right after install — instead of only
                // waiting for the passive onRegistered/onNewToken callbacks,
                // which never fire when the device registration stalls.
                PushDiagnostics.fetch(this)
                Log.i(TAG, "Firebase ready: analytics, crashlytics and topic 'all' subscribed")
            } else {
                Log.i(TAG, "Firebase config not present (no google-services.json) - telemetry dormant")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase initialization skipped: ${e.message}")
        }
    }
}
