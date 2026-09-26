package com.zincmusic.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zincmusic.app.R
import com.zincmusic.app.ZincMusicApplication

/**
 * Receives Firebase Cloud Messages sent from the Firebase Console
 * (Engage > Messaging > New campaign, target the topic "all").
 *
 * Routing:
 *  - App in the FOREGROUND: the message is shown as an in-app banner that slides
 *    in from the top of the screen (no notification permission needed).
 *  - App in the BACKGROUND / killed: notification-payload messages are
 *    auto-displayed by FCM itself (using the default channel / icon declared in
 *    AndroidManifest.xml), while data-payload messages are shown here as a
 *    heads-up system notification.
 */
class ZincFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: getString(R.string.app_name)
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: ""

        if (title.isBlank() && body.isBlank()) return

        if (ZincMusicApplication.isAppInForeground) {
            // In-app banner — guaranteed visible while the user is inside the app.
            InAppNotificationCenter.post(title, body)
        } else {
            // Only reached for data-only payloads; FCM already auto-displays
            // notification payloads on its own when the app is backgrounded.
            showSystemNotification(title, body)
        }
    }

    override fun onRegistered(installationId: String) {
        // Modern FID-based registration (firebase-messaging 25.1+, enabled via
        // the firebase_messaging_installation_id_enabled manifest flag). Fires
        // on app startup and routine syncs while auto-init is enabled. The FID
        // is persisted so the Settings screen can surface it for test sends.
        PushDiagnostics.storeInstallationId(this, installationId)
        Log.d(TAG, "Registered with Firebase installation ID")
    }

    override fun onNewToken(token: String) {
        // Legacy registration token callback (deprecated in favour of FIDs but
        // still delivered). Topic subscriptions survive token rotation on the
        // server side, so there is nothing to re-subscribe here.
        PushDiagnostics.storeToken(this, token)
        Log.d(TAG, "FCM registration token was rotated")
    }

    private fun showSystemNotification(title: String, body: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ZincMusicApplication.ANNOUNCEMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        if (notificationsAllowed) {
            try {
                NotificationManagerCompat.from(this).notify(ANNOUNCEMENT_NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                Log.w(TAG, "Announcement notification suppressed: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "ZincFCM"
        private const val ANNOUNCEMENT_NOTIFICATION_ID = 4001
    }
}
