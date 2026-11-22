package com.example.util

import com.example.model.UserPresence
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PresenceDisplayState(
    val isOnline: Boolean,
    val label: String,
    val isKnown: Boolean,
    val isRecentlyLeft: Boolean = false,
    val isHidden: Boolean = false
)

object PresenceUtils {
    const val HEARTBEAT_INTERVAL_MS = 45_000L
    const val ONLINE_STALE_THRESHOLD_MS = 120_000L
    const val RECENTLY_LEFT_WINDOW_MS = 30 * 60_000L
    const val UI_TICKER_INTERVAL_MS = 30_000L

    fun resolvePresenceDisplay(
        presence: UserPresence?,
        nowMillis: Long = System.currentTimeMillis()
    ): PresenceDisplayState {
        if (presence == null) {
            return PresenceDisplayState(
                isOnline = false,
                label = "Offline",
                isKnown = false
            )
        }

        if (presence.hideOnlineStatus) {
            return PresenceDisplayState(
                isOnline = false,
                label = "Status hidden",
                isKnown = true,
                isRecentlyLeft = false,
                isHidden = true
            )
        }

        val lastSeenMillis = presence.lastSeenAt?.toDate()?.time
        val isFresh = lastSeenMillis == null || (nowMillis - lastSeenMillis) <= ONLINE_STALE_THRESHOLD_MS
        if (presence.online && isFresh) {
            return PresenceDisplayState(
                isOnline = true,
                label = "Online",
                isKnown = true
            )
        }

        if (lastSeenMillis == null) {
            return PresenceDisplayState(
                isOnline = false,
                label = "Offline",
                isKnown = true
            )
        }

        val elapsedMs = (nowMillis - lastSeenMillis).coerceAtLeast(0L)
        val minuteMs = 60_000L
        val hourMs = 60 * minuteMs
        val dayMs = 24 * hourMs

        val label = when {
            elapsedMs < minuteMs -> "Last seen just now"
            elapsedMs < hourMs -> "Last seen ${elapsedMs / minuteMs}m ago"
            elapsedMs < dayMs -> "Last seen ${elapsedMs / hourMs}h ago"
            elapsedMs < 7 * dayMs -> "Last seen ${elapsedMs / dayMs}d ago"
            else -> {
                val dateLabel = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(lastSeenMillis))
                "Last seen $dateLabel"
            }
        }

        return PresenceDisplayState(
            isOnline = false,
            label = label,
            isKnown = true,
            isRecentlyLeft = elapsedMs <= RECENTLY_LEFT_WINDOW_MS
        )
    }
}
