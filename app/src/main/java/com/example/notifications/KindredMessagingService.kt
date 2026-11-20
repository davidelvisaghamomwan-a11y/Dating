package com.example.notifications

import android.util.Log
import com.example.data.KindredRepository
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Handles Firebase Cloud Messaging token refreshes and incoming push notifications
 * for new matches and new real-time chat messages.
 */
class KindredMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token received")
        if (FirebaseApp.getApps(applicationContext).isEmpty()) return
        val uid = Firebase.auth.currentUser?.uid
        if (!uid.isNullOrBlank() && token.isNotBlank()) {
            serviceScope.launch {
                val repo = KindredRepository(applicationContext)
                repo.saveFcmToken(token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val currentUid = if (FirebaseApp.getApps(applicationContext).isNotEmpty()) {
            Firebase.auth.currentUser?.uid
        } else {
            null
        }
        val senderId = data["senderId"]
        if (!currentUid.isNullOrBlank() && senderId == currentUid) {
            return
        }

        val type = data["type"]?.uppercase()
        val matchId = data["matchId"]
        val title = data["title"] ?: message.notification?.title
        val body = data["body"] ?: message.notification?.body
        val senderName = data["senderName"] ?: data["partnerName"] ?: title ?: "Kindred"

        when (type) {
            KindredNotificationHelper.TYPE_NEW_MATCH, "MATCH" -> {
                KindredNotificationHelper.showMatchNotification(
                    context = applicationContext,
                    matchId = matchId,
                    partnerName = senderName,
                    body = body ?: "You matched with $senderName! Start chatting now."
                )
            }

            KindredNotificationHelper.TYPE_NEW_MESSAGE, "MESSAGE" -> {
                if (!KindredNotificationHelper.shouldSuppressForegroundMessageNotification(matchId)) {
                    KindredNotificationHelper.showMessageNotification(
                        context = applicationContext,
                        matchId = matchId,
                        senderName = senderName,
                        messageText = body ?: "Sent you a new message"
                    )
                }
            }

            KindredNotificationHelper.TYPE_INCOMING_CALL, "CALL", "VIDEO_CALL", "VOICE_CALL" -> {
                val callId = data["callId"].orEmpty().ifBlank { matchId ?: senderName }
                val callType = data["callType"] ?: if (type == "VOICE_CALL") "VOICE" else "VIDEO"
                KindredNotificationHelper.showIncomingCallNotification(
                    context = applicationContext,
                    matchId = matchId,
                    callId = callId,
                    callerName = senderName,
                    callType = callType
                )
            }

            else -> {
                if (!body.isNullOrBlank() &&
                    !KindredNotificationHelper.shouldSuppressForegroundMessageNotification(matchId)
                ) {
                    KindredNotificationHelper.showMessageNotification(
                        context = applicationContext,
                        matchId = matchId,
                        senderName = senderName,
                        messageText = body
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private companion object {
        const val TAG = "KindredFCM"
    }
}
