package com.example.util

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class FacePoint(
    val x: Float,
    val y: Float
)

data class FaceDescriptor(
    val bounds: RectF,
    val leftEye: FacePoint,
    val rightEye: FacePoint,
    val noseBase: FacePoint? = null,
    val mouthLeft: FacePoint? = null,
    val mouthRight: FacePoint? = null,
    val mouthBottom: FacePoint? = null,
    val leftCheek: FacePoint? = null,
    val rightCheek: FacePoint? = null,
    val smilingProbability: Float? = null,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val headYawDeg: Float = 0f,
    val headRollDeg: Float = 0f,
    val confidence: Float = 0.85f
) {
    val eyeDistancePx: Float
        get() = hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y)

    val eyeMidpoint: FacePoint
        get() = FacePoint(
            x = (leftEye.x + rightEye.x) * 0.5f,
            y = (leftEye.y + rightEye.y) * 0.5f
        )

    val eyeRollRadians: Float
        get() = atan2(rightEye.y - leftEye.y, rightEye.x - leftEye.x)
}

data class FaceSimilarityBreakdown(
    val overallSimilarity: Float,
    val geometryScore: Float,
    val structureScore: Float,
    val toneScore: Float
)

/**
 * Pure facial geometry + eye-aligned crop perceptual comparison engine used to compare
 * a live camera selfie against the user's profile photos and block catfishing.
 */
object FaceComparisonEngine {

    const val MIN_BEST_PHOTO_SIMILARITY = 0.65f
    const val MIN_AVG_PHOTO_SIMILARITY = 0.58f
    const val MIN_PER_PHOTO_FLOOR = 0.45f

    private const val PATCH_SIZE = 32
    private const val GRAD_SIZE = 16

    fun compareFaceBitmapsAndDescriptors(
        selfieBitmap: Bitmap,
        selfieFace: FaceDescriptor,
        profileBitmap: Bitmap,
        profileFace: FaceDescriptor
    ): FaceSimilarityBreakdown {
        val geometryScore = computeGeometrySimilarity(selfieFace, profileFace)

        val selfiePatch = extractEyeAlignedFacePatch(selfieBitmap, selfieFace)
        val profilePatch = extractEyeAlignedFacePatch(profileBitmap, profileFace)

        val structureScore = if (selfiePatch != null && profilePatch != null) {
            val zncc = computeZeroMeanNcc(selfiePatch.luminance, profilePatch.luminance)
            val gradCosine = computeGradientSimilarity(selfiePatch.luminance, profilePatch.luminance)
            (0.55f * zncc + 0.45f * gradCosine).coerceIn(0f, 1f)
        } else {
            geometryScore
        }

        val toneScore = if (selfiePatch != null && profilePatch != null) {
            computeChromaticSimilarity(selfiePatch.rgHistogram, profilePatch.rgHistogram)
        } else {
            0.5f
        }

        val hasRichLandmarks = selfieFace.noseBase != null &&
            profileFace.noseBase != null &&
            selfieFace.mouthLeft != null &&
            profileFace.mouthLeft != null

        val overall = if (hasRichLandmarks) {
            (0.40f * geometryScore + 0.42f * structureScore + 0.18f * toneScore).coerceIn(0f, 1f)
        } else {
            (0.25f * geometryScore + 0.55f * structureScore + 0.20f * toneScore).coerceIn(0f, 1f)
        }

        return FaceSimilarityBreakdown(
            overallSimilarity = overall,
            geometryScore = geometryScore,
            structureScore = structureScore,
            toneScore = toneScore
        )
    }

    /**
     * Computes scale- and rotation-invariant similarity between normalized facial landmark vectors.
     */
    fun computeGeometrySimilarity(a: FaceDescriptor, b: FaceDescriptor): Float {
        val normA = normalizeLandmarks(a) ?: return 0.5f
        val normB = normalizeLandmarks(b) ?: return 0.5f

        val squaredDiffs = mutableListOf<Float>()

        // Compare bounding box proportions relative to inter-ocular spacing
        squaredDiffs += sq(normA.boundsWidthRatio - normB.boundsWidthRatio) * 0.45f
        squaredDiffs += sq(normA.boundsHeightRatio - normB.boundsHeightRatio) * 0.45f

        normA.nose?.let { na ->
            normB.nose?.let { nb ->
                squaredDiffs += sq(na.x - nb.x) + sq(na.y - nb.y)
            }
        }
        normA.mouthCenter?.let { ma ->
            normB.mouthCenter?.let { mb ->
                squaredDiffs += sq(ma.x - mb.x) + sq(ma.y - mb.y)
            }
        }
        if (normA.mouthWidthRatio != null && normB.mouthWidthRatio != null) {
            squaredDiffs += sq(normA.mouthWidthRatio - normB.mouthWidthRatio) * 1.4f
        }
        if (normA.cheekWidthRatio != null && normB.cheekWidthRatio != null) {
            squaredDiffs += sq(normA.cheekWidthRatio - normB.cheekWidthRatio) * 1.2f
        }
        if (normA.noseToMouthRatio != null && normB.noseToMouthRatio != null) {
            squaredDiffs += sq(normA.noseToMouthRatio - normB.noseToMouthRatio) * 1.5f
        }
        normA.mouthBottom?.let { ba ->
            normB.mouthBottom?.let { bb ->
                squaredDiffs += sq(ba.x - bb.x) + sq(ba.y - bb.y)
            }
        }

        if (squaredDiffs.isEmpty()) return 0.5f
        val meanSqError = squaredDiffs.average().toFloat()
        return exp(-meanSqError / 0.14f).coerceIn(0f, 1f)
    }

