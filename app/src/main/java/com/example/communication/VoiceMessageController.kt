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
import com.example.data.CloudMediaStorageService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "VoiceMessageCtrl"

data class RecordedVoiceClip(
    val dataUrl: String,
    val durationSec: Long,
    val localFile: File? = null
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
            outputFile = null

            if (file == null || !file.exists() || file.length() <= 0L) {
                runCatching { file?.delete() }
                return Result.failure(IllegalStateException("Recorded audio clip was empty."))
            }

            Result.success(
                RecordedVoiceClip(
                    dataUrl = "file://${file.absolutePath}",
                    durationSec = elapsedSec.coerceIn(1L, 60L),
                    localFile = file
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to finalize voice note", e)
            runCatching { file?.delete() }
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
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
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
        if (activeMessageId == messageId && (mediaPlayer?.isPlaying == true || loadJob?.isActive == true)) {
            stop()
            return false
        }
        stop()

        val clean = mediaDataUrl.trim()
        if (clean.isBlank()) {
            onError("Voice note audio is missing.")
            return false
        }

        activeMessageId = messageId
        loadJob = playerScope.launch {
            try {
                val resolvedFile: File = withContext(Dispatchers.IO) {
                    when {
                        CloudMediaStorageService.isRemoteHttpUrl(clean) -> {
                            val dir = File(context.cacheDir, "voice_cache").apply { mkdirs() }
                            val safeId = messageId.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "voice" }
                            val cached = File(dir, "voice_$safeId.m4a")
                            if (cached.exists() && cached.length() > 0L) {
                                cached
                            } else {
                                CloudMediaStorageService.downloadUrlToFile(clean, cached).getOrThrow()
                            }
                        }
                        clean.startsWith("file://") -> {
                            File(clean.removePrefix("file://")).also {
                                if (!it.exists() || it.length() <= 0L) {
                                    throw IllegalStateException("Local audio file unavailable.")
                                }
                            }
                        }
                        else -> {
                            val base64Body = clean.substringAfter("base64,", clean)
                            val audioBytes = Base64.decode(base64Body, Base64.DEFAULT)
                            if (audioBytes.isEmpty()) throw IllegalStateException("Empty voice note payload.")
                            File.createTempFile("kindred_play_", ".m4a", context.cacheDir).apply {
                                writeBytes(audioBytes)
                                tempFile = this
                            }
                        }
                    }
                }

                if (activeMessageId != messageId) return@launch

                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(resolvedFile.absolutePath)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Playback error for message=$messageId", e)
                stop()
                onError(e.localizedMessage ?: "Could not play voice note audio.")
            }
        }
        return true
    }

    fun stop() {
        loadJob?.cancel()
        loadJob = null
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
