package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.media.FaceDetector
import android.net.Uri
import android.util.Base64
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.model.LivenessAnalysisResult
import com.example.model.ProfilePhotoComparison
import com.example.model.VerificationChallenge
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class DetectedFacesOutcome(
    val faces: List<FaceDescriptor>
)

object VerificationAnalyzer {

    private val mlKitDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.12f)
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * Analyzes a captured CameraX selfie for liveness & challenge compliance, then compares it
     * against all uploaded profile photos to prevent catfishing.
     */
    suspend fun analyzeSelfieAgainstProfilePhotos(
        imageProxy: ImageProxy,
        profilePhotoDataUrls: List<String>,
        challenge: VerificationChallenge,
        mirrorFrontCamera: Boolean = true
    ): LivenessAnalysisResult = withContext(Dispatchers.Default) {
        val rawBitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
        val oriented = orientBitmap(rawBitmap, rotation, mirrorHorizontal = mirrorFrontCamera)
        val scaledSelfie = resizeToMaxDim(oriented, maxDim = 640)
        analyzeSelfieBitmapAgainstProfilePhotos(
            selfieBitmap = scaledSelfie,
            profilePhotoDataUrls = profilePhotoDataUrls,
            challenge = challenge
        )
    }

    suspend fun analyzeSelfieBitmapAgainstProfilePhotos(
        selfieBitmap: Bitmap,
        profilePhotoDataUrls: List<String>,
        challenge: VerificationChallenge
    ): LivenessAnalysisResult = withContext(Dispatchers.Default) {
        val jpegStream = ByteArrayOutputStream()
        selfieBitmap.compress(Bitmap.CompressFormat.JPEG, 85, jpegStream)
        val jpegBytes = jpegStream.toByteArray()
        val sha256 = sha256Hex(jpegBytes)

        val previewDataUrl = buildSelfiePreviewDataUrl(selfieBitmap)

        val detection = detectFaces(selfieBitmap)
        val faces = detection.faces
        val count = faces.size

        if (count == 0) {
            return@withContext LivenessAnalysisResult(
                passed = false,
                livenessPassed = false,
                faceMatchPassed = false,
                faceCount = 0,
                confidence = 0f,
                eyesDistancePx = 0f,
                photoSha256 = sha256,
                comparedPhotoCount = profilePhotoDataUrls.size,
                capturedSelfiePreviewDataUrl = previewDataUrl,
                summary = "No face detected in selfie. Center your face in even lighting and try again."
            )
        }

        if (count > 1) {
            return@withContext LivenessAnalysisResult(
                passed = false,
                livenessPassed = false,
                faceMatchPassed = false,
                faceCount = count,
                confidence = faces.maxOf { it.confidence },
                eyesDistancePx = faces.maxOf { it.eyeDistancePx },
                photoSha256 = sha256,
                comparedPhotoCount = profilePhotoDataUrls.size,
                capturedSelfiePreviewDataUrl = previewDataUrl,
                summary = "Multiple faces detected ($count). Only one person can appear during verification."
            )
        }

        val selfieFace = faces.first()
        val sharpness = FaceComparisonEngine.computeSharpnessScore(selfieBitmap, selfieFace.bounds)
        val livenessFailure = evaluateLivenessAndChallenge(
            face = selfieFace,
            sharpness = sharpness,
            challenge = challenge
        )

        if (livenessFailure != null) {
            return@withContext LivenessAnalysisResult(
                passed = false,
                livenessPassed = false,
                faceMatchPassed = false,
                faceCount = 1,
                confidence = selfieFace.confidence,
                eyesDistancePx = selfieFace.eyeDistancePx,
                photoSha256 = sha256,
                comparedPhotoCount = profilePhotoDataUrls.size,
                capturedSelfiePreviewDataUrl = previewDataUrl,
                summary = livenessFailure
            )
        }

        val cleanPhotos = profilePhotoDataUrls.filter { it.isNotBlank() }
        if (cleanPhotos.isEmpty()) {
            return@withContext LivenessAnalysisResult(
                passed = false,
                livenessPassed = true,
                faceMatchPassed = false,
                faceCount = 1,
                confidence = selfieFace.confidence,
                eyesDistancePx = selfieFace.eyeDistancePx,
                photoSha256 = sha256,
                comparedPhotoCount = 0,
                capturedSelfiePreviewDataUrl = previewDataUrl,
                summary = "Liveness passed, but you have no profile photos to compare against. Upload a profile photo first."
            )
        }

        val comparisons = mutableListOf<ProfilePhotoComparison>()
        for ((index, dataUrl) in cleanPhotos.withIndex()) {
            val profileBitmap = decodeBitmapFromDataUrl(dataUrl)
            if (profileBitmap == null) {
                comparisons += ProfilePhotoComparison(
                    photoIndex = index,
                    photoDataUrl = dataUrl,
                    faceDetected = false,
                    similarityScore = 0f,
                    geometryScore = 0f,
                    structureScore = 0f,
                    toneScore = 0f,
                    matched = false,
                    reason = "Could not decode photo ${index + 1}"
                )
                continue
            }

            val profileFaces = detectFaces(profileBitmap).faces
            val primaryProfileFace = profileFaces.maxByOrNull { it.bounds.width() * it.bounds.height() }
            if (primaryProfileFace == null) {
                comparisons += ProfilePhotoComparison(
                    photoIndex = index,
                    photoDataUrl = dataUrl,
                    faceDetected = false,
                    similarityScore = 0f,
                    geometryScore = 0f,
                    structureScore = 0f,
                    toneScore = 0f,
                    matched = false,
                    reason = "No clear face detected in photo ${index + 1}"
                )
                continue
            }

            val breakdown = FaceComparisonEngine.compareFaceBitmapsAndDescriptors(
                selfieBitmap = selfieBitmap,
                selfieFace = selfieFace,
                profileBitmap = profileBitmap,
                profileFace = primaryProfileFace
            )
            val simPct = (breakdown.overallSimilarity * 100).roundToInt()
            val matched = breakdown.overallSimilarity >= FaceComparisonEngine.MIN_BEST_PHOTO_SIMILARITY
            val reason = when {
                matched -> "Strong facial match ($simPct%)"
                breakdown.overallSimilarity >= FaceComparisonEngine.MIN_PER_PHOTO_FLOOR ->
                    "Borderline match ($simPct%) — try closer angle or lighting"
                else -> "Face mismatch ($simPct%) — does not match live selfie"
            }

            comparisons += ProfilePhotoComparison(
                photoIndex = index,
                photoDataUrl = dataUrl,
                faceDetected = true,
                similarityScore = breakdown.overallSimilarity,
                geometryScore = breakdown.geometryScore,
                structureScore = breakdown.structureScore,
                toneScore = breakdown.toneScore,
                matched = matched,
                reason = reason
            )
        }

        val faceMatches = comparisons.filter { it.faceDetected }
        if (faceMatches.isEmpty()) {
            return@withContext LivenessAnalysisResult(
                passed = false,
                livenessPassed = true,
                faceMatchPassed = false,
                faceCount = 1,
                confidence = selfieFace.confidence,
                eyesDistancePx = selfieFace.eyeDistancePx,
                photoSha256 = sha256,
                comparedPhotoCount = comparisons.size,
                bestSimilarityScore = 0f,
                averageSimilarityScore = 0f,
                perPhotoComparisons = comparisons,
                capturedSelfiePreviewDataUrl = previewDataUrl,
                summary = "None of your uploaded profile photos contain a clear, front-facing face to compare against."
            )
        }

        val bestSim = faceMatches.maxOf { it.similarityScore }
        val avgSim = faceMatches.map { it.similarityScore }.average().toFloat()
        val minSim = faceMatches.minOf { it.similarityScore }

        val matchPassed = bestSim >= FaceComparisonEngine.MIN_BEST_PHOTO_SIMILARITY &&
            avgSim >= FaceComparisonEngine.MIN_AVG_PHOTO_SIMILARITY &&
            minSim >= FaceComparisonEngine.MIN_PER_PHOTO_FLOOR

        val bestPct = (bestSim * 100).roundToInt()
        val avgPct = (avgSim * 100).roundToInt()
        val minPct = (minSim * 100).roundToInt()

        val summary = when {
            matchPassed ->
                "Selfie verified against ${faceMatches.size} profile ${if (faceMatches.size == 1) "photo" else "photos"} ($bestPct% peak match)."
            minSim < FaceComparisonEngine.MIN_PER_PHOTO_FLOOR ->
                "Potential mismatch detected: one profile photo only scored $minPct% similarity against your live selfie. Replace mismatched photos or retake in clear lighting."
            else ->
                "Selfie similarity too low ($bestPct% peak, $avgPct% avg). Ensure your face is well-lit and matches your profile photos."
        }

        LivenessAnalysisResult(
            passed = matchPassed,
            livenessPassed = true,
            faceMatchPassed = matchPassed,
            faceCount = 1,
            confidence = selfieFace.confidence,
            eyesDistancePx = selfieFace.eyeDistancePx,
            photoSha256 = sha256,
            comparedPhotoCount = comparisons.size,
            bestSimilarityScore = bestSim,
            averageSimilarityScore = avgSim,
            perPhotoComparisons = comparisons,
            capturedSelfiePreviewDataUrl = previewDataUrl,
            summary = summary
        )
    }

    private fun evaluateLivenessAndChallenge(
        face: FaceDescriptor,
        sharpness: Float,
        challenge: VerificationChallenge
    ): String? {
        if (face.confidence < 0.45f) {
            return "Face confidence too low (${(face.confidence * 100).toInt()}%). Hold steady in brighter light."
        }
        if (face.eyeDistancePx < 18.0f) {
            return "Move closer to the front camera so both eyes and facial features are clearly framed."
        }
        if (sharpness < 3.0f) {
            return "Image is too blurry or dark. Clean the lens and face even lighting."
        }
        val leftOpen = face.leftEyeOpenProbability
        val rightOpen = face.rightEyeOpenProbability
        if ((leftOpen != null && leftOpen < 0.35f) || (rightOpen != null && rightOpen < 0.35f)) {
            return "Keep both eyes open clearly while capturing your verification selfie."
        }

        return when (challenge.code) {
            "SMILE_CENTER" -> {
                val smile = face.smilingProbability
                when {
                    abs(face.headYawDeg) > 22f -> "Face straight toward the camera for the smile check."
                    smile != null && smile < 0.40f -> "Give a natural smile to complete the ${challenge.code} liveness prompt."
                    else -> null
                }
            }
            "EYES_LEVEL" -> {
                if (abs(face.headYawDeg) > 18f || abs(face.headRollDeg) > 16f) {
                    "Hold your head level and look straight into the camera."
                } else null
            }
            "SLIGHT_TILT" -> {
                if (abs(face.headYawDeg) > 32f) {
                    "Keep your face within the circular frame during capture."
                } else null
            }
            else -> null
        }
    }

    private suspend fun detectFaces(bitmap: Bitmap): DetectedFacesOutcome {
        // Primary path: Google ML Kit Face Detection (accurate landmarks, head angles, eye/smile probabilities)
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val mlFaces = mlKitDetector.process(image).await()
            val descriptors = mlFaces.mapNotNull { face -> mapMlKitFace(face) }
            return DetectedFacesOutcome(descriptors)
        } catch (_: Throwable) {
            // Fallback path for environments where ML Kit native libs are unavailable (e.g. JVM Robolectric)
        }

        val evenW = (if (bitmap.width % 2 == 0) bitmap.width else bitmap.width - 1).coerceAtLeast(16)
        val safeH = bitmap.height.coerceAtLeast(16)
        val rgb565 = Bitmap.createBitmap(evenW, safeH, Bitmap.Config.RGB_565)
        Canvas(rgb565).drawBitmap(bitmap, 0f, 0f, null)

        val detector = FaceDetector(evenW, safeH, 3)
        val rawFaces = arrayOfNulls<FaceDetector.Face>(3)
        val found = detector.findFaces(rgb565, rawFaces)
        val mapped = rawFaces.take(found).filterNotNull().map { f ->
            val mid = PointF()
            f.getMidPoint(mid)
            val eyeDist = f.eyesDistance().coerceAtLeast(8f)
            val halfSpan = eyeDist * 0.5f
            val bounds = RectF(
                (mid.x - eyeDist * 1.1f).coerceAtLeast(0f),
                (mid.y - eyeDist * 0.85f).coerceAtLeast(0f),
                (mid.x + eyeDist * 1.1f).coerceAtMost(evenW.toFloat()),
                (mid.y + eyeDist * 1.65f).coerceAtMost(safeH.toFloat())
            )
            FaceDescriptor(
                bounds = bounds,
                leftEye = FacePoint(mid.x - halfSpan, mid.y),
                rightEye = FacePoint(mid.x + halfSpan, mid.y),
                confidence = f.confidence().coerceIn(0f, 1f)
            )
        }
        return DetectedFacesOutcome(mapped)
    }

    private fun mapMlKitFace(face: Face): FaceDescriptor? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val box = RectF(face.boundingBox)
        if (box.width() < 12f || box.height() < 12f) return null

        // If ML Kit missed one eye landmark at an angle, estimate eye positions inside the face bounding box
        val resolvedLeftEye = leftEye?.let { FacePoint(it.x, it.y) }
            ?: FacePoint(box.left + box.width() * 0.32f, box.top + box.height() * 0.36f)
        val resolvedRightEye = rightEye?.let { FacePoint(it.x, it.y) }
            ?: FacePoint(box.left + box.width() * 0.68f, box.top + box.height() * 0.36f)

        val orderedLeft: FacePoint
        val orderedRight: FacePoint
        if (resolvedLeftEye.x <= resolvedRightEye.x) {
            orderedLeft = resolvedLeftEye
            orderedRight = resolvedRightEye
        } else {
            orderedLeft = resolvedRightEye
            orderedRight = resolvedLeftEye
        }

        fun landmarkPoint(type: Int): FacePoint? =
            face.getLandmark(type)?.position?.let { FacePoint(it.x, it.y) }

        return FaceDescriptor(
            bounds = box,
            leftEye = orderedLeft,
            rightEye = orderedRight,
            noseBase = landmarkPoint(FaceLandmark.NOSE_BASE),
            mouthLeft = landmarkPoint(FaceLandmark.MOUTH_LEFT),
            mouthRight = landmarkPoint(FaceLandmark.MOUTH_RIGHT),
            mouthBottom = landmarkPoint(FaceLandmark.MOUTH_BOTTOM),
            leftCheek = landmarkPoint(FaceLandmark.LEFT_CHEEK),
            rightCheek = landmarkPoint(FaceLandmark.RIGHT_CHEEK),
            smilingProbability = face.smilingProbability?.takeIf { it >= 0f },
            leftEyeOpenProbability = face.leftEyeOpenProbability?.takeIf { it >= 0f },
            rightEyeOpenProbability = face.rightEyeOpenProbability?.takeIf { it >= 0f },
            headYawDeg = face.headEulerAngleY,
            headRollDeg = face.headEulerAngleZ,
            confidence = 0.92f
        )
    }

    suspend fun compressUriToDataUrl(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val original = inputStream.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
                val resized = resizeToMaxDim(original, maxDim = 360)

                var quality = 78
                var bytes: ByteArray
                do {
                    val out = ByteArrayOutputStream()
                    resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    bytes = out.toByteArray()
                    quality -= 8
                } while (bytes.size > 85_000 && quality >= 35)

                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:image/jpeg;base64,$encoded"
            } catch (_: Exception) {
                null
            }
        }

    fun decodeBase64DataUrl(dataUrl: String?): ImageBitmap? {
        return decodeBitmapFromDataUrl(dataUrl)?.asImageBitmap()
    }

    fun decodeBitmapFromDataUrl(dataUrl: String?): Bitmap? {
        if (dataUrl.isNullOrBlank()) return null
        return try {
            val base64Part = dataUrl.substringAfter("base64,", dataUrl)
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildSelfiePreviewDataUrl(bitmap: Bitmap): String {
        val thumb = resizeToMaxDim(bitmap, maxDim = 240)
        val out = ByteArrayOutputStream()
        thumb.compress(Bitmap.CompressFormat.JPEG, 76, out)
        val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }

    private fun orientBitmap(bitmap: Bitmap, rotationDeg: Float, mirrorHorizontal: Boolean): Bitmap {
        if (rotationDeg == 0f && !mirrorHorizontal) return bitmap
        val matrix = Matrix().apply {
            if (rotationDeg != 0f) postRotate(rotationDeg)
            if (mirrorHorizontal) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun resizeToMaxDim(bitmap: Bitmap, maxDim: Int): Bitmap {
        val scale = min(1f, maxDim.toFloat() / max(bitmap.width, bitmap.height).toFloat())
        val targetW = ((bitmap.width * scale).roundToInt()).let { if (it % 2 == 0) it else it - 1 }.coerceAtLeast(160)
        val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(160)
        return if (bitmap.width == targetW && bitmap.height == targetH) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
