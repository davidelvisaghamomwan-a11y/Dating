package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.PublicProfile
import com.example.ui.theme.CoralRose
import com.example.util.PreparedVideoUpload
import com.example.util.MediaImageHelper
import com.example.util.VideoClipHelper
import kotlinx.coroutines.launch
import java.io.File

private const val MAX_STORY_VIDEO_SECONDS = 30
private val StoryInstagramBlue = Color(0xFF0095F6)

private enum class StoryCaptureMode {
    PHOTO,
    VIDEO
}

private class CaptureStoryVideoContract : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: Context, input: Uri): Intent {
        return super.createIntent(context, input).apply {
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, MAX_STORY_VIDEO_SECONDS)
        }
    }
}

@Composable
fun CreateStoryStudioDialog(
    myProfile: PublicProfile?,
    currentUserId: String,
    uploadProgress: Float? = null,
    onPostPhotoStory: (photoDataUrl: String, caption: String, onComplete: (Boolean) -> Unit) -> Unit,
    onPostVideoStory: (prepared: PreparedVideoUpload, caption: String, onComplete: (Boolean) -> Unit) -> Unit,
    onPostPhotoStoryWithResult: ((photoDataUrl: String, caption: String, onComplete: (Boolean, String?) -> Unit) -> Unit)? = null,
    onPostVideoStoryWithResult: ((prepared: PreparedVideoUpload, caption: String, onComplete: (Boolean, String?) -> Unit) -> Unit)? = null,
    onStatusMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var captureMode by remember { mutableStateOf(StoryCaptureMode.PHOTO) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequestedOnce by remember { mutableStateOf(false) }

    var isPreparingMedia by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }
    var capturedPhotoDataUrl by remember { mutableStateOf<String?>(null) }
    var capturedVideo by remember { mutableStateOf<PreparedVideoUpload?>(null) }
    var captionDraft by remember { mutableStateOf("") }
    var previewError by remember { mutableStateOf<String?>(null) }

    var pendingSystemPhotoFile by remember { mutableStateOf<File?>(null) }
    var pendingSystemVideoFile by remember { mutableStateOf<File?>(null) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionRequestedOnce = true
        hasCameraPermission = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequestedOnce) {
            permissionsLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    fun processPhotoUri(uri: Uri) {
        scope.launch {
            isPreparingMedia = true
            previewError = null
            val dataUrl = MediaImageHelper.compressUriForFeedPostDataUrl(
                context = context,
                uri = uri,
                maxDim = 1080,
                maxRawBytes = 220_000
            )
            isPreparingMedia = false
            if (dataUrl != null) {
                capturedVideo = null
                capturedPhotoDataUrl = dataUrl
            } else {
                previewError = "Could not load selected photo."
            }
        }
    }

    fun processVideoUri(uri: Uri) {
        scope.launch {
            isPreparingMedia = true
            previewError = null
            val res = VideoClipHelper.prepareVideoForUpload(
                context = context,
                uri = uri,
                cacheKey = "story_cam_${System.currentTimeMillis()}"
            )
            isPreparingMedia = false
            res.onSuccess { clip ->
                capturedPhotoDataUrl = null
                capturedVideo = clip
            }.onFailure { err ->
                previewError = err.localizedMessage ?: "Could not prepare story video."
            }
        }
    }

    val takeSystemPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val file = pendingSystemPhotoFile
        pendingSystemPhotoFile = null
        if (saved && file != null && file.exists() && file.length() > 0L) {
            processPhotoUri(Uri.fromFile(file))
        }
    }

    val captureSystemVideoLauncher = rememberLauncherForActivityResult(
        contract = CaptureStoryVideoContract()
    ) { saved ->
        val file = pendingSystemVideoFile
        pendingSystemVideoFile = null
        if (saved && file != null && file.exists() && file.length() > 0L) {
            processVideoUri(Uri.fromFile(file))
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("video/")) {
                processVideoUri(uri)
            } else {
                processPhotoUri(uri)
            }
        }
    }

    LaunchedEffect(hasCameraPermission, useFrontCamera, captureMode) {
        if (!hasCameraPermission) return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        try {
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build().also {
                previewViewRef?.let { pv -> it.setSurfaceProvider(pv.surfaceProvider) }
            }

            val cameraSelector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            if (captureMode == StoryCaptureMode.PHOTO) {
                val cap = ImageCapture.Builder()
                    .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()
                imageCapture = cap
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, cap)
            } else {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD, androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                    .build()
                val vCap = VideoCapture.withOutput(recorder)
                videoCapture = vCap
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, vCap)
            }
        } catch (e: Exception) {
            previewError = "Camera setup failed: ${e.localizedMessage}"
        }
    }

    fun capturePhotoX() {
        val cap = imageCapture ?: return
        val photoFile = File(context.cacheDir, "story_photo_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        cap.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    processPhotoUri(Uri.fromFile(photoFile))
                }
                override fun onError(exception: ImageCaptureException) {
                    runCatching {
                        val (file, uri) = VideoClipHelper.createCaptureStoryPhotoUri(context)
                        pendingSystemPhotoFile = file
                        takeSystemPhotoLauncher.launch(uri)
                    }
                }
            }
        )
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun toggleVideoRecordingX() {
        val vCap = videoCapture ?: return
        val recording = activeRecording
        if (recording != null) {
            recording.stop()
            activeRecording = null
            isRecordingVideo = false
            return
        }

        val videoFile = File(context.cacheDir, "story_video_${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(videoFile).build()
        activeRecording = vCap.output
            .prepareRecording(context, outputOptions)
            .apply { if (hasAudioPermission) withAudioEnabled() }
            .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        isRecordingVideo = true
                    }
                    is VideoRecordEvent.Finalize -> {
                        isRecordingVideo = false
                        activeRecording = null
                        if (!recordEvent.hasError()) {
                            processVideoUri(Uri.fromFile(videoFile))
                        } else {
                            onStatusMessage("Video recording failed")
                        }
                    }
                    else -> Unit
                }
            }
    }

    Dialog(
        onDismissRequest = { if (!isPublishing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val photoData = capturedPhotoDataUrl
                val vidUpload = capturedVideo

                if (photoData == null && vidUpload == null) {
                    if (hasCameraPermission) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    previewViewRef = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Camera permission needed to record stories",
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Button(
                                onClick = {
                                    permissionsLauncher.launch(
                                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StoryInstagramBlue)
                            ) {
                                Text("Grant Permission")
                            }
                            OutlinedButton(
                                onClick = {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                }
                            ) {
                                Text("Choose from Gallery")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        if (captureMode == StoryCaptureMode.PHOTO && hasCameraPermission) {
                            IconButton(onClick = { flashEnabled = !flashEnabled }) {
                                Icon(
                                    imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                    contentDescription = "Flash",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        if (hasCameraPermission) {
                            IconButton(onClick = { useFrontCamera = !useFrontCamera }) {
                                Icon(
                                    imageVector = Icons.Filled.Cameraswitch,
                                    contentDescription = "Switch camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PHOTO",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (captureMode == StoryCaptureMode.PHOTO) Color.White else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clickable { if (!isRecordingVideo) captureMode = StoryCaptureMode.PHOTO }
                                    .padding(6.dp)
                            )
                            Text(
                                text = "VIDEO",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (captureMode == StoryCaptureMode.VIDEO) Color.White else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clickable { captureMode = StoryCaptureMode.VIDEO }
                                    .padding(6.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 36.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = "Gallery", tint = Color.White, modifier = Modifier.size(22.dp))
                            }

                            Surface(
                                shape = CircleShape,
                                color = if (isRecordingVideo) CoralRose else Color.White,
                                modifier = Modifier
                                    .size(76.dp)
                                    .border(4.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                    .clickable {
                                        if (captureMode == StoryCaptureMode.PHOTO) {
                                            if (hasCameraPermission) capturePhotoX()
                                            else {
                                                runCatching {
                                                    val (file, uri) = VideoClipHelper.createCaptureStoryPhotoUri(context)
                                                    pendingSystemPhotoFile = file
                                                    takeSystemPhotoLauncher.launch(uri)
                                                }
                                            }
                                        } else {
                                            if (hasCameraPermission) toggleVideoRecordingX()
                                            else {
                                                runCatching {
                                                    val (file, uri) = VideoClipHelper.createCaptureStoryVideoUri(context)
                                                    pendingSystemVideoFile = file
                                                    captureSystemVideoLauncher.launch(uri)
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isRecordingVideo) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Color.White, RoundedCornerShape(4.dp))
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (captureMode == StoryCaptureMode.PHOTO) {
                                        runCatching {
                                            val (file, uri) = VideoClipHelper.createCaptureStoryPhotoUri(context)
                                            pendingSystemPhotoFile = file
                                            takeSystemPhotoLauncher.launch(uri)
                                        }
                                    } else {
                                        runCatching {
                                            val (file, uri) = VideoClipHelper.createCaptureStoryVideoUri(context)
                                            pendingSystemVideoFile = file
                                            captureSystemVideoLauncher.launch(uri)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (captureMode == StoryCaptureMode.PHOTO) Icons.Filled.PhotoCamera else Icons.Filled.Videocam,
                                    contentDescription = "System Camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (photoData != null) {
                            val bmp = remember(photoData) { MediaImageHelper.decodeBase64DataUrl(photoData) }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "Captured story",
                                    contentScale = ContentScale.Crop,
                                    filterQuality = FilterQuality.High,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else if (vidUpload != null) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        setVideoURI(Uri.fromFile(vidUpload.localCachedFile))
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                            start()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    capturedPhotoDataUrl = null
                                    capturedVideo = null
                                    captionDraft = ""
                                }
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Discard", tint = Color.White, modifier = Modifier.size(28.dp))
                            }

                            Text(
                                text = "Add to story",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Button(
                                onClick = {
                                    if (isPublishing) return@Button
                                    isPublishing = true
                                    previewError = null
                                    val cap = captionDraft.trim()
                                    val photo = capturedPhotoDataUrl
                                    val vid = capturedVideo
                                     scope.launch {
                                         try {
                                             kotlinx.coroutines.withTimeout(com.example.data.CloudMediaStorageService.UPLOAD_TIMEOUT_MS + 5_000L) {
                                                 if (photo != null) {
                                                     var done = false
                                                     val photoWithRes = onPostPhotoStoryWithResult
                                                     if (photoWithRes != null) {
                                                         photoWithRes(photo, cap) { success, errMsg ->
                                                             done = true
                                                             isPublishing = false
                                                             if (success) onDismiss()
                                                             else previewError = errMsg ?: "Failed to upload photo story"
                                                         }
                                                     } else {
                                                         onPostPhotoStory(photo, cap) { success ->
                                                             done = true
                                                             isPublishing = false
                                                             if (success) onDismiss()
                                                             else previewError = "Failed to upload photo story"
                                                         }
                                                     }
                                                     while (!done) { kotlinx.coroutines.delay(100L) }
                                                 } else if (vid != null) {
                                                     var done = false
                                                     val vidWithRes = onPostVideoStoryWithResult
                                                     if (vidWithRes != null) {
                                                         vidWithRes(vid, cap) { success, errMsg ->
                                                             done = true
                                                             isPublishing = false
                                                             if (success) onDismiss()
                                                             else previewError = errMsg ?: "Failed to upload video story"
                                                         }
                                                     } else {
                                                         onPostVideoStory(vid, cap) { success ->
                                                             done = true
                                                             isPublishing = false
                                                             if (success) onDismiss()
                                                             else previewError = "Failed to upload video story"
                                                         }
                                                     }
                                                     while (!done) { kotlinx.coroutines.delay(100L) }
                                                 } else {
                                                     isPublishing = false
                                                 }
                                             }
                                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                            android.util.Log.e("CreateStoryStudio", "Story upload timed out", e)
                                            previewError = "Upload timed out. Check network connection."
                                            isPublishing = false
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            isPublishing = false
                                            throw e
                                        } catch (e: Exception) {
                                            android.util.Log.e("CreateStoryStudio", "Story upload error", e)
                                            previewError = e.localizedMessage ?: "Upload failed"
                                            isPublishing = false
                                        } finally {
                                            isPublishing = false
                                        }
                                    }
                                },
                                enabled = !isPublishing,
                                colors = ButtonDefaults.buttonColors(containerColor = StoryInstagramBlue, contentColor = Color.White),
                                modifier = Modifier.height(38.dp)
                            ) {
                                if (isPublishing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("Share", fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .imePadding()
                                .padding(16.dp)
                        ) {
                            OutlinedTextField(
                                value = captionDraft,
                                onValueChange = { if (it.length <= 150) captionDraft = it },
                                placeholder = { Text("Add a caption to your story...", color = Color.White.copy(alpha = 0.7f)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color.White
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("story_caption_input")
                            )
                        }
                    }
                }

                if (isPreparingMedia) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(color = StoryInstagramBlue, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
                            Text(
                                text = "Preparing story media...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                if (isPublishing) {
                    val pct = ((uploadProgress ?: 0f).coerceIn(0f, 1f) * 100).toInt()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator(
                                color = StoryInstagramBlue,
                                strokeWidth = 4.dp,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = if (pct > 0) "Uploading... $pct%" else "Uploading...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (uploadProgress != null) {
                                LinearProgressIndicator(
                                    progress = { uploadProgress.coerceIn(0f, 1f) },
                                    color = StoryInstagramBlue,
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                )
                            }
                        }
                    }
                }

                previewError?.let { errText ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 64.dp)
                            .fillMaxWidth()
                            .testTag("story_upload_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = errText,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { previewError = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss error",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
