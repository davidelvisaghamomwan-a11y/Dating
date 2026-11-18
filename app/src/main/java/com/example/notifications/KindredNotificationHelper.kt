package com.example.notifications

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
import com.example.MainActivity
import com.example.R
import java.security.MessageDigest

object KindredNotificationHelper {
    const val CHANNEL_MATCHES = "kindred_matches"
    const val CHANNEL_MESSAGES = "kindred_messages"

    const val TYPE_NEW_MATCH = "NEW_MATCH"
    const val TYPE_NEW_MESSAGE = "NEW_MESSAGE"

    const val EXTRA_MATCH_ID = "extra_match_id"
    const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val matchesChannel = NotificationChannel(
            CHANNEL_MATCHES,
            context.getString(R.string.fcm_matches_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when you get a mutual match"
            enableVibration(true)
        }

        val messagesChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            context.getString(R.string.fcm_messages_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for new real-time chat messages"
            enableVibration(true)
        }

        manager.createNotificationChannel(matchesChannel)
        manager.createNotificationChannel(messagesChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildLaunchIntent(
        context: Context,
        matchId: String?,
        notificationType: String
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!matchId.isNullOrBlank()) {
                putExtra(EXTRA_MATCH_ID, matchId)
            }
            putExtra(EXTRA_NOTIFICATION_TYPE, notificationType)
        }
    }

    fun showMatchNotification(
        context: Context,
        matchId: String?,
        partnerName: String,
        body: String = "You and $partnerName liked each other! Say hello."
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val contentIntent = buildLaunchIntent(context, matchId, TYPE_NEW_MATCH)
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("match_${matchId ?: partnerName}").hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MATCHES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("It's a Match with $partnerName!")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                ("match_${matchId ?: partnerName}").hashCode(),
                notification
            )
        }
    }

    fun showMessageNotification(
        context: Context,
        matchId: String?,
        senderName: String,
        messageText: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val contentIntent = buildLaunchIntent(context, matchId, TYPE_NEW_MESSAGE)
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("msg_${matchId ?: senderName}").hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("New message from $senderName")
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                ("msg_${matchId ?: senderName}").hashCode(),
                notification
            )
        }
    }

    fun tokenDocumentId(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
