package com.mudita.mmd.samples.hermes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Notification policy, shaped around how MuditaOS K treats notifications:
 * there is no tray, and only alarms, calendar events and calls reach the screen.
 *
 *  - Normal agent messages: no notification at all. They land in [Inbox].
 *  - Urgent agent messages: one alarm-style full-screen intent, which is the same path
 *    alarms use, plus a silent fallback notification for stock Android.
 *  - The listener's foreground-service notification is required by Android; it is
 *    created at minimum importance so it never sounds or vibrates.
 */
object Notifier {
    const val CHANNEL_LISTENER = "listener"
    const val CHANNEL_URGENT = "urgent"
    const val LISTENER_NOTIFICATION_ID = 1

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LISTENER, "Connection", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps the link to your agent open. Silent."
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_URGENT, "Urgent from agent", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Only messages your agent marks as urgent."
                setShowBadge(false)
                enableVibration(true)
            },
        )
    }

    fun listenerNotification(context: Context): Notification =
        Notification.Builder(context, CHANNEL_LISTENER)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("Hermes is listening")
            .setOngoing(true)
            .setContentIntent(mainIntent(context))
            .build()

    fun showUrgent(context: Context, item: InboxItem) {
        val alert = Intent(context, UrgentAlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(UrgentAlertActivity.EXTRA_TITLE, item.title)
            .putExtra(UrgentAlertActivity.EXTRA_MESSAGE, item.message)
        val full = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            alert,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, CHANNEL_URGENT)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(item.title.ifBlank { "Hermes" })
            .setContentText(item.message)
            .setStyle(Notification.BigTextStyle().bigText(item.message))
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(item.id.hashCode(), n)
    }

    private fun mainIntent(context: Context) = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
