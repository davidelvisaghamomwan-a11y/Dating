package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.communication.CallRingtoneController
import com.example.communication.LiveCallAudioEngine
import com.example.model.CallAudioChunk
import com.example.model.CallSession
import com.example.model.PublicProfile
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import kotlinx.coroutines.delay

private const val TAG = "InAppCallOverlay"

@Composable
internal fun IncomingCallBanner(
    session: CallSession,
    partner: PublicProfile?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVideo = session.callType == "VIDEO"
    val callerName = partner?.displayName ?: "Kindred Match"
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("incoming_call_banner"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.5.dp, EmeraldMint.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileAvatarImage(
                photoDataUrl = partner?.photoDataUrl,
                displayName = callerName,
                seed = partner?.avatarSeed ?: session.callerUid,
                modifier = Modifier.size(48.dp),
                initialsFontSizeSp = 18
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = callerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isVideo) "Incoming in-app video call…" else "Incoming in-app voice call…",
                    style = MaterialTheme.typography.bodySmall,
                    color = EmeraldMint
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(onClick = onDecline, modifier = Modifier.testTag("decline_call_button")) {
                    Icon(Icons.Filled.CallEnd, contentDescription = "Decline call", tint = Color.White)
                }
            }
            Surface(
                shape = CircleShape,
                color = EmeraldMint,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(onClick = onAccept, modifier = Modifier.testTag("accept_call_button")) {
                    Icon(
                        imageVector = if (isVideo) Icons.Filled.Videocam else Icons.Filled.Call,
                        contentDescription = "Accept call",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
internal fun FullscreenIncomingCallDialog(
    session: CallSession,
    partner: PublicProfile?,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val isVideo = session.callType == "VIDEO"
    val callerName = partner?.displayName ?: "Kindred Match"
    val locationLabel = partner?.formattedLocation()?.takeIf { it.isNotBlank() }

    val pulseTransition = rememberInfiniteTransition(label = "incomingRingPulse")
    val ringScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringScale"
    )
    val ringAlpha by pulseTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF14111D), Color(0xFF28162C), Color(0xFF12101A))
                    )
                )
                .padding(horizontal = 24.dp, vertical = 36.dp)
                .testTag("incoming_call_banner")
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = EmeraldMint.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, EmeraldMint.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isVideo) Icons.Filled.Videocam else Icons.Filled.Call,
                            contentDescription = null,
                            tint = EmeraldMint,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isVideo) "Incoming Kindred Video Call" else "Incoming Kindred Voice Call",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldMint,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = callerName,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (locationLabel != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = locationLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }

            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size((156 * ringScale).dp)
                        .clip(CircleShape)
                        .border(2.dp, EmeraldMint.copy(alpha = ringAlpha), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .clip(CircleShape)
                        .border(3.dp, CoralRose.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    ProfileAvatarImage(
                        photoDataUrl = partner?.photoDataUrl,
                        displayName = callerName,
                        seed = partner?.avatarSeed ?: session.callerUid,
                        modifier = Modifier.size(134.dp),
                        initialsFontSizeSp = 44
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error,
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(68.dp)
                    ) {
                        IconButton(
                            onClick = onDecline,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("decline_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = "Decline call",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Decline",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = EmeraldMint,
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(68.dp)
                    ) {
                        IconButton(
                            onClick = onAccept,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("accept_call_button")
                        ) {
                            Icon(
                                imageVector = if (isVideo) Icons.Filled.Videocam else Icons.Filled.Call,
                                contentDescription = "Accept call",
                                tint = Color.Black,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Accept",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
internal fun InAppCallOverlay(
    session: CallSession,
    currentUserId: String,
    partner: PublicProfile?,
    incomingAudioChunks: List<CallAudioChunk> = emptyList(),
    onSendAudioChunk: (seq: Long, sampleRateHz: Int, audioBase64: String) -> Unit = { _, _, _ -> },
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onEndCall: (durationSeconds: Long) -> Unit
) {
    val context = LocalContext.current
    val isVideo = session.callType == "VIDEO"
    val isCaller = session.callerUid == currentUserId
    val isMuted = if (isCaller) session.callerMuted else session.calleeMuted
    val isMyCameraOn = if (isCaller) session.callerCameraOn else session.calleeCameraOn
    val isPartnerMuted = if (isCaller) session.calleeMuted else session.callerMuted
    val partnerName = partner?.displayName ?: "Kindred Match"

    var useFrontCamera by remember { mutableStateOf(true) }
    var speakerOn by remember { mutableStateOf(isVideo) }
    var elapsedSec by remember(session.id, session.status) { mutableLongStateOf(0L) }

    val audioEngine = remember(context) { LiveCallAudioEngine(context.applicationContext) }
    val ringtoneController = remember(context) { CallRingtoneController(context.applicationContext) }
    val playedChunkKeys = remember(session.id) { linkedSetOf<String>() }
    val latestOnSendAudioChunk by rememberUpdatedState(onSendAudioChunk)

    val localMicLevel by audioEngine.localMicLevel.collectAsStateWithLifecycle()
    val remoteAudioLevel by audioEngine.remoteAudioLevel.collectAsStateWithLifecycle()
    val isAudioHardwareActive by audioEngine.isAudioHardwareActive.collectAsStateWithLifecycle()

    val requiredPerms = remember(isVideo) {
        if (isVideo) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
    }
    var hasCallPermissions by remember(requiredPerms) {
        mutableStateOf(
            requiredPerms.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCallPermissions = results.values.all { it }
    }

    LaunchedEffect(session.id) {
        if (!hasCallPermissions) {
            permissionLauncher.launch(requiredPerms)
        }
    }

    DisposableEffect(session.id) {
        onDispose {
            ringtoneController.release()
            audioEngine.release()
        }
    }

    // Caller ringback tone while waiting for answer
    LaunchedEffect(session.id, session.status, isCaller) {
        if (session.status == "RINGING" && isCaller) {
            ringtoneController.startOutgoingRingback()
        } else {
            ringtoneController.stopAll()
        }
    }

    // Bidirectional live PCM voice capture + AudioTrack output while call is ACTIVE
    LaunchedEffect(session.id, session.status, hasCallPermissions) {
        if (session.status == "ACTIVE") {
            ringtoneController.stopAll()
            audioEngine.start(
                speakerOn = speakerOn,
                isMuted = isMuted,
                onAudioFrameReady = { seq, sampleRateHz, audioBase64 ->
                    latestOnSendAudioChunk(seq, sampleRateHz, audioBase64)
                }
            )
        } else {
            audioEngine.stop()
        }
    }

    LaunchedEffect(session.status, speakerOn, isMuted) {
        if (session.status == "ACTIVE") {
            audioEngine.updateAudioRouting(speakerOn = speakerOn, isMuted = isMuted)
        }
    }

    LaunchedEffect(session.id, session.status, incomingAudioChunks) {
        if (session.status != "ACTIVE") return@LaunchedEffect
        val freshChunks = incomingAudioChunks
            .filter { it.senderUid != currentUserId && it.audioBase64.isNotBlank() }
            .sortedBy { it.seq }
        freshChunks.forEach { chunk ->
            val key = chunk.id.ifBlank { "${chunk.senderUid}_${chunk.seq}" }
            if (playedChunkKeys.add(key)) {
                audioEngine.enqueueRemoteAudio(chunk.audioBase64)
                if (playedChunkKeys.size > 400) {
                    val iter = playedChunkKeys.iterator()
                    if (iter.hasNext()) {
                        iter.next()
                        iter.remove()
                    }
                }
            }
        }
    }

    LaunchedEffect(session.id, session.status) {
        if (session.status == "ACTIVE") {
            while (true) {
                delay(1000L)
                elapsedSec += 1L
            }
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF14111D), Color(0xFF211728), Color(0xFF12101A))
                    )
                )
                .padding(20.dp)
                .testTag("in_app_call_overlay")
        ) {
            // Top call state header
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = EmeraldMint.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = when (session.status) {
                            "ACTIVE" -> {
                                val m = elapsedSec / 60L
                                val s = elapsedSec % 60L
                                "Connected • %02d:%02d".format(m, s)
                            }
                            "RINGING" -> if (isCaller) "Ringing $partnerName…" else "Connecting…"
                            else -> "Call active"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldMint,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = partnerName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(if (isVideo) "In-App Video Call" else "In-App Voice Call")
                        if (isPartnerMuted) append(" • Partner muted")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
                if (session.status == "ACTIVE") {
                    Spacer(modifier = Modifier.height(8.dp))
                    val speakingText = when {
                        remoteAudioLevel > 0.06f -> "$partnerName speaking"
                        !isMuted && localMicLevel > 0.06f -> "Your mic active"
                        isAudioHardwareActive -> "Voice audio live (${if (speakerOn) "Speaker" else "Earpiece"})"
                        else -> "Initializing voice audio…"
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.1f),
                        border = BorderStroke(
                            0.5.dp,
                            if (remoteAudioLevel > 0.06f || (!isMuted && localMicLevel > 0.06f)) {
                                EmeraldMint.copy(alpha = 0.75f)
                            } else {
                                ChampagneGold.copy(alpha = 0.45f)
                            }
                        ),
                        modifier = Modifier.testTag("call_audio_status_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = if (remoteAudioLevel > 0.06f) EmeraldMint else ChampagneGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = speakingText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // Center visual stage + optional CameraX local feed
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isVideo && isMyCameraOn && hasCallPermissions) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 240.dp, height = 310.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black)
                        ) {
                            CallCameraPreview(
                                useFrontCamera = useFrontCamera,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "Your Camera",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ProfileAvatarImage(
                            photoDataUrl = partner?.photoDataUrl,
                            displayName = partnerName,
                            seed = partner?.avatarSeed ?: session.calleeUid,
                            modifier = Modifier.size(124.dp),
                            initialsFontSizeSp = 40
                        )
                        if (!hasCallPermissions) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { permissionLauncher.launch(requiredPerms) },
                                colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
                            ) {
                                Text("Grant Mic & Camera Access")
                            }
                        }
                    }
                }
            }

            // Bottom Controls
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.1f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallControlIcon(
                        icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        label = if (isMuted) "Unmute" else "Mute",
                        active = isMuted,
                        onClick = onToggleMute,
                        tag = "call_mute_button"
                    )
                    CallControlIcon(
                        icon = Icons.Filled.VolumeUp,
                        label = if (speakerOn) "Speaker" else "Earpiece",
                        active = speakerOn,
                        onClick = { speakerOn = !speakerOn },
                        tag = "call_speaker_button"
                    )
                    if (isVideo) {
                        CallControlIcon(
                            icon = if (isMyCameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                            label = if (isMyCameraOn) "Cam On" else "Cam Off",
                            active = !isMyCameraOn,
                            onClick = onToggleCamera,
                            tag = "call_camera_toggle_button"
                        )
                        CallControlIcon(
                            icon = Icons.Filled.Cameraswitch,
                            label = "Flip",
                            active = false,
                            onClick = { useFrontCamera = !useFrontCamera },
                            tag = "call_flip_camera_button"
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(54.dp)
                        ) {
                            IconButton(
                                onClick = { onEndCall(elapsedSec) },
                                modifier = Modifier.testTag("call_end_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CallEnd,
                                    contentDescription = stringResource(R.string.chat_end_call_cd),
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("End", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (active) Color.White else Color.White.copy(alpha = 0.14f),
            modifier = Modifier.size(48.dp)
        ) {
            IconButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) Color.Black else Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun CallCameraPreview(
    useFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraBindError by remember { mutableIntStateOf(0) }

    if (cameraBindError > 0) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Camera preview unavailable", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        return
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = previewView.surfaceProvider
                        }
                        val selector = if (useFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                    } catch (e: Exception) {
                        Log.w(TAG, "Call camera preview failed to bind", e)
                        cameraBindError += 1
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        },
        modifier = modifier
    )
}
