package com.example.communication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Real-time bidirectional voice audio engine for Kindred in-app voice & video calls.
 * Captures 8 kHz 16-bit PCM voice via [AudioRecord] with hardware echo cancellation,
 * streams compact Base64 frames, and plays remote participant frames via [AudioTrack]
 * in [AudioManager.MODE_IN_COMMUNICATION].
 */
class LiveCallAudioEngine(
    context: Context
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var playbackJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var isStarted = false
    private var seqCounter = 0L

    @Volatile
    private var muted = false

    private val playbackQueue = Channel<ByteArray>(
        capacity = 12,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _localMicLevel = MutableStateFlow(0f)
    val localMicLevel: StateFlow<Float> = _localMicLevel.asStateFlow()

    private val _remoteAudioLevel = MutableStateFlow(0f)
    val remoteAudioLevel: StateFlow<Float> = _remoteAudioLevel.asStateFlow()

    private val _isAudioHardwareActive = MutableStateFlow(false)
    val isAudioHardwareActive: StateFlow<Boolean> = _isAudioHardwareActive.asStateFlow()

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start(
        speakerOn: Boolean,
        isMuted: Boolean,
        onAudioFrameReady: (seq: Long, sampleRateHz: Int, audioBase64: String) -> Unit
    ) {
        muted = isMuted
        if (isStarted) {
            updateAudioRouting(speakerOn = speakerOn, isMuted = isMuted)
            return
        }
        isStarted = true
        previousAudioMode = runCatching { audioManager?.mode ?: AudioManager.MODE_NORMAL }
            .getOrDefault(AudioManager.MODE_NORMAL)

        requestCallAudioFocus()
        updateAudioRouting(speakerOn = speakerOn, isMuted = isMuted)

        startPlaybackLoop()
        if (hasRecordAudioPermission()) {
            startCaptureLoop(onAudioFrameReady)
        } else {
            Log.w(TAG, "RECORD_AUDIO permission missing; starting playback-only mode")
        }
    }

    fun updateAudioRouting(speakerOn: Boolean, isMuted: Boolean) {
        muted = isMuted
        if (isMuted) {
            _localMicLevel.value = 0f
        }
        runCatching {
            val am = audioManager ?: return@runCatching
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isMicrophoneMute = isMuted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val desiredType = if (speakerOn) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                val device = am.availableCommunicationDevices.firstOrNull { it.type == desiredType }
                if (device != null) {
                    am.setCommunicationDevice(device)
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = speakerOn
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = speakerOn
            }
        }
    }

    fun enqueueRemoteAudio(audioBase64: String) {
        if (audioBase64.isBlank()) return
        val bytes = runCatching { Base64.decode(audioBase64, Base64.DEFAULT) }.getOrNull() ?: return
        if (bytes.isEmpty()) return
        _remoteAudioLevel.value = computePcm16RmsLevel(bytes, bytes.size)
        playbackQueue.trySend(bytes)
    }

    @SuppressLint("MissingPermission")
    private fun startCaptureLoop(
        onAudioFrameReady: (seq: Long, sampleRateHz: Int, audioBase64: String) -> Unit
    ) {
        captureJob?.cancel()
        captureJob = engineScope.launch {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(FRAME_BYTES)
            val recordBufferSize = maxOf(minBuf * 2, FRAME_BYTES * 2)

            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recordBufferSize
                )
            }.getOrNull()

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord initialization failed or unavailable on device")
                runCatching { recorder?.release() }
                return@launch
            }
            audioRecord = recorder

            runCatching {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                        enabled = true
                    }
                }
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(recorder.audioSessionId)?.apply {
                        enabled = true
                    }
                }
            }

            runCatching { recorder.startRecording() }
            _isAudioHardwareActive.value = true

            val buffer = ByteArray(FRAME_BYTES)
            while (isActive) {
                var offset = 0
                while (offset < FRAME_BYTES && isActive) {
                    val read = runCatching {
                        recorder.read(buffer, offset, FRAME_BYTES - offset)
                    }.getOrDefault(-1)
                    if (read <= 0) break
                    offset += read
                }
                if (offset <= 0) continue

                if (muted) {
                    _localMicLevel.value = 0f
                    continue
                }

                val rms = computePcm16RmsLevel(buffer, offset)
                _localMicLevel.value = rms
                val encoded = Base64.encodeToString(buffer, 0, offset, Base64.NO_WRAP)
                val seq = seqCounter++
                onAudioFrameReady(seq, SAMPLE_RATE_HZ, encoded)
            }
        }
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = engineScope.launch {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(FRAME_BYTES)
            val trackBufferSize = maxOf(minBuf * 2, FRAME_BYTES * 3)

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setSampleRate(SAMPLE_RATE_HZ)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(trackBufferSize)
                    .build()
            }.getOrNull()

            if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                Log.w(TAG, "AudioTrack initialization failed or unavailable on device")
                runCatching { track?.release() }
                return@launch
            }
            audioTrack = track
            runCatching { track.play() }
            _isAudioHardwareActive.value = true

            while (isActive) {
                val frame = playbackQueue.receiveCatching().getOrNull() ?: break
                runCatching {
                    track.write(frame, 0, frame.size)
                }
            }
        }
    }

    private fun requestCallAudioFocus() {
        runCatching {
            val am = audioManager ?: return@runCatching
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioFocusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        }
    }

    private fun abandonCallAudioFocus() {
        runCatching {
            val am = audioManager ?: return@runCatching
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
    }

    fun stop() {
        isStarted = false
        _isAudioHardwareActive.value = false
        _localMicLevel.value = 0f
        _remoteAudioLevel.value = 0f

        captureJob?.cancel()
        captureJob = null
        playbackJob?.cancel()
        playbackJob = null

        runCatching { echoCanceler?.release() }
        echoCanceler = null
        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null

        runCatching {
            audioRecord?.stop()
        }
        runCatching {
            audioRecord?.release()
        }
        audioRecord = null

        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
        }
        runCatching {
            audioTrack?.release()
        }
        audioTrack = null

        runCatching {
            audioManager?.let { am ->
                am.isMicrophoneMute = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = false
                }
                am.mode = previousAudioMode
            }
        }
        abandonCallAudioFocus()
    }

    fun release() {
        stop()
        playbackQueue.close()
        engineScope.cancel()
    }

    companion object {
        private const val TAG = "LiveCallAudioEngine"
        const val SAMPLE_RATE_HZ = 8000
        // 200 ms per frame at 8 kHz 16-bit mono = 1600 samples = 3200 bytes
        const val FRAME_BYTES = 3200

        fun computePcm16RmsLevel(bytes: ByteArray, length: Int = bytes.size): Float {
            val sampleCount = (length / 2).coerceAtLeast(0)
            if (sampleCount == 0) return 0f
            var sumSq = 0.0
            var i = 0
            while (i + 1 < length) {
                val low = bytes[i].toInt() and 0xFF
                val high = bytes[i + 1].toInt()
                val sample = ((high shl 8) or low).toShort().toDouble()
                sumSq += sample * sample
                i += 2
            }
            val rms = sqrt(sumSq / sampleCount)
            return (rms / 8000.0).toFloat().coerceIn(0f, 1f)
        }
    }
}
