package com.zincmusic.app.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.atomic.AtomicInteger

/**
 * Persistence + active fetching for the Firebase Cloud Messaging identifiers
 * shown in Settings → Notifications → Push diagnostics.
 *
 * v1.1.6 only stored values delivered by the passive service callbacks
 * (onRegistered / onNewToken). If the device never completes its Firebase
 * registration those callbacks never fire, and the screen sat on
 * "registering… / fetching…" forever with a Refresh button that only re-read
 * empty SharedPreferences. This version actively fetches both identifiers:
 *
 *  - [FirebaseInstallations.getInstance][FirebaseInstallations].id  → Installation ID
 *  - [FirebaseMessaging.getInstance][FirebaseMessaging].token      → Registration token
 *
 * Both the resulting values AND the failure reasons are persisted, so the
 * Settings screen can always show *why* a fetch has not succeeded.
 */
object PushDiagnostics {

    private const val TAG = "PushDiagnostics"
    private const val PREFS = "push_diagnostics"
    private const val KEY_TOKEN = "registration_token"
    private const val KEY_FID = "installation_id"
    private const val KEY_TOKEN_ERROR = "registration_token_error"
    private const val KEY_FID_ERROR = "installation_id_error"

    /** Number of Firebase tasks currently in flight (drives the UI spinner). */
    private val pending = AtomicInteger(0)

    /** True while an active fetch is still waiting for Firebase. */
    val fetchInFlight: Boolean
        get() = pending.get() > 0

    fun storeToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).remove(KEY_TOKEN_ERROR).apply()
    }

    fun storeInstallationId(context: Context, installationId: String) {
        prefs(context).edit().putString(KEY_FID, installationId).remove(KEY_FID_ERROR).apply()
    }

    fun getToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)

    fun getInstallationId(context: Context): String? =
        prefs(context).getString(KEY_FID, null)

    fun getTokenError(context: Context): String? =
        prefs(context).getString(KEY_TOKEN_ERROR, null)

    fun getInstallationIdError(context: Context): String? =
        prefs(context).getString(KEY_FID_ERROR, null)

    /**
     * Actively (re)fetches the Installation ID and the FCM registration token.
     * Safe to call repeatedly — Firebase caches resolved values and only hits
     * the network when registration genuinely needs to (re)run. Failures are
     * persisted so the diagnostics screen can display the exact reason.
     */
    fun fetch(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val appContext = context.applicationContext

        // Installation ID — the authoritative Firebase Installations identifier.
        pending.incrementAndGet()
        try {
            FirebaseInstallations.getInstance().id
                .addOnSuccessListener { fid ->
                    storeInstallationId(appContext, fid)
                    Log.d(TAG, "Installation ID fetched: $fid")
                }
                .addOnFailureListener { e ->
                    recordError(appContext, KEY_FID_ERROR, "Installation ID", e)
                }
                .addOnCompleteListener { pending.decrementAndGet() }
        } catch (e: Exception) {
            recordError(appContext, KEY_FID_ERROR, "Installation ID", e)
            pending.decrementAndGet()
        }

        // Registration token — required for Firebase Console test messages.
        pending.incrementAndGet()
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    storeToken(appContext, token)
                    Log.d(TAG, "Registration token fetched (${token.length} chars)")
                }
                .addOnFailureListener { e ->
                    recordError(appContext, KEY_TOKEN_ERROR, "Registration token", e)
                }
                .addOnCompleteListener { pending.decrementAndGet() }
        } catch (e: Exception) {
            recordError(appContext, KEY_TOKEN_ERROR, "Registration token", e)
            pending.decrementAndGet()
        }
    }

    private fun recordError(context: Context, key: String, what: String, e: Exception?) {
        val message = e?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "unknown error"
        // Keep it single-line and bounded — it is rendered in a compact UI row.
        val trimmed = message.replace('\n', ' ').take(160)
        prefs(context).edit().putString(key, trimmed).apply()
        Log.w(TAG, "$what fetch failed: $trimmed")
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
