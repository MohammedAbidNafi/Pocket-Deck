package com.abidnafi.pocketdeck.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.abidnafi.pocketdeck.MainActivity
import com.abidnafi.pocketdeck.R

/**
 * The "Back to PocketDeck" notification shown while a game launched from here is running.
 * Tapping it brings PocketDeck's existing task to the front (MainActivity is singleTask).
 */
class ReturnNotifier(private val context: Context) {

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(gameTitle: String) {
        if (!canPost()) return
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Back to ${context.getString(R.string.app_name)}")
            .setContentText("Playing $gameTitle · tap to return")
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setAutoCancel(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, notification) }
    }

    fun hide() {
        NotificationManagerCompat.from(context).cancel(ID)
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        // Creating again is allowed and refreshes the name/description (e.g. after the app was renamed).
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Back to PocketDeck", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while a game is running so you can jump back to PocketDeck"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL = "return_to_hub"
        private const val ID = 4201

        /** Opens (or brings back) PocketDeck from outside the app: notification, tile, controller shortcut. */
        fun openAppIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, 0, launchIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun launchIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
}
