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
import com.example.model.MatchRecord
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class KindredBackgroundService : Service() {
    private var matchesRegistration: ListenerRegistration? = null
    private val callRegistrations = mutableMapOf<String, ListenerRegistration>()
    private val activeRingingCallIds = mutableSetOf<String>()
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
            Log.w(TAG, "Unable to elevate background call listener to foreground", e)
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
        if (matchesRegistration != null) return
        val dbId = getString(R.string.firestore_database_id)
        val db = runCatching { FirebaseFirestore.getInstance(dbId) }.getOrNull() ?: return

        matchesRegistration = db.collection("matches")
            .whereArrayContains("participantUids", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Background matches listener error", error)
                    return@addSnapshotListener
                }
                val matchIds = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(MatchRecord::class.java)?.id?.ifBlank { doc.id } ?: doc.id
                }?.toSet().orEmpty()

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

    private fun handleCallSessionsUpdate(
        db: FirebaseFirestore,
        uid: String,
        matchId: String,
        sessions: List<CallSession>
    ) {
        val ringingForMe = sessions.filter { it.status == "RINGING" && it.calleeUid == uid }
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
                db.collection("publicProfiles").document(session.callerUid).get()
                    .addOnSuccessListener { snap ->
                        val callerName = snap.getString("displayName")?.takeIf { it.isNotBlank() }
                            ?: "Kindred Match"
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
                    .addOnFailureListener {
                        KindredNotificationHelper.showIncomingCallNotification(
                            context = this,
                            matchId = matchId,
                            callId = session.id,
                            callerName = "Kindred Match",
                            callType = session.callType
                        )
                    }
            }
        }
    }

    override fun onDestroy() {
        matchesRegistration?.remove()
        matchesRegistration = null
        callRegistrations.values.forEach { it.remove() }
        callRegistrations.clear()
        activeRingingCallIds.clear()
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
