package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.model.LivenessAnalysisResult
import com.example.model.ProfilePhotoComparison
import com.example.model.PublicProfile
import com.example.model.VerificationChallenge
import com.example.model.VerificationRecord
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.VerifiedShieldBadge
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.ErrorCrimson
import com.example.util.LocationAndGeoHelper
import com.example.util.VerificationAnalyzer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun VerificationScreen(
    myProfile: PublicProfile?,
    isEmailVerified: Boolean,
    verifications: List<VerificationRecord>,
    activeChallenge: VerificationChallenge,
    isBusy: Boolean,
    onCycleChallenge: () -> Unit,
    onSubmitVerification: (VerificationChallenge, LivenessAnalysisResult, Boolean) -> Unit,
    onNavigateToProfile: () -> Unit,
    initialAnalysis: LivenessAnalysisResult? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraOpen by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var lastAnalysis by remember(initialAnalysis) { mutableStateOf(initialAnalysis) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val profilePhotos = remember(myProfile?.photoDataUrl, myProfile?.profilePhotos) {
        myProfile?.allProfilePhotos().orEmpty()
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            cameraError = null
            cameraOpen = true
        } else {
            cameraError = "Camera permission is required to complete live selfie comparison & liveness verification."
        }
    }

    val locationGranted = remember(context) {
        LocationAndGeoHelper.hasLocationPermission(context)
    }
    val trustScore = (myProfile?.verificationScore ?: if (isEmailVerified) 30L else 15L).toInt()
    val isVerified = myProfile?.isProfileVerified == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.verify_title),
                style = MaterialTheme.typography.displaySmall
            )

            // Trust Score Hero Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_verification_shield),
                        contentDescription = stringResource(R.string.cd_verify_shield),
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        VerifiedShieldBadge(
                            isVerified = isVerified,
                            score = trustScore
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isVerified) "Identity & Photo Match Verified" else "Verify Against Your Photos",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "We match a live front-camera selfie against your profile photos on-device to prevent catfishing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (trustScore / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50)),
                            color = if (isVerified) EmeraldMint else ChampagneGold,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Composite Trust Score: $trustScore / 100",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Three Security Verification Pillars
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Anti-Catfish Security Pillars",
                        style = MaterialTheme.typography.titleMedium
                    )

                    VerificationPillarRow(
                        title = "Google Identity Binding",
                        subtitle = if (isEmailVerified) "Signed in with verified Google credential" else "Google account authenticated",
                        completed = true
                    )

                    VerificationPillarRow(
                        title = "Live Selfie vs. Profile Photo Match",
                        subtitle = if (isVerified) {
                            "Matched live camera capture against ${profilePhotos.size.coerceAtLeast(1)} profile photo(s)"
                        } else {
                            "Compares facial landmarks & eye-aligned features against every uploaded photo"
                        },
                        completed = isVerified
                    )

                    VerificationPillarRow(
                        title = "Location Proximity Attestation",
                        subtitle = if (locationGranted) {
                            "GPS permission active • coarse coordinates rounded"
                        } else {
                            "Grant location in Explore or Profile for +15 trust points"
                        },
                        completed = locationGranted
                    )
                }
            }

            if (myProfile == null) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Create Your Profile First",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Save your basic profile details with at least one clear face photo before running verification.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onNavigateToProfile,
                            modifier = Modifier.testTag("create_profile_cta_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
                        ) {
                            Text("Go to Profile Setup")
                        }
                    }
                }
            } else if (profilePhotos.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.testTag("missing_photos_blocker_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddAPhoto,
                                contentDescription = null,
                                tint = CoralRose
                            )
                            Text(
                                text = "Upload a Profile Photo First",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = "To prevent catfishing, live selfie verification compares your camera capture against your uploaded profile photos. Add at least one clear photo of your face in Profile.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Button(
                            onClick = onNavigateToProfile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("upload_photos_cta_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CoralRose, contentColor = Color.White)
                        ) {
                            Text("Go to Profile Photos", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Live CameraX Liveness & Photo Comparison Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = EmeraldMint
                                )
                                Text(
                                    text = "Liveness & Photo Match",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            TextButton(
                                onClick = onCycleChallenge,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Prompt")
                            }
                        }

                        // Reference profile photos strip
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Comparing against ${profilePhotos.size} uploaded profile ${if (profilePhotos.size == 1) "photo" else "photos"}:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                profilePhotos.forEachIndexed { idx, dataUrl ->
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                                RoundedCornerShape(12.dp)
                                            )
                                    ) {
                                        ProfileAvatarImage(
                                            photoDataUrl = dataUrl,
                                            displayName = myProfile.displayName,
                                            seed = myProfile.avatarSeed,
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(12.dp),
                                            initialsFontSizeSp = 18
                                        )
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.65f),
                                            shape = RoundedCornerShape(topStart = 6.dp),
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            Text(
                                                text = "#${idx + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = activeChallenge.prompt,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ChampagneGold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeChallenge.hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!cameraError.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ErrorCrimson.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, ErrorCrimson.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = cameraError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorCrimson,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        if (cameraOpen && hasCameraPermission) {
                            LiveCameraVerificationBox(
                                isBusy = isAnalyzing || isBusy,
                                onCaptureFrame = { imageCapture, usedFrontCamera ->
                                    isAnalyzing = true
                                    cameraError = null
                                    imageCapture.takePicture(
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(image: ImageProxy) {
                                                scope.launch {
                                                    try {
                                                        val analysis = VerificationAnalyzer
                                                            .analyzeSelfieAgainstProfilePhotos(
                                                                imageProxy = image,
                                                                profilePhotoDataUrls = profilePhotos,
                                                                challenge = activeChallenge,
                                                                mirrorFrontCamera = usedFrontCamera
                                                            )
                                                        lastAnalysis = analysis
                                                        cameraOpen = false
                                                        if (analysis.passed) {
                                                            onSubmitVerification(
                                                                activeChallenge,
                                                                analysis,
                                                                LocationAndGeoHelper.hasLocationPermission(context)
                                                            )
                                                        } else {
                                                            cameraError = analysis.summary
                                                        }
                                                    } finally {
                                                        image.close()
                                                        isAnalyzing = false
                                                    }
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                isAnalyzing = false
                                                cameraError = exception.localizedMessage
                                                    ?: "Camera capture failed. Please retry."
                                            }
                                        }
                                    )
                                },
                                onCloseCamera = { cameraOpen = false }
                            )
                        } else {
                            Button(
                                onClick = {
                                    if (hasCameraPermission) {
                                        cameraError = null
                                        cameraOpen = true
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("start_camera_verification_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldMint, contentColor = Color(0xFF042220))
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        lastAnalysis != null && lastAnalysis?.passed == false -> "Retake Verification Selfie"
                                        isVerified -> "Re-Verify With Live Camera"
                                        else -> stringResource(R.string.btn_start_verification)
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        lastAnalysis?.let { result ->
                            SelfieComparisonBreakdownCard(
                                result = result,
                                displayName = myProfile.displayName,
                                avatarSeed = myProfile.avatarSeed,
                                onUpdateProfilePhotos = onNavigateToProfile
                            )
                        }
                    }
                }
            }

            // Immutable Firestore Verification Audit History
            if (verifications.isNotEmpty()) {
                Text(
                    text = "Verification Audit History",
                    style = MaterialTheme.typography.titleMedium
                )
                val formatter = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }
                verifications.forEach { rec ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = EmeraldMint
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${rec.challengeCode} • ${(rec.photoSimilarityScore * 100).roundToInt()}% photo match (${rec.comparedPhotoCount} photo${if (rec.comparedPhotoCount == 1L) "" else "s"})",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Liveness ${(rec.faceConfidence * 100).roundToInt()}% • Digest: ${rec.photoSha256.take(16)}…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatter.format(rec.createdAt?.toDate() ?: Date()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SelfieComparisonBreakdownCard(
    result: LivenessAnalysisResult,
    displayName: String,
    avatarSeed: String,
    onUpdateProfilePhotos: () -> Unit
) {
    val accentColor = if (result.passed) EmeraldMint else ErrorCrimson
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("verification_comparison_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (result.passed) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Selfie vs. Profile Photo Match",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = result.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Side-by-side Live Selfie preview + aggregate metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!result.capturedSelfiePreviewDataUrl.isNullOrBlank()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ProfileAvatarImage(
                            photoDataUrl = result.capturedSelfiePreviewDataUrl,
                            displayName = displayName,
                            seed = avatarSeed,
                            modifier = Modifier
                                .size(64.dp)
                                .border(2.dp, accentColor, RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            initialsFontSizeSp = 22
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Live Selfie",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.FaceRetouchingNatural,
                                contentDescription = null,
                                tint = accentColor
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Liveness: ${if (result.livenessPassed) "Passed" else "Failed"} (${(result.confidence * 100).roundToInt()}%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (result.livenessPassed) EmeraldMint else ErrorCrimson
                    )
                    Text(
                        text = "Peak Match: ${(result.bestSimilarityScore * 100).roundToInt()}% • Avg: ${(result.averageSimilarityScore * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "SHA-256: ${result.photoSha256.take(16)}…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (result.perPhotoComparisons.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.perPhotoComparisons.forEach { item ->
                        PerPhotoComparisonRow(
                            item = item,
                            displayName = displayName,
                            avatarSeed = avatarSeed
                        )
                    }
                }
            }

            if (!result.passed && result.livenessPassed && !result.faceMatchPassed) {
                OutlinedButton(
                    onClick = onUpdateProfilePhotos,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("fix_mismatched_photos_button")
                ) {
                    Text("Update Profile Photos")
                }
            }
        }
    }
}

@Composable
private fun PerPhotoComparisonRow(
    item: ProfilePhotoComparison,
    displayName: String,
    avatarSeed: String
) {
    val rowColor = when {
        !item.faceDetected -> ChampagneGold
        item.matched -> EmeraldMint
        else -> ErrorCrimson
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileAvatarImage(
                photoDataUrl = item.photoDataUrl,
                displayName = displayName,
                seed = avatarSeed,
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(10.dp),
                initialsFontSizeSp = 16
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Profile Photo #${item.photoIndex + 1}",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = item.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.faceDetected) {
                    Text(
                        text = "Geom ${(item.geometryScore * 100).roundToInt()}% • Structure ${(item.structureScore * 100).roundToInt()}% • Tone ${(item.toneScore * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = rowColor.copy(alpha = 0.16f)
            ) {
                Text(
                    text = if (item.faceDetected) "${(item.similarityScore * 100).roundToInt()}%" else "No Face",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = rowColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun VerificationPillarRow(
    title: String,
    subtitle: String,
    completed: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (completed) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (completed) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LiveCameraVerificationBox(
    isBusy: Boolean,
    onCaptureFrame: (ImageCapture, Boolean) -> Unit,
    onCloseCamera: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var bindError by remember { mutableStateOf<String?>(null) }
    var usedFrontCamera by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> {
                        usedFrontCamera = true
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> {
                        usedFrontCamera = false
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    else -> null
                }
                if (selector == null) {
                    bindError = "No hardware or emulated camera available on this device."
                    return@Runnable
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            } catch (e: Exception) {
                bindError = e.localizedMessage ?: "Failed to bind camera."
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!bindError.isNullOrBlank()) {
            Text(
                text = bindError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .border(3.dp, EmeraldMint, CircleShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCloseCamera,
                enabled = !isBusy,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = { onCaptureFrame(imageCapture, usedFrontCamera) },
                enabled = !isBusy && bindError == null,
                modifier = Modifier
                    .weight(1.4f)
                    .height(50.dp)
                    .testTag("capture_verify_button"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldMint, contentColor = Color(0xFF042220))
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF042220),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Capture & Compare", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
