package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.PublicProfile
import com.example.ui.theme.CoralRose
import com.example.util.PreparedVideoUpload
import com.example.util.MediaImageHelper
import com.example.util.VideoClipHelper
import kotlinx.coroutines.launch

@Composable
fun CreatePostInlineBanner(
    myProfile: PublicProfile?,
    onOpenComposer: () -> Unit,
    onPhotoClick: () -> Unit,
    onVideoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
            .fillMaxWidth()
            .testTag("create_post_inline_banner")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenComposer),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileAvatarImage(
                    photoDataUrl = myProfile?.photoDataUrl,
                    displayName = myProfile?.displayName ?: "Me",
                    seed = myProfile?.avatarSeed ?: "me",
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    initialsFontSizeSp = 15
                )

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "What's on your mind?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onPhotoClick,
                        modifier = Modifier.testTag("inline_add_photo_btn")
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = CoralRose, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photo", style = MaterialTheme.typography.labelMedium)
                    }

                    FilledTonalButton(
                        onClick = onVideoClick,
                        modifier = Modifier.testTag("inline_add_video_btn")
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = CoralRose, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Video", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Button(
                    onClick = onOpenComposer,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRose),
                    modifier = Modifier.testTag("inline_create_post_btn")
                ) {
                    Text("Post")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostComposerSheet(
    myProfile: PublicProfile?,
    initialPhotos: List<String> = emptyList(),
    initialVideo: PreparedVideoUpload? = null,
    onCreateStoryClick: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onPublish: (caption: String, photoDataUrl: String?, photoUrls: List<String>, video: PreparedVideoUpload?) -> Unit,
    onPublishWithResult: ((caption: String, photoDataUrl: String?, photoUrls: List<String>, video: PreparedVideoUpload?, onComplete: (Boolean, String?) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    val photoUrls = remember { mutableStateListOf<String>().apply { addAll(initialPhotos) } }
    var preparedVideo by remember { mutableStateOf<PreparedVideoUpload?>(initialVideo) }
    var isPreparingMedia by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isPreparingMedia = true
                preparedVideo = null
                val totalTargetCount = (photoUrls.size + uris.size).coerceIn(1, 6)
                val perPhotoMaxBytes = if (totalTargetCount == 1) {
                    340_000
                } else {
                    (460_000 / totalTargetCount).coerceIn(72_000, 128_000)
                }
                val perPhotoMaxDim = if (totalTargetCount == 1) 1280 else 1080
                uris.forEach { uri ->
                    if (photoUrls.size < 6) {
                        val dataUrl = MediaImageHelper.compressUriForFeedPostDataUrl(
                            context = context,
                            uri = uri,
                            maxDim = perPhotoMaxDim,
                            maxRawBytes = perPhotoMaxBytes
                        )
                        if (dataUrl != null && dataUrl !in photoUrls) {
                            photoUrls.add(dataUrl)
                        }
                    }
                }
                isPreparingMedia = false
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isPreparingMedia = true
                photoUrls.clear()
                val res = VideoClipHelper.prepareVideoForUpload(context, uri)
                isPreparingMedia = false
                res.onSuccess { clip ->
                    preparedVideo = clip
                }.onFailure { err ->
                    errorMsg = err.localizedMessage ?: "Could not process video"
                }
            }
        }
    }

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
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .testTag("create_post_composer_sheet"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                    Text(
                        text = "Create Post",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            if (isPublishing) return@Button
                            val trimmedCaption = caption.trim()
                            if (trimmedCaption.isBlank() && photoUrls.isEmpty() && preparedVideo == null) {
                                errorMsg = "Add a caption or media before posting"
                                return@Button
                            }
                            isPublishing = true
                            errorMsg = null
                            val finalCaption = trimmedCaption
                            val firstPhoto = photoUrls.firstOrNull()
                            val multiPhotos = if (photoUrls.size > 1) photoUrls.toList() else emptyList()
                            val vid = preparedVideo

                            scope.launch {
                                try {
                                    kotlinx.coroutines.withTimeout(45_000L) {
                                        val pubResult = onPublishWithResult
                                        if (pubResult != null) {
                                            var done = false
                                            pubResult(finalCaption, firstPhoto, multiPhotos, vid) { success, errMsg ->
                                                done = true
                                                isPublishing = false
                                                if (success) {
                                                    onDismiss()
                                                } else {
                                                    errorMsg = errMsg ?: "Publish failed. Please try again."
                                                }
                                            }
                                            while (!done) { kotlinx.coroutines.delay(100L) }
                                        } else {
                                            onPublish(finalCaption, firstPhoto, multiPhotos, vid)
                                            isPublishing = false
                                            onDismiss()
                                        }
                                    }
                                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                    android.util.Log.e("CreatePostComposer", "Publish timed out", e)
                                    errorMsg = "Publishing timed out. Check network connection."
                                    isPublishing = false
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    isPublishing = false
                                    throw e
                                } catch (e: Exception) {
                                    android.util.Log.e("CreatePostComposer", "Publish error", e)
                                    errorMsg = e.localizedMessage ?: "Publish failed"
                                    isPublishing = false
                                } finally {
                                    isPublishing = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralRose),
                        enabled = !isPreparingMedia && !isPublishing && (caption.isNotBlank() || photoUrls.isNotEmpty() || preparedVideo != null),
                        modifier = Modifier.testTag("publish_post_submit_btn")
                    ) {
                        if (isPublishing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Share")
                        }
                    }
                }

                // Author Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileAvatarImage(
                        photoDataUrl = myProfile?.photoDataUrl,
                        displayName = myProfile?.displayName ?: "Me",
                        seed = myProfile?.avatarSeed ?: "me",
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        initialsFontSizeSp = 16
                    )
                    Column {
                        Text(
                            text = myProfile?.displayName ?: "Kindred Member",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Caption Input
                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 1000) caption = it },
                    placeholder = { Text("What's on your mind? Share updates, thoughts, or tag #vibes #kindred...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("composer_caption_input")
                )

                // Hashtag Quick Suggestions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("#kindred", "#connect", "#lifestyle", "#travel", "#vibes").forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clickable {
                                if (!caption.contains(tag)) {
                                    caption = if (caption.isBlank()) tag else "$caption $tag"
                                }
                            }
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = CoralRose,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }



                // Loading media
                if (isPreparingMedia) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = CoralRose, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Preparing media...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Media Previews
                if (photoUrls.isNotEmpty()) {
                    Text(
                        text = "Attached Photos (${photoUrls.size}/6)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(photoUrls) { idx, url ->
                            val bmp = remember(url) {
                                if (url.startsWith("data:image")) MediaImageHelper.decodeBase64DataUrl(url) else null
                            }
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF221A26))
                            ) {
                                if (bmp != null) {
                                    Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                IconButton(
                                    onClick = { photoUrls.removeAt(idx) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                if (preparedVideo != null) {
                    val clip = preparedVideo!!
                    Text(
                        text = "Attached Video Clip",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    val thumb = remember(clip.thumbnailDataUrl) {
                        clip.thumbnailDataUrl?.let { MediaImageHelper.decodeBase64DataUrl(it) }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF14101A))
                    ) {
                        if (thumb != null) {
                            Image(bitmap = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp).align(Alignment.Center))
                        IconButton(
                            onClick = { preparedVideo = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove video", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Add Media Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("composer_pick_photos_btn")
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = CoralRose)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Photos")
                    }

                    OutlinedButton(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("composer_pick_video_btn")
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = CoralRose)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Video")
                    }
                }



                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