    fun computeSharpnessScore(bitmap: Bitmap, bounds: RectF): Float {
        val bw = bitmap.width
        val bh = bitmap.height
        val left = bounds.left.toInt().coerceIn(0, max(0, bw - 2))
        val top = bounds.top.toInt().coerceIn(0, max(0, bh - 2))
        val right = bounds.right.toInt().coerceIn(left + 2, bw)
        val bottom = bounds.bottom.toInt().coerceIn(top + 2, bh)
        val w = right - left
        val h = bottom - top
        if (w < 8 || h < 8) return 0f

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, left, top, w, h)

        val stepX = max(1, w / 28)
        val stepY = max(1, h / 28)
        var diffSum = 0.0
        var count = 0

        var y = 0
        while (y < h - stepY) {
            var x = 0
            while (x < w - stepX) {
                val c = luminanceAt(pixels[y * w + x])
                val rx = luminanceAt(pixels[y * w + (x + stepX)])
                val dy = luminanceAt(pixels[(y + stepY) * w + x])
                val grad = abs(c - rx) + abs(c - dy)
                diffSum += grad
                count++
                x += stepX
            }
            y += stepY
        }
        return if (count == 0) 0f else (diffSum / count).toFloat()
    }

    private data class NormalizedLandmarks(
        val boundsWidthRatio: Float,
        val boundsHeightRatio: Float,
        val nose: FacePoint?,
        val mouthCenter: FacePoint?,
        val mouthBottom: FacePoint?,
        val mouthWidthRatio: Float?,
        val cheekWidthRatio: Float?,
        val noseToMouthRatio: Float?
    )

    private fun normalizeLandmarks(face: FaceDescriptor): NormalizedLandmarks? {
        val d = face.eyeDistancePx
        if (d < 4f) return null
        val mid = face.eyeMidpoint
        val angle = face.eyeRollRadians
        val ux = cos(angle)
        val uy = sin(angle)
        val vx = -uy
        val vy = ux

        fun project(pt: FacePoint?): FacePoint? {
            if (pt == null) return null
            val rx = pt.x - mid.x
            val ry = pt.y - mid.y
            return FacePoint(
                x = (rx * ux + ry * uy) / d,
                y = (rx * vx + ry * vy) / d
            )
        }

        val nose = project(face.noseBase)
        val mouthLeft = project(face.mouthLeft)
        val mouthRight = project(face.mouthRight)
        val mouthBottom = project(face.mouthBottom)
        val leftCheek = project(face.leftCheek)
        val rightCheek = project(face.rightCheek)

        val mouthCenter = if (mouthLeft != null && mouthRight != null) {
            FacePoint((mouthLeft.x + mouthRight.x) * 0.5f, (mouthLeft.y + mouthRight.y) * 0.5f)
        } else null

        val mouthWidth = if (mouthLeft != null && mouthRight != null) {
            hypot(mouthRight.x - mouthLeft.x, mouthRight.y - mouthLeft.y)
        } else null

        val cheekWidth = if (leftCheek != null && rightCheek != null) {
            hypot(rightCheek.x - leftCheek.x, rightCheek.y - leftCheek.y)
        } else null

        val noseToMouth = if (nose != null && mouthCenter != null) {
            hypot(mouthCenter.x - nose.x, mouthCenter.y - nose.y)
        } else null

        return NormalizedLandmarks(
            boundsWidthRatio = face.bounds.width() / d,
            boundsHeightRatio = face.bounds.height() / d,
            nose = nose,
            mouthCenter = mouthCenter,
            mouthBottom = mouthBottom,
            mouthWidthRatio = mouthWidth,
            cheekWidthRatio = cheekWidth,
            noseToMouthRatio = noseToMouth
        )
    }

    private data class AlignedFacePatch(
        val luminance: FloatArray,
        val rgHistogram: FloatArray
    )

    /**
     * Extracts a 32x32 eye-aligned face patch via direct inverse affine sampling over [bitmap] pixels,
     * avoiding Canvas hardware dependencies and keeping per-photo comparison deterministic across JVM/device.
     */
    private fun extractEyeAlignedFacePatch(bitmap: Bitmap, face: FaceDescriptor): AlignedFacePatch? {
        val eyeDist = face.eyeDistancePx
        val bw = bitmap.width
        val bh = bitmap.height
        if (eyeDist < 6f || bw < 4 || bh < 4) return null

        val srcPixels = IntArray(bw * bh)
        bitmap.getPixels(srcPixels, 0, bw, 0, 0, bw, bh)

        val mid = face.eyeMidpoint
        val angle = face.eyeRollRadians
        // Unit eye axis (ux, uy) and downward orthogonal face axis (vx, vy)
        val ux = cos(angle)
        val uy = sin(angle)
        val vx = -uy
        val vy = ux

        val cropW = eyeDist * 2.0f
        val cropH = eyeDist * 2.4f
        val targetW = PATCH_SIZE
        val targetH = PATCH_SIZE

        val luminance = FloatArray(targetW * targetH)
        val histBins = 16 // 4x4 normalized r-g chromaticity grid
        val rgHist = FloatArray(histBins)
        var histTotal = 0f

        for (py in 0 until targetH) {
            val localY = (py.toFloat() / targetH - 0.34f) * cropH
            for (px in 0 until targetW) {
                val localX = (px.toFloat() / targetW - 0.5f) * cropW
                val srcX = (mid.x + localX * ux + localY * vx).roundToInt().coerceIn(0, bw - 1)
                val srcY = (mid.y + localX * uy + localY * vy).roundToInt().coerceIn(0, bh - 1)

                val argb = srcPixels[srcY * bw + srcX]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val idx = py * targetW + px
                luminance[idx] = 0.299f * r + 0.587f * g + 0.114f * b

                // Focus chromatic histogram on inner facial region (rows 8..25, cols 6..25)
                if (px in 6..25 && py in 8..25) {
                    val sum = (r + g + b).coerceAtLeast(1)
                    val rn = (r.toFloat() / sum).coerceIn(0f, 0.999f)
                    val gn = (g.toFloat() / sum).coerceIn(0f, 0.999f)
                    val binR = (rn * 4f).toInt().coerceIn(0, 3)
                    val binG = (gn * 4f).toInt().coerceIn(0, 3)
                    rgHist[binR * 4 + binG] += 1f
                    histTotal += 1f
                }
            }
        }

        if (histTotal > 0f) {
            for (i in rgHist.indices) {
                rgHist[i] /= histTotal
            }
        }
        return AlignedFacePatch(luminance = luminance, rgHistogram = rgHist)
    }

    internal fun computeZeroMeanNcc(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        val meanA = a.average().toFloat()
        val meanB = b.average().toFloat()
        var num = 0.0
        var denA = 0.0
        var denB = 0.0
        for (i in a.indices) {
            val da = (a[i] - meanA).toDouble()
            val db = (b[i] - meanB).toDouble()
            num += da * db
            denA += da * da
            denB += db * db
        }
        val denom = sqrt(denA * denB)
        if (denom < 1e-6) return 0f
        val ncc = (num / denom).toFloat().coerceIn(-1f, 1f)
        return ((ncc + 0.2f) / 1.2f).coerceIn(0f, 1f)
    }

    internal fun computeGradientSimilarity(a: FloatArray, b: FloatArray): Float {
        val ga = extractEdges(a)
        val gb = extractEdges(b)
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in ga.indices) {
            dot += ga[i] * gb[i]
            normA += ga[i] * ga[i]
            normB += gb[i] * gb[i]
        }
        val denom = sqrt(normA * normB)
        return if (denom < 1e-6) 0f else (dot / denom).toFloat().coerceIn(0f, 1f)
    }

    private fun extractEdges(lum: FloatArray): FloatArray {
        val out = FloatArray(GRAD_SIZE * GRAD_SIZE)
        for (gy in 0 until GRAD_SIZE) {
            val y = min(PATCH_SIZE - 2, gy * 2 + 1)
            for (gx in 0 until GRAD_SIZE) {
                val x = min(PATCH_SIZE - 2, gx * 2 + 1)
                val gxVal = lum[y * PATCH_SIZE + (x + 1)] - lum[y * PATCH_SIZE + (x - 1)]
                val gyVal = lum[(y + 1) * PATCH_SIZE + x] - lum[(y - 1) * PATCH_SIZE + x]
                out[gy * GRAD_SIZE + gx] = hypot(gxVal, gyVal)
            }
        }
        return out
    }

    internal fun computeChromaticSimilarity(histA: FloatArray, histB: FloatArray): Float {
        if (histA.size != histB.size) return 0.5f
        var bc = 0.0
        for (i in histA.indices) {
            bc += sqrt((histA[i] * histB[i]).toDouble())
        }
        return bc.toFloat().coerceIn(0f, 1f)
    }

    private fun luminanceAt(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun sq(v: Float): Float = v * v
}
