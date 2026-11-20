package com.example.communication

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Handles device ringtone + haptic vibration for incoming calls and subtle
 * telephone ringback tone for outgoing ringing calls.
 */
class CallRingtoneController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaPlayer: MediaPlayer? = null
    private var fallbackRingtone: Ringtone? = null
    private var ringbackJob: Job? = null
    private var toneGenerator: ToneGenerator? = null

    @Volatile
    var isRinging: Boolean = false
        private set

    fun startIncomingRing() {
        if (isRinging) return
        stopAll()
        isRinging = true

        // Start haptic vibration pattern (0ms delay, 800ms vibrate, 1000ms pause)
        runCatching {
            val pattern = longArrayOf(0L, 800L, 1000L)
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, 0)
                }
            }
        }

        // Start system default ringtone looping on USAGE_NOTIFICATION_RINGTONE
        val ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (ringUri != null) {
            val startedMediaPlayer = runCatching {
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                player.setDataSource(appContext, ringUri)
                player.isLooping = true
                player.prepare()
                player.start()
                mediaPlayer = player
                true
            }.getOrDefault(false)

            if (!startedMediaPlayer) {
                runCatching {
                    val ringtone = RingtoneManager.getRingtone(appContext, ringUri)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone?.isLooping = true
                    }
                    ringtone?.play()
                    fallbackRingtone = ringtone
                }.onFailure { err ->
                    Log.w(TAG, "Unable to play incoming ringtone", err)
                }
            }
        }
    }

    fun startOutgoingRingback() {
        stopAll()
        isRinging = true
        ringbackJob = scope.launch {
            val gen = runCatching {
                ToneGenerator(AudioManager.STREAM_VOICE_CALL, 55)
            }.getOrNull()
            toneGenerator = gen
            while (isActive) {
                runCatching {
                    gen?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1400)
                }
                delay(3200L)
            }
        }
    }

    fun stopAll() {
        isRinging = false
        ringbackJob?.cancel()
        ringbackJob = null

        runCatching {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        }
        toneGenerator = null

        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null

        runCatching {
            fallbackRingtone?.stop()
        }
        fallbackRingtone = null

        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.cancel()
        }
    }

    fun release() {
        stopAll()
        scope.cancel()
    }

    private companion object {
        const val TAG = "CallRingtoneController"
    }
}
