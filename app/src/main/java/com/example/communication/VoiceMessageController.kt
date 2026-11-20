package com.example.communication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

private const val TAG = "VoiceMessageCtrl"
private const val DATA_URL_PREFIX = "data:audio/mp4;base64,"
private const val MAX_AUDIO_BYTES = 180_000

data class RecordedVoiceClip(
    val dataUrl: String,
    val durationSec: Long
)

object VoicePermissionHelper {
    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startMs: Long = 0L

    fun startRecording(): Result<Unit> {
        stopSilently()
        return try {
            val file = File.createTempFile("kindred_voice_", ".m4a", context.cacheDir)
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(24_000)
                setAudioSamplingRate(16_000)
                setMaxDuration(25_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            startMs = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start audio recording", e)
            stopSilently()
            Result.failure(e)
        }
    }

    fun stopAndEncode(): Result<RecordedVoiceClip> {
        val file = outputFile
        val elapsedSec = (((System.currentTimeMillis() - startMs).coerceAtLeast(1000L)) + 500L) / 1000L
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null

            val bytes = file?.takeIf { it.exists() }?.readBytes()
                ?: return Result.failure(IllegalStateException("No audio was captured."))
            file.delete()
            outputFile = null

            if (bytes.isEmpty()) {
                return Result.failure(IllegalStateException("Recorded clip was empty."))
            }
            val safeBytes = if (bytes.size > MAX_AUDIO_BYTES) {
                bytes.copyOf(MAX_AUDIO_BYTES)
            } else {
                bytes
            }
            val encoded = DATA_URL_PREFIX + Base64.encodeToString(safeBytes, Base64.NO_WRAP)
            Result.success(
                RecordedVoiceClip(
                    dataUrl = encoded,
                    durationSec = elapsedSec.coerceIn(1L, 60L)
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to finalize voice note", e)
            stopSilently()
            Result.failure(e)
        }
    }

    fun stopSilently() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        try {
            outputFile?.delete()
        } catch (_: Exception) {
        }
        outputFile = null
    }
}

class VoiceNotePlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null
    var activeMessageId: String? = null
        private set

    fun playOrStop(
        messageId: String,
        mediaDataUrl: String,
        onCompleted: () -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (activeMessageId == messageId && mediaPlayer?.isPlaying == true) {
            stop()
            return false
        }
        stop()

        return try {
            val base64Body = mediaDataUrl.substringAfter("base64,", mediaDataUrl)
            val audioBytes = Base64.decode(base64Body, Base64.DEFAULT)
            val file = File.createTempFile("kindred_play_", ".m4a", context.cacheDir)
            file.writeBytes(audioBytes)
            tempFile = file

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    stop()
                    onCompleted()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onError("Unable to play voice note on this device.")
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            activeMessageId = messageId
            true
        } catch (e: Exception) {
            Log.e(TAG, "Playback error", e)
            stop()
            onError("Could not decode voice note audio.")
            false
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        activeMessageId = null
        try {
            tempFile?.delete()
        } catch (_: Exception) {
        }
        tempFile = null
    }
}
