package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
internal fun ChatBubble(
    message: ChatMessage,
    isMine: Boolean,
    showPartnerAvatar: Boolean,
    partner: PublicProfile?,
    partnerId: String
) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeLabel = remember(message.createdAt) {
        val date: Date = message.createdAt?.toDate() ?: Date()
        timeFormatter.format(date)
    }
    val isIcebreaker = message.messageType == "ICEBREAKER"

    Row(
        modifier = Modifier.fillMaxWidth(),
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
                if (isIcebreaker) {
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
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                            imageVector = Icons.Filled.Done,
                            contentDescription = "Sent",
                            tint = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PartnerProfileQuickSheet(
    partner: PublicProfile,
    distanceKm: Double?,
    onBlockClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProfileAvatarImage(
                photoDataUrl = partner.photoDataUrl,
                displayName = partner.displayName,
                seed = partner.avatarSeed,
                modifier = Modifier.size(68.dp),
                initialsFontSizeSp = 24
            )
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
                            append(partner.cityLabel)
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

        if (onBlockClick != null) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onBlockClick,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sheet_block_user_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Block ${partner.displayName}")
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
