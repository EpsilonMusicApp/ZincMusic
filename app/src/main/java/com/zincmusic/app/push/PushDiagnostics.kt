package com.zincmusic.app.push

import android.content.Context

/**
 * Tiny persistence layer for the Firebase Cloud Messaging identifiers.
 *
 * The Settings → Notifications screen surfaces the registration token and the
 * Firebase Installation ID so the developer can paste them into the Firebase
 * Console's "Send test message" field (Engage → Messaging → New campaign →
 * Send test message). Both values are written by [ZincFirebaseMessagingService]
 * callbacks and by the Application startup token fetch, then read here without
 * any async dance.
 */
object PushDiagnostics {

    private const val PREFS = "push_diagnostics"
    private const val KEY_TOKEN = "registration_token"
    private const val KEY_FID = "installation_id"

    fun storeToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TOKEN, token).apply()
    }

    fun storeInstallationId(context: Context, installationId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_FID, installationId).apply()
    }

    fun getToken(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)

    fun getInstallationId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FID, null)
}
