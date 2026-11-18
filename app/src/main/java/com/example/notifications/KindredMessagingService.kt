package com.example.notifications

import android.util.Log
import com.example.data.KindredRepository
import com.google.firebase.Firebase
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

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token received")
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
        val type = data["type"]?.uppercase()
        val matchId = data["matchId"]
        val title = data["title"] ?: message.notification?.title
        val body = data["body"] ?: message.notification?.body
        val senderName = data["senderName"] ?: data["partnerName"] ?: title ?: "Kindred"

        when (type) {
            KindredNotificationHelper.TYPE_NEW_MATCH -> {
                KindredNotificationHelper.showMatchNotification(
                    context = applicationContext,
                    matchId = matchId,
                    partnerName = senderName,
                    body = body ?: "You matched with $senderName! Start chatting now."
                )
            }

            KindredNotificationHelper.TYPE_NEW_MESSAGE -> {
                KindredNotificationHelper.showMessageNotification(
                    context = applicationContext,
                    matchId = matchId,
                    senderName = senderName,
                    messageText = body ?: "Sent you a new message"
                )
            }

            else -> {
                if (!body.isNullOrBlank()) {
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
