package com.tunnellight.stop_scroll.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tunnellight.stop_scroll.MainActivity
import com.tunnellight.stop_scroll.R
import com.tunnellight.stop_scroll.util.Format

/** The two nudges StopScroll sends: the daily goal being passed, and a bout running long. */
class ScrollNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GOAL,
                "Daily goal",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Tells you when you pass your daily scroll goal." },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BINGE,
                "Long sessions",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Nudges you when a single scrolling session runs long." },
        )
    }

    fun notifyDailyGoalPassed(totalMs: Long, goalMs: Long) = notify(
        id = ID_GOAL,
        channel = CHANNEL_GOAL,
        title = "Past your daily goal",
        text = "${Format.duration(totalMs)} scrolled today, goal is ${Format.duration(goalMs)}.",
    )

    fun notifyLongSession(appLabel: String, boutMs: Long) = notify(
        id = ID_BINGE,
        channel = CHANNEL_BINGE,
        title = "${Format.duration(boutMs)} straight in $appLabel",
        text = "Still scrolling. Worth a pause?",
    )

    private fun notify(id: Int, channel: String, title: String, text: String) {
        if (!manager.areNotificationsEnabled()) return
        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        // areNotificationsEnabled() covers the POST_NOTIFICATIONS grant; the platform still
        // requires the permission check to be visible to lint, hence the guarded call.
        runCatching { manager.notify(id, notification) }
    }

    private companion object {
        const val CHANNEL_GOAL = "goal"
        const val CHANNEL_BINGE = "binge"
        const val ID_GOAL = 1001
        const val ID_BINGE = 1002
    }
}
