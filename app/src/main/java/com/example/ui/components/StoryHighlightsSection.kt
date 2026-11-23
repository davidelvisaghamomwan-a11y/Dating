package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.StoryHighlight
import com.example.model.StoryItem
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.util.VerificationAnalyzer
import kotlinx.coroutines.delay

@Composable
fun StoryHighlightsSection(
    highlights: List<StoryHighlight>,
    isOwner: Boolean,
    onOpenHighlight: (StoryHighlight) -> Unit,
    onOpenCreateHighlight: () -> Unit = {},
    onDeleteHighlight: (String) -> Unit = {},
    compactInstagramStyle: Boolean = false,
    modifier: Modifier = Modifier
) {
    var highlightToDelete by remember { mutableStateOf<StoryHighlight?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("story_highlights_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!compactInstagramStyle) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Story Highlights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isOwner && highlights.isNotEmpty()) {
                    TextButton(
                        onClick = onOpenCreateHighlight,
                        modifier = Modifier.testTag("add_highlight_text_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add New", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (highlights.isEmpty() && !isOwner) {
            if (!compactInstagramStyle) {
                Text(
                    text = "No story highlights added yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    horizontal = if (compactInstagramStyle) 16.dp else 2.dp,
                    vertical = 4.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("highlights_tray_row")
            ) {
                if (!compactInstagramStyle && isOwner) {
                    item(key = "new_highlight_button") {
                        HighlightNewBubble(
                            compactInstagramStyle = false,
                            onClick = onOpenCreateHighlight
                        )
                    }
                }

                items(highlights, key = { it.id }) { highlight ->
                    val ringBrush = Brush.linearGradient(
                        colors = if (compactInstagramStyle) {
                            listOf(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                            )
                        } else {
                            listOf(
                                CoralRose,
                                ChampagneGold,
                                Color(0xFFFF8A65)
                            )
                        }
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clickable { onOpenHighlight(highlight) }
                            .testTag("highlight_bubble_${highlight.id}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .border(
                                    BorderStroke(if (compactInstagramStyle) 1.5.dp else 2.dp, ringBrush),
                                    CircleShape
                                )
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val coverBitmap = remember(highlight.coverUrl) {
                                if (highlight.coverUrl.startsWith("data:image")) {
                                    VerificationAnalyzer.decodeBase64DataUrl(highlight.coverUrl)
                                } else null
                            }
                            if (coverBitmap != null) {
                                Image(
                                    bitmap = coverBitmap,
                                    contentDescription = highlight.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (highlight.coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = highlight.coverUrl,
                                    contentDescription = highlight.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = CoralRose,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Text(
                            text = highlight.title.ifBlank { "Highlight" },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(68.dp)
                        )
                    }
                }

                if (compactInstagramStyle && isOwner) {
                    item(key = "new_highlight_button") {
                        HighlightNewBubble(
                            compactInstagramStyle = true,
                            onClick = onOpenCreateHighlight
                        )
                    }
                }
            }
        }
    }

    highlightToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { highlightToDelete = null },
            title = { Text("Delete Highlight") },
            text = { Text("Are you sure you want to delete '${target.title}'? The original stories will still remain in your archive.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteHighlight(target.id)
                        highlightToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { highlightToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HighlightNewBubble(
    compactInstagramStyle: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("create_highlight_bubble")
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(
                    if (compactInstagramStyle) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest
                )
                .border(
                    border = BorderStroke(
                        width = if (compactInstagramStyle) 1.2.dp else 1.5.dp,
                        color = if (compactInstagramStyle) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "New Highlight",
                tint = if (compactInstagramStyle) MaterialTheme.colorScheme.onSurface else CoralRose,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = "New",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateHighlightDialog(
    archivedStories: List<StoryItem>,
    onDismiss: () -> Unit,
    onSaveHighlight: (title: String, coverUrl: String, items: List<StoryItem>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    val selectedStoryIds = remember { mutableStateListOf<String>() }
    var selectedCoverUrl by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .testTag("create_highlight_dialog"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                    Text(
                        text = "New Story Highlight",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            val pickedStories = archivedStories.filter { it.id in selectedStoryIds }
                            if (pickedStories.isEmpty()) {
                                errorMsg = "Select at least 1 story for this highlight"
                                return@Button
                            }
                            val finalTitle = title.trim().ifBlank { "Highlights" }
                            val finalCover = if (selectedCoverUrl.isNotBlank()) {
                                selectedCoverUrl
                            } else {
                                pickedStories.firstOrNull()?.let {
                                    it.thumbnailDataUrl ?: it.mediaDataUrl
                                }.orEmpty()
                            }
                            onSaveHighlight(finalTitle, finalCover, pickedStories)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralRose),
                        enabled = selectedStoryIds.isNotEmpty(),
                        modifier = Modifier.testTag("save_highlight_btn")
                    ) {
                        Text("Save")
                    }
                }

                // Highlight Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 30) title = it },
                    label = { Text("Highlight Name") },
                    placeholder = { Text("e.g. Trips, Summer, Memories") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("highlight_title_input")
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = "Select Stories from Archive (${selectedStoryIds.size} selected)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                if (archivedStories.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = CoralRose, modifier = Modifier.size(36.dp))
                            Text("No Past Stories in Archive", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Stories you post on Kindred will automatically be saved to your private archive so you can curate them into highlights anytime.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("archive_stories_grid")
                    ) {
                        items(archivedStories, key = { it.id }) { story ->
                            val isSelected = story.id in selectedStoryIds
                            val mediaPreview = remember(story.thumbnailDataUrl, story.mediaDataUrl) {
                                val url = story.thumbnailDataUrl ?: story.mediaDataUrl
                                if (url != null && url.startsWith("data:image")) {
                                    VerificationAnalyzer.decodeBase64DataUrl(url)
                                } else null
                            }

                            Box(
                                modifier = Modifier
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF201A24))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) CoralRose else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        if (isSelected) {
                                            selectedStoryIds.remove(story.id)
                                        } else {
                                            selectedStoryIds.add(story.id)
                                            if (selectedCoverUrl.isBlank()) {
                                                selectedCoverUrl = (story.thumbnailDataUrl ?: story.mediaDataUrl).orEmpty()
                                            }
                                        }
                                    }
                            ) {
                                if (mediaPreview != null) {
                                    Image(
                                        bitmap = mediaPreview,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (story.mediaType == "VIDEO") Icons.Filled.PlayArrow else Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .clip(CircleShape)
                                            .background(CoralRose),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HighlightViewerDialog(
    highlight: StoryHighlight,
    isOwner: Boolean,
    onDismiss: () -> Unit,
    onDeleteHighlight: (String) -> Unit = {}
) {
    val stories = highlight.storyItems
    if (stories.isEmpty()) {
        onDismiss()
        return
    }

    var activeIndex by remember(stories.size) { mutableIntStateOf(0) }
    val currentStory = stories.getOrNull(activeIndex) ?: stories.first()
    var isPaused by remember { mutableStateOf(false) }

    LaunchedEffect(activeIndex, isPaused) {
        if (!isPaused) {
            delay(5_000L)
            if (activeIndex < stories.lastIndex) {
                activeIndex += 1
            } else {
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(14.dp)
                .testTag("highlight_viewer_dialog")
        ) {
            // Media Display
            val imageBitmap = remember(currentStory.mediaDataUrl) {
                if (currentStory.mediaDataUrl?.startsWith("data:image") == true) {
                    VerificationAnalyzer.decodeBase64DataUrl(currentStory.mediaDataUrl)
                } else null
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF14101A)),
                contentAlignment = Alignment.Center
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = currentStory.caption ?: "Highlight story",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = CoralRose,
                            modifier = Modifier.size(54.dp)
                        )
                        if (!currentStory.caption.isNullOrBlank()) {
                            Text(
                                text = currentStory.caption,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }

                // Left/Right Tap Areas for Navigation
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable {
                                if (activeIndex > 0) activeIndex -= 1
                            }
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable {
                                if (activeIndex < stories.lastIndex) {
                                    activeIndex += 1
                                } else {
                                    onDismiss()
                                }
                            }
                    )
                }
            }

            // Top Header: Progress bars + Title + Delete/Close
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Progress indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stories.indices.forEach { index ->
                        val progress by animateFloatAsState(
                            targetValue = when {
                                index < activeIndex -> 1f
                                index == activeIndex -> 1f
                                else -> 0f
                            },
                            label = "highlight_progress"
                        )
                        LinearProgressIndicator(
                            progress = { if (index < activeIndex) 1f else if (index == activeIndex) progress else 0f },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.35f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(CoralRose),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(
                                text = highlight.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${activeIndex + 1} of ${stories.size}",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isOwner) {
                            IconButton(onClick = {
                                onDeleteHighlight(highlight.id)
                                onDismiss()
                            }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Highlight", tint = Color.White)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }

            // Bottom Caption
            if (!currentStory.caption.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = currentStory.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
