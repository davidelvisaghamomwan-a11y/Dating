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
    const val CHANNEL_BACKGROUND_SERVICE = "kindred_background_sync"
    const val CHANNEL_APP_UPDATES = "kindred_app_updates"
    const val CHANNEL_SOCIAL = "kindred_social"

    const val TYPE_NEW_MATCH = "NEW_MATCH"
    const val TYPE_NEW_MESSAGE = "NEW_MESSAGE"
    const val TYPE_INCOMING_CALL = "INCOMING_CALL"
    const val TYPE_APP_UPDATE = "APP_UPDATE"
    const val TYPE_FRIEND_REQUEST = "FRIEND_REQUEST"
    const val TYPE_PROFILE_LIKE = "PROFILE_LIKE"
    const val TYPE_NEW_FOLLOWER = "NEW_FOLLOWER"
    const val TYPE_POST_LIKE = "POST_LIKE"
    const val TYPE_POST_COMMENT = "POST_COMMENT"
    const val TYPE_MODERATION_NOTICE = "MODERATION_NOTICE"

    const val EXTRA_MATCH_ID = "extra_match_id"
    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_POST_ID = "extra_post_id"
    const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"

    const val NOTIFICATION_ID_BACKGROUND_SERVICE = 4201
    const val NOTIFICATION_ID_APP_UPDATE = 4202

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

        val ringtoneUri = runCatching {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }.getOrNull()
        val callsChannel = NotificationChannel(
            CHANNEL_CALLS,
            context.getString(R.string.fcm_calls_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for incoming voice and video calls"
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 600L, 600L)
            if (ringtoneUri != null) {
                runCatching {
                    setSound(
                        ringtoneUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            }
        }

        val backgroundChannel = NotificationChannel(
            CHANNEL_BACKGROUND_SERVICE,
            context.getString(R.string.fcm_background_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps live incoming call & message alerts ready in the background"
            setShowBadge(false)
        }

        val updatesChannel = NotificationChannel(
            CHANNEL_APP_UPDATES,
            context.getString(R.string.fcm_updates_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when a new Kindred APK release is published on GitHub"
        }

        val socialChannel = NotificationChannel(
            CHANNEL_SOCIAL,
            context.getString(R.string.fcm_social_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time alerts for post likes, comments, and new followers"
            enableVibration(true)
        }

        runCatching {
            manager.createNotificationChannel(matchesChannel)
            manager.createNotificationChannel(messagesChannel)
            manager.createNotificationChannel(callsChannel)
            manager.createNotificationChannel(backgroundChannel)
            manager.createNotificationChannel(updatesChannel)
            manager.createNotificationChannel(socialChannel)
        }
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
        callId: String? = null,
        postId: String? = null
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!matchId.isNullOrBlank()) {
                putExtra(EXTRA_MATCH_ID, matchId)
            }
            if (!callId.isNullOrBlank()) {
                putExtra(EXTRA_CALL_ID, callId)
            }
            if (!postId.isNullOrBlank()) {
                putExtra(EXTRA_POST_ID, postId)
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
            .setSmallIcon(R.drawable.ic_stat_kindred)
            .setContentTitle("It's a Match with $partnerName!")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        postNotificationSafely(
            context = context,
            notificationId = ("match_${matchId ?: partnerName}").hashCode(),
            notification = notification
        )
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
            .setSmallIcon(R.drawable.ic_stat_kindred)
            .setContentTitle("New message from $senderName")
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        postNotificationSafely(
            context = context,
            notificationId = ("msg_${matchId ?: senderName}").hashCode(),
            notification = notification
        )
    }

    fun showSocialNotification(
        context: Context,
        title: String,
        body: String,
        notificationType: String,
        tagKey: String,
        postId: String? = null
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val contentIntent = buildLaunchIntent(
            context = context,
            matchId = null,
            notificationType = notificationType,
            postId = postId
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("social_$tagKey").hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SOCIAL)
            .setSmallIcon(R.drawable.ic_stat_kindred)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        postNotificationSafely(
            context = context,
            notificationId = ("social_$tagKey").hashCode(),
            notification = notification
        )
    }

    fun showPostLikeNotification(
        context: Context,
        postId: String?,
        likerName: String,
        postPreview: String = ""
    ) {
        val body = if (postPreview.isNotBlank()) {
            "$likerName liked your post: \"${postPreview.take(60)}\""
        } else {
            "$likerName liked your post on Kindred"
        }
        showSocialNotification(
            context = context,
            title = "New Like on Your Post",
            body = body,
            notificationType = TYPE_POST_LIKE,
            tagKey = "plike_${postId ?: "p"}_$likerName",
            postId = postId
        )
    }

    fun showPostCommentNotification(
        context: Context,
        postId: String?,
        commenterName: String,
        commentText: String
    ) {
        val cleanComment = commentText.trim().take(120).ifBlank { "Left a comment on your post" }
        showSocialNotification(
            context = context,
            title = "$commenterName commented on your post",
            body = cleanComment,
            notificationType = TYPE_POST_COMMENT,
            tagKey = "pcomment_${postId ?: "p"}_${commenterName}_${cleanComment.hashCode()}",
            postId = postId
        )
    }

    fun showModerationNoticeNotification(
        context: Context,
        noticeId: String,
        contentTypeLabel: String,
        reason: String,
        details: String? = null
    ) {
        val cleanDetails = details?.trim().orEmpty()
        val body = if (cleanDetails.isNotBlank()) {
            "Your $contentTypeLabel was removed: $reason — $cleanDetails"
        } else {
            "Your $contentTypeLabel was removed for violating community guidelines: $reason"
        }
        showSocialNotification(
            context = context,
            title = "Content Removed by Moderator",
            body = body,
            notificationType = TYPE_MODERATION_NOTICE,
            tagKey = "mod_$noticeId"
        )
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
            .setSmallIcon(R.drawable.ic_stat_kindred)
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

        postNotificationSafely(
            context = context,
            notificationId = notificationId,
            notification = notification
        )
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

    fun buildBackgroundServiceNotification(context: Context): android.app.Notification {
        ensureChannels(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_BACKGROUND_SERVICE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_BACKGROUND_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_kindred)
            .setContentTitle("Kindred real-time sync active")
            .setContentText("Listening for live messages, likes, comments & calls")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun showAppUpdateNotification(
        context: Context,
        tagName: String,
        title: String,
        installUrl: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val viewIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(installUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_APP_UPDATE,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_APP_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_kindred)
            .setContentTitle("Kindred Update Ready ($tagName)")
            .setContentText("$title is available on GitHub. Tap to download and install.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        postNotificationSafely(
            context = context,
            notificationId = NOTIFICATION_ID_APP_UPDATE,
            notification = notification
        )
    }

    private fun postNotificationSafely(
        context: Context,
        notificationId: Int,
        notification: android.app.Notification
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission revoked at runtime while posting notification.
        } catch (_: Exception) {
            // Defensive catch against OEM NotificationManager binder exceptions.
        }
    }

    fun cancelAppUpdateNotification(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_APP_UPDATE)
        }
    }

    fun tokenDocumentId(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
