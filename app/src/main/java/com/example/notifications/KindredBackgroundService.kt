package com.example.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.R
import com.example.communication.CallRingtoneController
import com.example.model.CallSession
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.FollowRecord
import com.example.model.MatchRecord
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class KindredBackgroundService : Service() {
    private var matchesRegistration: ListenerRegistration? = null
    private var myPostsRegistration: ListenerRegistration? = null
    private var followsRegistration: ListenerRegistration? = null

    private val callRegistrations = mutableMapOf<String, ListenerRegistration>()
    private val commentRegistrations = mutableMapOf<String, ListenerRegistration>()

    private val activeRingingCallIds = mutableSetOf<String>()
    private val knownLastMessageMs = mutableMapOf<String, Long>()
    private var matchesInitialized = false

    private val knownPostLikes = mutableMapOf<String, Set<String>>()
    private var postsInitialized = false

    private val initializedCommentPosts = mutableSetOf<String>()
    private val seenCommentIds = mutableSetOf<String>()

    private val seenFollowIds = mutableSetOf<String>()
    private var followsInitialized = false
    private val serviceStartTimeMs = System.currentTimeMillis()

    private var ringtoneController: CallRingtoneController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        KindredNotificationHelper.ensureChannels(this)
        val notification = KindredNotificationHelper.buildBackgroundServiceNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    KindredNotificationHelper.NOTIFICATION_ID_BACKGROUND_SERVICE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(
                    KindredNotificationHelper.NOTIFICATION_ID_BACKGROUND_SERVICE,
                    notification
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to elevate background sync listener to foreground", e)
            stopSelf()
            return
        }
        ringtoneController = CallRingtoneController(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isEnabled(this) || FirebaseApp.getApps(this).isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val uid = Firebase.auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        attachFirestoreListeners(uid)
        return START_STICKY
    }

    private fun attachFirestoreListeners(uid: String) {
        val dbId = getString(R.string.firestore_database_id)
        val db = runCatching { FirebaseFirestore.getInstance(dbId) }.getOrNull() ?: return

        // 1. Real-time Matches -> Direct Messages & Calls
        if (matchesRegistration == null) {
            matchesRegistration = db.collection("matches")
                .whereArrayContains("participantUids", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Background matches listener error", error)
                        return@addSnapshotListener
                    }
                    val records = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(MatchRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.let { if (it.id.isBlank()) it.copy(id = doc.id) else it }
                    }.orEmpty()

                    handleMatchMessagesUpdate(db, uid, records)

                    val matchIds = records.map { it.id }.toSet()
                    val removed = callRegistrations.keys - matchIds
                    removed.forEach { oldId ->
                        callRegistrations.remove(oldId)?.remove()
                    }

                    matchIds.forEach { matchId ->
                        if (matchId !in callRegistrations) {
                            callRegistrations[matchId] = db.collection("matches")
                                .document(matchId)
                                .collection("calls")
                                .addSnapshotListener { callSnap, callErr ->
                                    if (callErr != null) {
                                        Log.w(TAG, "Background call listener error for $matchId", callErr)
                                        return@addSnapshotListener
                                    }
                                    val sessions = callSnap?.documents?.mapNotNull { doc ->
                                        doc.toObject(CallSession::class.java)?.copy(id = doc.id)
                                    }.orEmpty()
                                    handleCallSessionsUpdate(db, uid, matchId, sessions)
                                }
                        }
                    }
                }
        }

        // 2. Real-time Likes & Comments on Current User's Posts
        if (myPostsRegistration == null) {
            myPostsRegistration = db.collection("feedPosts")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Background posts listener error", error)
                        return@addSnapshotListener
                    }
                    val myPosts = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(FeedPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.let { if (it.id.isBlank()) it.copy(id = doc.id) else it }
                    }.orEmpty()

                    handleMyPostsLikesUpdate(db, uid, myPosts)
                    syncPostCommentsListeners(db, uid, myPosts)
                }
        }

        // 3. Real-time New Followers
        if (followsRegistration == null) {
            followsRegistration = db.collection("follows")
                .whereEqualTo("followingUid", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Background follows listener error", error)
                        return@addSnapshotListener
                    }
                    val follows = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(FollowRecord::class.java)
                            ?.let { if (it.id.isBlank()) it.copy(id = doc.id) else it }
                    }.orEmpty()

                    if (!followsInitialized) {
                        follows.forEach { seenFollowIds += it.id }
                        if (follows.isNotEmpty()) {
                            followsInitialized = true
                        }
                        return@addSnapshotListener
                    }

                    follows.forEach { follow ->
                        val createdAtMs = follow.createdAt?.toDate()?.time ?: 0L
                        if (createdAtMs <= serviceStartTimeMs) {
                            seenFollowIds += follow.id
                            return@forEach
                        }
                        if (seenFollowIds.add(follow.id) && follow.followerUid != uid) {
                            resolveDisplayName(db, follow.followerUid, follow.followerName.ifBlank { "Kindred Member" }) { followerName ->
                                KindredNotificationHelper.showSocialNotification(
                                    context = this,
                                    title = "New Follower",
                                    body = "$followerName started following you.",
                                    notificationType = KindredNotificationHelper.TYPE_NEW_FOLLOWER,
                                    tagKey = "follow_${follow.followerUid}"
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun handleMatchMessagesUpdate(
        db: FirebaseFirestore,
        uid: String,
        records: List<MatchRecord>
    ) {
        if (!matchesInitialized) {
            records.forEach { m ->
                knownLastMessageMs[m.id] = m.lastMessageAt?.toDate()?.time ?: 0L
            }
            if (records.isNotEmpty()) {
                matchesInitialized = true
            }
            return
        }

        records.forEach { match ->
            val msgMs = match.lastMessageAt?.toDate()?.time ?: 0L
            val prevMs = knownLastMessageMs[match.id] ?: 0L
            if (msgMs <= serviceStartTimeMs) {
                knownLastMessageMs[match.id] = maxOf(prevMs, msgMs)
                return@forEach
            }
            if (msgMs > prevMs) {
                knownLastMessageMs[match.id] = msgMs
                val senderId = match.lastMessageSenderId
                if (!senderId.isNullOrBlank() &&
                    senderId != uid &&
                    !KindredNotificationHelper.shouldSuppressForegroundMessageNotification(match.id)
                ) {
                    val previewText = match.lastMessageText?.takeIf { it.isNotBlank() } ?: "Sent you a new message"
                    resolveDisplayName(db, senderId, "Kindred Member") { senderName ->
                        KindredNotificationHelper.showMessageNotification(
                            context = this,
                            matchId = match.id,
                            senderName = senderName,
                            messageText = previewText
                        )
                    }
                }
            }
        }
    }

    private fun handleMyPostsLikesUpdate(
        db: FirebaseFirestore,
        uid: String,
        myPosts: List<FeedPost>
    ) {
        if (!postsInitialized) {
            myPosts.forEach { post ->
                knownPostLikes[post.id] = post.likedByUids.toSet()
            }
            postsInitialized = true
            return
        }

        myPosts.forEach { post ->
            val prevLikes = knownPostLikes[post.id]
            val currentLikes = post.likedByUids.toSet()
            knownPostLikes[post.id] = currentLikes

            if (prevLikes != null) {
                val newlyAdded = (currentLikes - prevLikes).filter { it != uid }
                newlyAdded.forEach { likerUid ->
                    resolveDisplayName(db, likerUid, "Kindred Member") { likerName ->
                        KindredNotificationHelper.showPostLikeNotification(
                            context = this,
                            postId = post.id,
                            likerName = likerName,
                            postPreview = post.caption
                        )
                    }
                }
            }
        }
    }

    private fun syncPostCommentsListeners(
        db: FirebaseFirestore,
        uid: String,
        myPosts: List<FeedPost>
    ) {
        // Track comments on the 15 most recent posts to stay lightweight
        val trackedPostIds = myPosts.take(15).map { it.id }.toSet()
        val removed = commentRegistrations.keys - trackedPostIds
        removed.forEach { oldPostId ->
            commentRegistrations.remove(oldPostId)?.remove()
            initializedCommentPosts.remove(oldPostId)
        }

        trackedPostIds.forEach { postId ->
            if (postId !in commentRegistrations) {
                commentRegistrations[postId] = db.collection("feedPosts")
                    .document(postId)
                    .collection("comments")
                    .addSnapshotListener { commentSnap, commentErr ->
                        if (commentErr != null) {
                            Log.w(TAG, "Background comments listener error for $postId", commentErr)
                            return@addSnapshotListener
                        }
                        val comments = commentSnap?.documents?.mapNotNull { doc ->
                            doc.toObject(FeedComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                                ?.let { if (it.id.isBlank()) it.copy(id = doc.id) else it }
                        }.orEmpty()

                        if (postId !in initializedCommentPosts) {
                            comments.forEach { seenCommentIds += it.id }
                            if (comments.isNotEmpty()) {
                                initializedCommentPosts += postId
                            }
                            return@addSnapshotListener
                        }

                        comments.forEach { comment ->
                            val commentCreatedAtMs = comment.createdAt?.toDate()?.time ?: 0L
                            if (commentCreatedAtMs <= serviceStartTimeMs) {
                                seenCommentIds += comment.id
                                return@forEach
                            }
                            if (seenCommentIds.add(comment.id) && comment.userId != uid) {
                                val commenterName = comment.authorName.ifBlank { "Kindred Member" }
                                KindredNotificationHelper.showPostCommentNotification(
                                    context = this,
                                    postId = postId,
                                    commenterName = commenterName,
                                    commentText = comment.text
                                )
                            }
                        }
                    }
            }
        }
    }

    private fun resolveDisplayName(
        db: FirebaseFirestore,
        targetUid: String,
        fallback: String,
        onResolved: (String) -> Unit
    ) {
        db.collection("publicProfiles").document(targetUid).get()
            .addOnSuccessListener { snap ->
                val name = snap.getString("displayName")?.takeIf { it.isNotBlank() } ?: fallback
                onResolved(name)
            }
            .addOnFailureListener {
                onResolved(fallback)
            }
    }

    private fun handleCallSessionsUpdate(
        db: FirebaseFirestore,
        uid: String,
        matchId: String,
        sessions: List<CallSession>
    ) {
        val now = System.currentTimeMillis()
        val ringingForMe = sessions.filter { session ->
            if (session.status != "RINGING" || session.calleeUid != uid) return@filter false
            val callCreatedAtMs = session.createdAt?.toDate()?.time ?: 0L
            if (callCreatedAtMs > 0L && (now - callCreatedAtMs) > 60_000L) {
                false
            } else {
                true
            }
        }
        val ringingIdsNow = ringingForMe.map { it.id }.toSet()

        val staleIds = activeRingingCallIds - ringingIdsNow
        staleIds.forEach { oldCallId ->
            activeRingingCallIds.remove(oldCallId)
            KindredNotificationHelper.cancelIncomingCallNotification(this, oldCallId)
        }
        if (ringingForMe.isEmpty()) {
            ringtoneController?.stopAll()
        }

        ringingForMe.forEach { session ->
            if (activeRingingCallIds.add(session.id)) {
                resolveDisplayName(db, session.callerUid, "Kindred Member") { callerName ->
                    KindredNotificationHelper.showIncomingCallNotification(
                        context = this,
                        matchId = matchId,
                        callId = session.id,
                        callerName = callerName,
                        callType = session.callType
                    )
                    if (KindredNotificationHelper.activeMatchIdInForeground != matchId) {
                        ringtoneController?.startIncomingRing()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        matchesRegistration?.remove()
        matchesRegistration = null
        myPostsRegistration?.remove()
        myPostsRegistration = null
        followsRegistration?.remove()
        followsRegistration = null
        callRegistrations.values.forEach { it.remove() }
        callRegistrations.clear()
        commentRegistrations.values.forEach { it.remove() }
        commentRegistrations.clear()
        activeRingingCallIds.clear()
        knownLastMessageMs.clear()
        knownPostLikes.clear()
        initializedCommentPosts.clear()
        seenCommentIds.clear()
        seenFollowIds.clear()
        ringtoneController?.release()
        ringtoneController = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KindredBgService"
        private const val PREFS_NAME = "kindred_background_sync"
        private const val KEY_ENABLED = "background_listener_enabled"

        fun isEnabled(context: Context): Boolean {
            return context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply()
            if (enabled) {
                startIfEnabled(context)
            } else {
                stop(context)
            }
        }

        fun startIfEnabled(context: Context) {
            if (!isEnabled(context) || FirebaseApp.getApps(context).isEmpty()) return
            if (Firebase.auth.currentUser == null) return
            val intent = Intent(context.applicationContext, KindredBackgroundService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
            }.onFailure { err ->
                Log.w(TAG, "Could not start background sync service", err)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, KindredBackgroundService::class.java)
                )
            }
        }
    }
}
