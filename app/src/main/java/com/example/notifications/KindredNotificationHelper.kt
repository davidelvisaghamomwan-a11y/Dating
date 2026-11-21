package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
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
    const val CHANNEL_CALLS = "kindred_calls"

    const val TYPE_NEW_MATCH = "NEW_MATCH"
    const val TYPE_NEW_MESSAGE = "NEW_MESSAGE"
    const val TYPE_INCOMING_CALL = "INCOMING_CALL"

    const val EXTRA_MATCH_ID = "extra_match_id"
    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"

    @Volatile
    var activeMatchIdInForeground: String? = null

    fun shouldSuppressForegroundMessageNotification(matchId: String?): Boolean {
        val active = activeMatchIdInForeground
        return !matchId.isNullOrBlank() && !active.isNullOrBlank() && active == matchId
    }

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

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val callsChannel = NotificationChannel(
            CHANNEL_CALLS,
            context.getString(R.string.fcm_calls_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for incoming voice and video calls"
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 600L, 600L)
            if (ringtoneUri != null) {
                setSound(
                    ringtoneUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        }

        manager.createNotificationChannel(matchesChannel)
        manager.createNotificationChannel(messagesChannel)
        manager.createNotificationChannel(callsChannel)
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
        notificationType: String,
        callId: String? = null
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!matchId.isNullOrBlank()) {
                putExtra(EXTRA_MATCH_ID, matchId)
            }
            if (!callId.isNullOrBlank()) {
                putExtra(EXTRA_CALL_ID, callId)
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

    private val activeCallNotificationIds = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    fun showIncomingCallNotification(
        context: Context,
        matchId: String?,
        callId: String,
        callerName: String,
        callType: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val isVideo = callType.equals("VIDEO", ignoreCase = true)
        val contentIntent = buildLaunchIntent(
            context = context,
            matchId = matchId,
            notificationType = TYPE_INCOMING_CALL,
            callId = callId
        )
        val notificationId = incomingCallNotificationId(callId)
        activeCallNotificationIds.add(notificationId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callLabel = if (isVideo) "Incoming video call" else "Incoming voice call"
        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$callerName is calling…")
            .setContentText("$callLabel on Kindred — tap to answer")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(true)
            .setTimeoutAfter(45_000L)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun cancelIncomingCallNotification(context: Context, callId: String? = null) {
        val mgr = NotificationManagerCompat.from(context)
        if (!callId.isNullOrBlank()) {
            val id = incomingCallNotificationId(callId)
            activeCallNotificationIds.remove(id)
            runCatching { mgr.cancel(id) }
            return
        }
        val toCancel = synchronized(activeCallNotificationIds) {
            val copy = activeCallNotificationIds.toList()
            activeCallNotificationIds.clear()
            copy
        }
        toCancel.forEach { id ->
            runCatching { mgr.cancel(id) }
        }
    }

    fun incomingCallNotificationId(callId: String): Int = ("call_$callId").hashCode()

    fun tokenDocumentId(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
