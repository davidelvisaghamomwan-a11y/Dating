package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ai.AiIcebreakerService
import com.example.ai.IcebreakerAiUiState
import com.example.communication.GifCatalog
import com.example.communication.KindredGifReaction
import com.example.model.ChatMessage
import com.example.model.PublicProfile
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IcebreakerCoachSheetContent(
    myProfile: PublicProfile?,
    partnerProfile: PublicProfile?,
    aiState: IcebreakerAiUiState,
    onGenerateAi: (tone: String) -> Unit,
    onSendIcebreaker: (String) -> Unit,
    onInsertIntoDraft: (String) -> Unit
) {
    val tones = listOf("Balanced", "Playful", "Curious", "Date Idea")
    var selectedTone by remember { mutableStateOf("Balanced") }
    val localSuggestions = remember(myProfile, partnerProfile) {
        AiIcebreakerService.buildProfileBasedOpeners(myProfile, partnerProfile)
    }

    val displayedSuggestions = when (aiState) {
        is IcebreakerAiUiState.Success -> aiState.suggestions
        else -> localSuggestions
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .testTag("icebreaker_coach_sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Icebreakers for ${partnerProfile?.displayName ?: "Match"}",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Tailored to shared interests, bio details & city.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { onGenerateAi(selectedTone) },
                enabled = aiState !is IcebreakerAiUiState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = CoralRose),
                modifier = Modifier.testTag("generate_ai_openers_button")
            ) {
                if (aiState is IcebreakerAiUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("AI Refresh")
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tones.forEach { tone ->
                FilterChip(
                    selected = selectedTone == tone,
                    onClick = { selectedTone = tone },
                    label = { Text(tone) }
                )
            }
        }

        if (aiState is IcebreakerAiUiState.Error) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            ) {
                Text(
                    text = "${aiState.message} Showing profile-matched prompts instead.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.height(260.dp)
        ) {
            itemsIndexed(displayedSuggestions) { index, suggestion ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("icebreaker_suggestion_$index")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = CircleShape, color = ChampagneGold.copy(alpha = 0.18f)) {
                                Text(
                                    text = suggestion.tone,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampagneGold,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = suggestion.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = suggestion.prompt,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onInsertIntoDraft(suggestion.prompt) }) {
                                Text("Edit")
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = { onSendIcebreaker(suggestion.prompt) },
                                colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
                            ) {
                                Text("Send")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GifPickerSheetContent(
    onSelectReaction: (KindredGifReaction) -> Unit,
    onSendCustomGifUrl: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var customUrl by remember { mutableStateOf("") }
    val filtered = remember(selectedCategory) {
        if (selectedCategory == "All") {
            GifCatalog.REACTIONS
        } else {
            GifCatalog.REACTIONS.filter { it.category == selectedCategory }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .testTag("gif_picker_sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Send Animated GIF", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GifCatalog.CATEGORIES.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat) }
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.height(240.dp)
        ) {
            items(filtered, key = { it.id }) { reaction ->
                Box(
                    modifier = Modifier
                        .height(92.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    GifCatalog.primaryColor(reaction),
                                    GifCatalog.secondaryColor(reaction)
                                )
                            )
                        )
                        .clickable { onSelectReaction(reaction) }
                        .padding(12.dp)
                        .testTag("gif_option_${reaction.id}"),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Text(
                            text = reaction.badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.86f)
                        )
                        Text(
                            text = reaction.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                placeholder = { Text("Or paste https://.../.gif URL") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onSendCustomGifUrl(customUrl.trim()) },
                enabled = GifCatalog.isHttpGifUrl(customUrl),
                colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
internal fun DisappearingTimerSheetContent(
    currentMatchTtlSeconds: Long,
    viewOnceNextMedia: Boolean,
    onSelectMatchTtl: (Long) -> Unit,
    onToggleViewOnce: (Boolean) -> Unit
) {
    val options = listOf(
        0L to "Off (Persistent chat)",
        30L to "30 seconds after send",
        300L to "5 minutes after send",
        86400L to "24 hours after send"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp)
            .testTag("disappearing_timer_sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Timer, contentDescription = null, tint = ChampagneGold)
            Text("Disappearing & Timed Media", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = "Messages sent while active auto-expire from chat once their countdown finishes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        options.forEach { (seconds, label) ->
            val selected = currentMatchTtlSeconds == seconds
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) CoralRose.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (selected) BorderStroke(1.dp, CoralRose) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectMatchTtl(seconds) }
                    .testTag("timer_option_$seconds")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = CoralRose, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(20.dp))
                    Column {
                        Text("View-Once Photos", style = MaterialTheme.typography.titleSmall)
                        Text("Partner can open photo once (10s auto-burn)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(
                    checked = viewOnceNextMedia,
                    onCheckedChange = onToggleViewOnce,
                    modifier = Modifier.testTag("view_once_switch")
                )
            }
        }
    }
}

@Composable
internal fun UnmatchOrBlockDialog(
    partnerName: String,
    onDismiss: () -> Unit,
    onUnmatchOnly: () -> Unit,
    onUnmatchAndBlock: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End conversation with $partnerName?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose whether to simply unmatch or permanently block $partnerName from future discovery and messaging.")
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onUnmatchOnly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("dialog_unmatch_only_button")
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unmatch")
                }
                Button(
                    onClick = onUnmatchAndBlock,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("dialog_unmatch_and_block_button")
                ) {
                    Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unmatch & Block $partnerName")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun MediaViewerDialog(
    message: ChatMessage,
    onDismiss: () -> Unit
) {
    val bitmap = remember(message.mediaDataUrl) {
        val raw = message.mediaDataUrl ?: return@remember null
        runCatching {
            val base64 = raw.substringAfter("base64,", raw)
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    var remainingSec by remember(message.id) { mutableLongStateOf(if (message.viewOnce) 10L else 0L) }
    if (message.viewOnce) {
        LaunchedEffect(message.id) {
            while (remainingSec > 0L) {
                delay(1000L)
                remainingSec -= 1L
            }
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.94f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.viewOnce) {
                        Surface(shape = CircleShape, color = ChampagneGold.copy(alpha = 0.25f)) {
                            Text(
                                text = "View once • ${remainingSec}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = ChampagneGold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close preview", tint = Color.White)
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Fullscreen chat photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(440.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
                if (message.text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}
