package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.communication.GifCatalog
import com.example.model.ChatMessage
import com.example.model.PublicProfile
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.LocationAndGeoHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun DayDividerPill(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
internal fun PartnerTypingBubble(
    partner: PublicProfile?,
    partnerId: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typingDots")
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "d1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 150), RepeatMode.Reverse),
        label = "d2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 300), RepeatMode.Reverse),
        label = "d3"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .testTag("partner_typing_indicator"),
        verticalAlignment = Alignment.Bottom
    ) {
        ProfileAvatarImage(
            photoDataUrl = partner?.photoDataUrl,
            displayName = partner?.displayName ?: "M",
            seed = partner?.avatarSeed ?: partnerId,
            modifier = Modifier.size(28.dp),
            initialsFontSizeSp = 12
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                listOf(dot1, dot2, dot3).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .scale(0.8f + alpha * 0.25f)
                            .background(CoralRose.copy(alpha = alpha), CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${partner?.displayName ?: "Match"} is typing…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun ChatBubble(
    message: ChatMessage,
    isMine: Boolean,
    showPartnerAvatar: Boolean,
    partner: PublicProfile?,
    partnerId: String,
    nowMillis: Long = System.currentTimeMillis(),
    playingVoiceMessageId: String? = null,
    onOpenMedia: (ChatMessage) -> Unit = {},
    onToggleVoicePlayback: (ChatMessage) -> Unit = {}
) {
    if (message.messageType == "CALL") {
        val isVideo = message.text.contains("Video", ignoreCase = true)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("call_log_bubble_${message.id}"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeLabel = remember(message.createdAt) {
        val date: Date = message.createdAt?.toDate() ?: Date()
        timeFormatter.format(date)
    }
    val remainingSec = message.remainingSecondsAt(nowMillis)
    val isReadByPartner = isMine && partnerId.isNotBlank() && message.isReadBy(partnerId)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_bubble_${message.id}"),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMine) {
            if (showPartnerAvatar) {
                ProfileAvatarImage(
                    photoDataUrl = partner?.photoDataUrl,
                    displayName = partner?.displayName ?: "M",
                    seed = partner?.avatarSeed ?: partnerId,
                    modifier = Modifier.size(28.dp),
                    initialsFontSizeSp = 12
                )
            } else {
                Spacer(modifier = Modifier.width(28.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        val bubbleShape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = if (isMine) 20.dp else 6.dp,
            bottomEnd = if (isMine) 6.dp else 20.dp
        )

        Surface(
            shape = bubbleShape,
            color = if (isMine) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = if (isMine) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .widthIn(max = 296.dp)
                .then(
                    if (isMine) {
                        Modifier.background(
                            brush = Brush.linearGradient(
                                colors = listOf(CoralRose, CoralRose.copy(alpha = 0.86f))
                            ),
                            shape = bubbleShape
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (message.messageType == "ICEBREAKER") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = if (isMine) Color.White.copy(alpha = 0.9f) else ChampagneGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Icebreaker",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMine) Color.White.copy(alpha = 0.9f) else ChampagneGold
                        )
                    }
                }

                when (message.messageType) {
                    "PHOTO" -> PhotoMessageBody(
                        message = message,
                        isMine = isMine,
                        onOpenMedia = onOpenMedia
                    )

                    "VOICE" -> VoiceMessageBody(
                        message = message,
                        isMine = isMine,
                        isPlaying = playingVoiceMessageId == message.id,
                        onTogglePlayback = { onToggleVoicePlayback(message) }
                    )

                    "GIF" -> GifMessageBody(
                        message = message,
                        isMine = isMine
                    )
                }

                if (message.text.isNotBlank()) {
                    if (message.messageType in setOf("PHOTO", "VOICE", "GIF")) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (message.viewOnce || remainingSec != null || message.disappearingSeconds > 0L) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = "Disappearing timer",
                            tint = if (isMine) Color.White.copy(alpha = 0.85f) else ChampagneGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = when {
                                remainingSec != null -> formatCountdownShort(remainingSec)
                                message.viewOnce -> "View once"
                                else -> formatCountdownShort(message.disappearingSeconds)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMine) Color.White.copy(alpha = 0.85f) else ChampagneGold,
                            modifier = Modifier.testTag("timer_badge_${message.id}")
                        )
                    }
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMine) {
                            Color.White.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (isMine) {
                        Icon(
                            imageVector = if (isReadByPartner) Icons.Filled.DoneAll else Icons.Filled.Done,
                            contentDescription = if (isReadByPartner) "Read" else "Sent",
                            tint = if (isReadByPartner) Color(0xFFA7F3D0) else Color.White.copy(alpha = 0.78f),
                            modifier = Modifier
                                .size(14.dp)
                                .testTag(if (isReadByPartner) "read_receipt_read_${message.id}" else "read_receipt_sent_${message.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoMessageBody(
    message: ChatMessage,
    isMine: Boolean,
    onOpenMedia: (ChatMessage) -> Unit
) {
    if (message.viewOnce && !isMine && !message.openedByRecipient) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.65f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenMedia(message) }
                .testTag("view_once_photo_card_${message.id}")
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = CircleShape, color = ChampagneGold.copy(alpha = 0.2f), modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                    }
                }
                Column {
                    Text("View-Once Photo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Tap to view before it disappears", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    val bitmap = remember(message.mediaDataUrl) {
        val raw = message.mediaDataUrl ?: return@remember null
        runCatching {
            val base64 = raw.substringAfter("base64,", raw)
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Shared chat photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onOpenMedia(message) }
                .testTag("photo_bubble_image_${message.id}")
        )
    } else {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.16f),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenMedia(message) }
                .padding(4.dp)
        ) {
            Text(
                text = "📷 Shared photo",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun VoiceMessageBody(
    message: ChatMessage,
    isMine: Boolean,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit
) {
    val durationLabel = remember(message.mediaDurationSec) {
        val total = message.mediaDurationSec.coerceAtLeast(1L)
        val m = total / 60L
        val s = total % 60L
        "%d:%02d".format(m, s)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isMine) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onTogglePlayback)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("voice_bubble_${message.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isMine) Color.White else CoralRose,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                    tint = if (isMine) CoralRose else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = if (isMine) Color.White else CoralRose,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isPlaying) "Playing voice note…" else "Voice note",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "$durationLabel • Tap to ${if (isPlaying) "stop" else "listen"}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isMine) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GifMessageBody(
    message: ChatMessage,
    isMine: Boolean
) {
    val reaction = GifCatalog.findById(message.mediaGifId)
    if (reaction != null) {
        val infinite = rememberInfiniteTransition(label = "gifPulse_${message.id}")
        val pulse by infinite.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "gifScale"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(GifCatalog.primaryColor(reaction), GifCatalog.secondaryColor(reaction))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("gif_bubble_${message.id}")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(pulse),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Gif, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(reaction.badgeText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = reaction.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = reaction.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
        }
    } else if (GifCatalog.isHttpGifUrl(message.mediaGifId)) {
        AsyncImage(
            model = message.mediaGifId,
            contentDescription = "Shared GIF",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(14.dp))
                .testTag("gif_bubble_${message.id}")
        )
    } else {
        Text(
            text = "🎞️ Animated GIF",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatCountdownShort(seconds: Long): String {
    return when {
        seconds >= 3600L -> "${seconds / 3600L}h"
        seconds >= 60L -> "${seconds / 60L}m"
        else -> "${seconds}s"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PartnerProfileQuickSheet(
    partner: PublicProfile,
    distanceKm: Double?,
    onUnmatchClick: (() -> Unit)? = null,
    onBlockClick: (() -> Unit)? = null
) {
    val photos = remember(partner.userId, partner.photoDataUrl, partner.profilePhotos) {
        partner.allProfilePhotos()
    }
    var galleryStartIndex by remember(partner.userId) { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp)
            .testTag("partner_profile_quick_sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = photos.isNotEmpty()) { galleryStartIndex = 0 }
                    .testTag("partner_sheet_avatar")
            ) {
                ProfileAvatarImage(
                    photoDataUrl = partner.photoDataUrl,
                    displayName = partner.displayName,
                    seed = partner.avatarSeed,
                    modifier = Modifier.size(68.dp),
                    initialsFontSizeSp = 24
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${partner.displayName}, ${partner.age}",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VerifiedShieldBadge(isVerified = partner.isProfileVerified, compact = true)
                    if (partner.isProfileVerified) {
                        Text(
                            text = "Trust ${partner.verificationScore}/100",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldMint
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = buildString {
                            append(partner.formattedLocation())
                            if (distanceKm != null) {
                                append(" • ")
                                append(LocationAndGeoHelper.formatDistanceLabel(distanceKm))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (photos.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Profile Photos (${photos.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    FilledTonalButton(
                        onClick = { galleryStartIndex = 0 },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sheet_view_photos_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Photos (${photos.size})")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    photos.forEachIndexed { index, photoUrl ->
                        Box(
                            modifier = Modifier
                                .size(width = 88.dp, height = 108.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { galleryStartIndex = index }
                                .testTag("partner_photo_thumb_$index")
                        ) {
                            ProfileAvatarImage(
                                photoDataUrl = photoUrl,
                                displayName = partner.displayName,
                                seed = partner.avatarSeed,
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(14.dp),
                                initialsFontSizeSp = 24
                            )
                        }
                    }
                }
            }
        }

        if (partner.bio.isNotBlank()) {
            Text(
                text = partner.bio,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (partner.interests.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                partner.interests.forEach { interest ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = interest,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        if (onUnmatchClick != null || onBlockClick != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onUnmatchClick != null) {
                    OutlinedButton(
                        onClick = onUnmatchClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sheet_unmatch_user_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unmatch")
                    }
                }
                if (onBlockClick != null) {
                    OutlinedButton(
                        onClick = onBlockClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sheet_block_user_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Block,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Block")
                    }
                }
            }
        }
    }

    val startIdx = galleryStartIndex
    if (startIdx != null && photos.isNotEmpty()) {
        PartnerPhotoGalleryDialog(
            photos = photos,
            initialIndex = startIdx,
            partnerName = partner.displayName,
            onDismiss = { galleryStartIndex = null }
        )
    }
}

@Composable
internal fun PartnerPhotoGalleryDialog(
    photos: List<String>,
    initialIndex: Int = 0,
    partnerName: String,
    onDismiss: () -> Unit
) {
    var activeIndex by remember(photos, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)))
    }
    val activePhoto = photos.getOrNull(activeIndex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .padding(16.dp)
                .testTag("partner_photo_gallery_dialog")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$partnerName's Photos",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Photo ${activeIndex + 1} of ${photos.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.testTag("gallery_counter_label")
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("gallery_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close gallery",
                        tint = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 72.dp),
                contentAlignment = Alignment.Center
            ) {
                val decodedBitmap = remember(activePhoto) {
                    com.example.util.VerificationAnalyzer.decodeBase64DataUrl(activePhoto)
                }
                if (decodedBitmap != null) {
                    Image(
                        bitmap = decodedBitmap,
                        contentDescription = "$partnerName photo ${activeIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
                    )
                } else if (!activePhoto.isNullOrBlank() && activePhoto.startsWith("http")) {
                    AsyncImage(
                        model = activePhoto,
                        contentDescription = "$partnerName photo ${activeIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
                    )
                } else {
                    ProfileAvatarImage(
                        photoDataUrl = activePhoto,
                        displayName = partnerName,
                        seed = partnerName,
                        modifier = Modifier.size(220.dp),
                        shape = RoundedCornerShape(24.dp),
                        initialsFontSizeSp = 64
                    )
                }
            }

            if (photos.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            activeIndex = if (activeIndex > 0) activeIndex - 1 else photos.lastIndex
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("gallery_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous photo",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Prev")
                    }

                    FilledTonalButton(
                        onClick = {
                            activeIndex = (activeIndex + 1) % photos.size
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("gallery_next_button")
                    ) {
                        Text("Next")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next photo",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

internal fun dayBucketLabel(date: Date?, fallbackFormatter: SimpleDateFormat): String {
    if (date == null) return "Today"
    val nowCal = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = date }
    return when {
        nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) -> "Today"
        nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) - msgCal.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
        else -> fallbackFormatter.format(date)
    }
}
