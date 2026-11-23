package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.util.VerificationAnalyzer
import com.example.util.VideoClipHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val MAX_STORY_VIDEO_SECONDS = 30
private val StoryBlue = Color(0xFF0095F6)

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
    onPostPhotoStory: (photoDataUrl: String, caption: String, onComplete: (Boolean) -> Unit) -> Unit,
    onPostVideoStory: (prepared: PreparedVideoUpload, caption: String, onComplete: (Boolean) -> Unit) -> Unit,
    onStatusMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionRequestedOnce = true
        hasCameraPermission = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            onStatusMessage("Camera permission denied. You can still pick from gallery.")
        }
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
            val dataUrl = VerificationAnalyzer.compressUriForFeedPostDataUrl(
                context = context,
                uri = uri,
                maxDim = 1280,
                maxRawBytes = 320_000
            )
            isPreparingMedia = false
            if (dataUrl != null) {
                capturedVideo = null
                capturedPhotoDataUrl = dataUrl
            } else {
                val msg = "Could not load captured photo."
                previewError = msg
                onStatusMessage(msg)
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
                val msg = err.localizedMessage ?: "Could not prepare story video."
                previewError = msg
                onStatusMessage(msg)
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

    fun launchSystemPhotoCapture() {
        runCatching {
            val (file, uri) = VideoClipHelper.createCaptureStoryPhotoUri(context)
            pendingSystemPhotoFile = file
            takeSystemPhotoLauncher.launch(uri)
        }.onFailure {
            onStatusMessage("System photo camera unavailable on this device.")
        }
    }

    fun launchSystemVideoCapture() {
        runCatching {
            val (file, uri) = VideoClipHelper.createCaptureStoryVideoUri(context)
            pendingSystemVideoFile = file
            captureSystemVideoLauncher.launch(uri)
        }.onFailure {
            onStatusMessage("System video camera unavailable on this device.")
        }
    }

    val galleryMediaLauncher = rememberLauncherForActivityResult(
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

    Dialog(
        onDismissRequest = { if (!isPublishing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("create_story_sheet"),
            color = Color.Black
        ) {
            val hasCapturedMedia = capturedPhotoDataUrl != null || capturedVideo != null

            if (hasCapturedMedia) {
                StoryPreviewAndShareStage(
                    myProfile = myProfile,
                    currentUserId = currentUserId,
                    photoDataUrl = capturedPhotoDataUrl,
                    preparedVideo = capturedVideo,
                    caption = captionDraft,
                    onCaptionChange = { if (it.length <= 160) captionDraft = it },
                    isPublishing = isPublishing,
                    errorMsg = previewError,
                    onRetake = {
                        if (!isPublishing) {
                            capturedPhotoDataUrl = null
                            capturedVideo = null
                            previewError = null
                        }
                    },
                    onDismiss = { if (!isPublishing) onDismiss() },
                    onShare = {
                        val photo = capturedPhotoDataUrl
                        val vid = capturedVideo
                        val trimmed = captionDraft.trim()
                        if (isPublishing) return@StoryPreviewAndShareStage
                        isPublishing = true
                        previewError = null
                        if (photo != null) {
                            onPostPhotoStory(photo, trimmed) { success ->
                                isPublishing = false
                                if (success) {
                                    onDismiss()
                                } else {
                                    previewError = "Failed to upload story. Tap Share Story to retry."
                                }
                            }
                        } else if (vid != null) {
                            onPostVideoStory(vid, trimmed) { success ->
                                isPublishing = false
                                if (success) {
                                    onDismiss()
                                } else {
                                    previewError = "Failed to upload video story. Tap Share Story to retry."
                                }
                            }
                        } else {
                            isPublishing = false
                        }
                    }
                )
            } else {
                StoryCameraCaptureStage(
                    captureMode = captureMode,
                    onModeChange = { captureMode = it },
                    useFrontCamera = useFrontCamera,
                    onToggleFrontCamera = { useFrontCamera = !useFrontCamera },
                    flashEnabled = flashEnabled,
                    onToggleFlash = { flashEnabled = !flashEnabled },
                    hasCameraPermission = hasCameraPermission,
                    hasAudioPermission = hasAudioPermission,
                    isPreparingMedia = isPreparingMedia,
                    onRequestPermissions = {
                        permissionsLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                        )
                    },
                    onPhotoFileCaptured = { file ->
                        processPhotoUri(Uri.fromFile(file))
                    },
                    onVideoFileCaptured = { file ->
                        processVideoUri(Uri.fromFile(file))
                    },
                    onOpenSystemPhotoCamera = ::launchSystemPhotoCapture,
                    onOpenSystemVideoCamera = ::launchSystemVideoCapture,
                    onOpenGallery = {
                        galleryMediaLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onError = { msg -> onStatusMessage(msg) },
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun StoryCameraCaptureStage(
    captureMode: StoryCaptureMode,
    onModeChange: (StoryCaptureMode) -> Unit,
    useFrontCamera: Boolean,
    onToggleFrontCamera: () -> Unit,
    flashEnabled: Boolean,
    onToggleFlash: () -> Unit,
    hasCameraPermission: Boolean,
    hasAudioPermission: Boolean,
    isPreparingMedia: Boolean,
    onRequestPermissions: () -> Unit,
    onPhotoFileCaptured: (File) -> Unit,
    onVideoFileCaptured: (File) -> Unit,
    onOpenSystemPhotoCamera: () -> Unit,
    onOpenSystemVideoCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var cameraBindError by remember { mutableStateOf<String?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCaptureRef by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var isCapturingPhoto by remember { mutableStateOf(false) }

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            recordingSeconds = 0
            return@LaunchedEffect
        }
        recordingSeconds = 0
        while (isRecording && recordingSeconds < MAX_STORY_VIDEO_SECONDS) {
            delay(1_000L)
            recordingSeconds += 1
        }
        if (isRecording && recordingSeconds >= MAX_STORY_VIDEO_SECONDS) {
            activeRecording?.stop()
        }
    }

    LaunchedEffect(flashEnabled, boundCamera) {
        boundCamera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                runCatching { cam.cameraControl.enableTorch(flashEnabled && captureMode == StoryCaptureMode.VIDEO) }
            }
        }
        imageCaptureRef?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    DisposableEffect(lifecycleOwner, hasCameraPermission, useFrontCamera) {
        if (!hasCameraPermission) {
            return@DisposableEffect onDispose {}
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val desiredSelector = if (useFrontCamera && cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    null
                }

                if (desiredSelector == null) {
                    cameraBindError = "No live camera sensor detected. Use System Camera or Gallery below."
                    return@Runnable
                }

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imgCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()

                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.HD,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                    )
                    .build()
                val vidCapture = VideoCapture.withOutput(recorder)

                cameraProvider.unbindAll()
                val cam = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    desiredSelector,
                    preview,
                    imgCapture,
                    vidCapture
                )
                boundCamera = cam
                imageCaptureRef = imgCapture
                videoCaptureRef = vidCapture
                cameraBindError = null
            } catch (e: Exception) {
                cameraBindError = e.localizedMessage ?: "Live camera unavailable on this device."
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            activeRecording?.stop()
            activeRecording = null
            isRecording = false
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    fun triggerPhotoShutter() {
        val imgCapture = imageCaptureRef
        if (imgCapture == null || isCapturingPhoto) {
            onOpenSystemPhotoCamera()
            return
        }
        isCapturingPhoto = true
        val (photoFile, _) = VideoClipHelper.createCaptureStoryPhotoUri(context)
        val options = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imgCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturingPhoto = false
                    onPhotoFileCaptured(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturingPhoto = false
                    onError(exception.localizedMessage ?: "Could not capture photo")
                }
            }
        )
    }

    fun toggleVideoRecording() {
        if (isRecording) {
            activeRecording?.stop()
            return
        }
        val vidCapture = videoCaptureRef
        if (vidCapture == null) {
            onOpenSystemVideoCamera()
            return
        }
        val (videoFile, _) = VideoClipHelper.createCaptureStoryVideoUri(context)
        val fileOutputOptions = FileOutputOptions.Builder(videoFile).build()
        val pendingRecording = vidCapture.output.prepareRecording(context, fileOutputOptions).apply {
            if (hasAudioPermission &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                withAudioEnabled()
            }
        }
        isRecording = true
        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                isRecording = false
                activeRecording = null
                if (!event.hasError() || (videoFile.exists() && videoFile.length() > 0L)) {
                    onVideoFileCaptured(videoFile)
                } else {
                    onError("Video recording failed (${event.error}). Try System Camera or Gallery.")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Live CameraX Viewfinder or Permission/Unavailable Fallback
        if (hasCameraPermission && cameraBindError == null) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .testTag("story_camera_preview")
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (!hasCameraPermission) {
                        "Enable Camera for Stories"
                    } else {
                        "Live Viewfinder Unavailable"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (!hasCameraPermission) {
                        "Allow camera and mic access to snap photos or record 24-hour video clips directly."
                    } else {
                        cameraBindError ?: "Use your device camera app or pick a photo/video from your gallery."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                if (!hasCameraPermission) {
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = StoryBlue),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(48.dp)
                            .testTag("story_grant_camera_btn")
                    ) {
                        Text("Allow Camera Access", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                FilledTonalButton(
                    onClick = onOpenSystemPhotoCamera,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(48.dp)
                        .testTag("story_system_photo_btn")
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Take Photo (Camera App)")
                }
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onOpenSystemVideoCamera,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(48.dp)
                        .testTag("story_system_video_btn")
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Record Video (Camera App)")
                }
            }
        }

        // Top Bar Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .testTag("close_story_studio_btn")
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close story camera", tint = Color.White)
            }

            if (isRecording) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFD92D20)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Text(
                            text = "REC 00:${"%02d".format(recordingSeconds)} / 00:$MAX_STORY_VIDEO_SECONDS",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = "24h Story",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasCameraPermission && cameraBindError == null && !isRecording) {
                    IconButton(
                        onClick = onToggleFlash,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("story_flash_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = if (flashEnabled) "Flash on" else "Flash off",
                            tint = if (flashEnabled) Color(0xFFFFD60A) else Color.White
                        )
                    }
                }
                if (!isRecording) {
                    IconButton(
                        onClick = {
                            if (captureMode == StoryCaptureMode.VIDEO) {
                                onOpenSystemVideoCamera()
                            } else {
                                onOpenSystemPhotoCamera()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("story_native_camera_btn")
                    ) {
                        Icon(
                            imageVector = if (captureMode == StoryCaptureMode.VIDEO) Icons.Filled.Videocam else Icons.Filled.PhotoCamera,
                            contentDescription = "Open device camera app",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Loading Scrim while processing captured or picked media
        if (isPreparingMedia || isCapturingPhoto) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = CoralRose)
                    Text(
                        text = if (isCapturingPhoto) "Capturing photo..." else "Preparing story media...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Bottom Camera Controls (Mode switcher + Gallery + Shutter + Flip)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Photo / Video Mode Pill
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (captureMode == StoryCaptureMode.PHOTO) Color.White else Color.White.copy(alpha = 0.16f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = !isRecording) { onModeChange(StoryCaptureMode.PHOTO) }
                        .testTag("story_mode_photo_btn")
                ) {
                    Text(
                        text = "PHOTO",
                        color = if (captureMode == StoryCaptureMode.PHOTO) Color.Black else Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (captureMode == StoryCaptureMode.VIDEO) CoralRose else Color.White.copy(alpha = 0.16f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = !isRecording) { onModeChange(StoryCaptureMode.VIDEO) }
                        .testTag("story_mode_video_btn")
                ) {
                    Text(
                        text = "VIDEO",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Main Action Row: Gallery | Shutter | Flip Camera
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !isRecording) { onOpenGallery() }
                        .padding(6.dp)
                        .testTag("story_gallery_picker_btn")
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.65f)),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = "Pick from gallery",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Text("Gallery", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }

                // Center Shutter / Record Button
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .border(
                            width = 4.dp,
                            color = if (captureMode == StoryCaptureMode.VIDEO) CoralRose else Color.White,
                            shape = CircleShape
                        )
                        .clickable(enabled = !isPreparingMedia && !isCapturingPhoto) {
                            if (captureMode == StoryCaptureMode.PHOTO) {
                                triggerPhotoShutter()
                            } else {
                                toggleVideoRecording()
                            }
                        }
                        .padding(7.dp)
                        .testTag("story_camera_shutter_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        captureMode == StoryCaptureMode.VIDEO && isRecording -> {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CoralRose)
                            )
                        }
                        captureMode == StoryCaptureMode.VIDEO -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(CoralRose)
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }

                // Flip Camera Button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = hasCameraPermission && !isRecording) { onToggleFrontCamera() }
                        .padding(6.dp)
                        .testTag("story_flip_camera_btn")
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Cameraswitch,
                                contentDescription = "Switch camera",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Text("Flip", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StoryPreviewAndShareStage(
    myProfile: PublicProfile?,
    currentUserId: String,
    photoDataUrl: String?,
    preparedVideo: PreparedVideoUpload?,
    caption: String,
    onCaptionChange: (String) -> Unit,
    isPublishing: Boolean,
    errorMsg: String?,
    onRetake: () -> Unit,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    var userPaused by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var activePlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    LaunchedEffect(isMuted, activePlayer) {
        activePlayer?.let { mp ->
            runCatching {
                val vol = if (isMuted) 0f else 1f
                mp.setVolume(vol, vol)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Full-Screen Preview Media
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(preparedVideo?.localCachedFile?.absolutePath) {
                    detectTapGestures(
                        onTap = {
                            if (preparedVideo != null) {
                                userPaused = !userPaused
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (preparedVideo != null) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setOnErrorListener { _, _, _ -> true }
                            setVideoURI(Uri.fromFile(preparedVideo.localCachedFile))
                            setOnPreparedListener { mp ->
                                activePlayer = mp
                                mp.isLooping = true
                                val vol = if (isMuted) 0f else 1f
                                mp.setVolume(vol, vol)
                                if (!userPaused) start()
                            }
                        }
                    },
                    update = { vv ->
                        if (!userPaused && !vv.isPlaying) {
                            runCatching { vv.start() }
                        } else if (userPaused && vv.isPlaying) {
                            runCatching { vv.pause() }
                        }
                    },
                    onRelease = {
                        activePlayer = null
                        runCatching { it.stopPlayback() }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                )

                AnimatedVisibility(
                    visible = userPaused,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Resume preview",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }
            } else if (photoDataUrl != null) {
                val bitmap = remember(photoDataUrl) {
                    VerificationAnalyzer.decodeBase64DataUrl(photoDataUrl)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Captured story photo",
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
                    )
                }
            }
        }

        // Top Bar: Back/Retake, 24h Badge, Mute (for video), Close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onRetake,
                enabled = !isPublishing,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .testTag("story_retake_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retake",
                    tint = Color.White
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Text(
                    text = if (preparedVideo != null) {
                        "Video Story • ${VideoClipHelper.formatDurationLabel(preparedVideo.durationSec)}"
                    } else {
                        "Photo Story • 24h"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (preparedVideo != null) {
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    enabled = !isPublishing,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Discard story", tint = Color.White)
                }
            }
        }

        // Bottom Caption + Share Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                    )
                )
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!errorMsg.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(listOf("#vibes", "#today", "#kindred", "#moments", "#ootd")) { tag ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = !isPublishing) {
                                if (!caption.contains(tag)) {
                                    val updated = if (caption.isBlank()) tag else "$caption $tag"
                                    if (updated.length <= 160) onCaptionChange(updated)
                                }
                            }
                    ) {
                        Text(
                            text = tag,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = caption,
                onValueChange = onCaptionChange,
                enabled = !isPublishing,
                placeholder = {
                    Text(
                        "Add a caption to your 24h story...",
                        color = Color.White.copy(alpha = 0.65f)
                    )
                },
                maxLines = 2,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.White.copy(alpha = 0.12f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                    focusedBorderColor = StoryBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    cursorColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("story_caption_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRetake,
                    enabled = !isPublishing,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Retake", color = Color.White)
                }

                Button(
                    onClick = onShare,
                    enabled = !isPublishing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StoryBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("publish_story_button")
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sharing...", fontWeight = FontWeight.Bold)
                    } else {
                        ProfileAvatarImage(
                            photoDataUrl = myProfile?.photoDataUrl,
                            displayName = myProfile?.displayName ?: "You",
                            seed = myProfile?.avatarSeed ?: currentUserId,
                            modifier = Modifier.size(24.dp),
                            shape = CircleShape,
                            initialsFontSizeSp = 10
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share to Story", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
