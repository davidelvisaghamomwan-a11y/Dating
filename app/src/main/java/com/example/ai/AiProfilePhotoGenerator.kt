package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val TAG = "AiProfilePhotoService"
private const val IMAGE_MODEL_NAME = "gemini-nano-banana-2.1"
private const val IMAGE_GEN_TIMEOUT_MS = 60_000L

data class AiPhotoStylePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val promptGuidance: String
)

object AiPhotoStylePresets {
    val PRESETS = listOf(
        AiPhotoStylePreset(
            id = "golden_hour_rooftop",
            title = "Golden Hour Rooftop",
            subtitle = "Warm sunset bokeh & natural smile",
            promptGuidance = "Candid golden hour portrait on a stylish city rooftop terrace at sunset, warm amber sunlight, shallow depth of field, relaxed natural smile, editorial dating profile photography, 85mm portrait lens, crisp focus."
        ),
        AiPhotoStylePreset(
            id = "artisan_cafe",
            title = "Warm Cafe Candid",
            subtitle = "Soft window light & relaxed vibe",
            promptGuidance = "Natural lifestyle portrait seated in a sunlit modern coffee bar with warm wood tones and soft window light, approachable confident expression, smart casual attire, 50mm f/1.8 natural photography."
        ),
        AiPhotoStylePreset(
            id = "studio_editorial",
            title = "Editorial Headshot",
            subtitle = "Clean studio lighting & sharp detail",
            promptGuidance = "Refined editorial portrait in soft flattering key light against a subtle neutral textured backdrop, crisp natural skin texture, authentic confident expression, modern magazine portrait look."
        ),
        AiPhotoStylePreset(
            id = "coastal_trail",
            title = "Outdoor Natural Light",
            subtitle = "Fresh air, daylight & vibrant clarity",
            promptGuidance = "Bright natural outdoor portrait along a scenic coastal overlook during late afternoon, soft sea breeze, vibrant natural colors, genuine friendly expression, crisp DSLR portrait photography."
        ),
        AiPhotoStylePreset(
            id = "gallery_evening",
            title = "Gallery Evening",
            subtitle = "Ambient architectural warmth",
            promptGuidance = "Stylish evening portrait inside a softly lit contemporary art gallery with warm architectural lighting, polished smart-evening outfit, poised relaxed posture, cinematic depth."
        )
    )
}

sealed interface AiPhotoGenUiState {
    data object Idle : AiPhotoGenUiState
    data object Loading : AiPhotoGenUiState
    data class Success(
        val bitmap: Bitmap,
        val dataUrl: String,
        val styleTitle: String,
        val usedReferencePhoto: Boolean
    ) : AiPhotoGenUiState
    data class Error(val message: String) : AiPhotoGenUiState
}

/**
 * Generates or enhances dating profile portraits using Firebase AI Logic (`Firebase.ai`)
 * backed by Gemini image generation (`gemini-nano-banana-2.1`).
 */
class AiProfilePhotoService(
    private val modelProvider: () -> GenerativeModel = {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
        }
        Firebase.ai.generativeModel(
            modelName = IMAGE_MODEL_NAME,
            generationConfig = config
        )
    }
) {
    private val imageModel: GenerativeModel by lazy { modelProvider() }

    suspend fun generateProfilePortrait(
        preset: AiPhotoStylePreset,
        customDetails: String,
        subjectGender: String?,
        referenceBitmap: Bitmap? = null
    ): Result<Bitmap> {
        val prompt = buildPortraitPrompt(
            preset = preset,
            customDetails = customDetails,
            subjectGender = subjectGender,
            hasReference = referenceBitmap != null
        )

        return try {
            val bitmap = withTimeout(IMAGE_GEN_TIMEOUT_MS) {
                val response = if (referenceBitmap != null) {
                    imageModel.generateContent(
                        content {
                            image(referenceBitmap)
                            text(prompt)
                        }
                    )
                } else {
                    imageModel.generateContent(prompt)
                }

                response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()
                    ?.image
            }

            if (bitmap != null) {
                Result.success(bitmap)
            } else {
                Result.failure(
                    IllegalStateException("Gemini did not return an image for this prompt. Try adjusting your description.")
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI profile photo generation timed out", e)
            Result.failure(
                IllegalStateException("Photo generation timed out after 60 seconds. Please try again.")
            )
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "AI profile photo generation quota exceeded", e)
            Result.failure(
                IllegalStateException("Image generation quota reached or Blaze billing plan required in Firebase. Please check your project quota and try again.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI profile photo generation failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: "Failed to generate profile photo.")
            )
        }
    }

    companion object {
        fun buildPortraitPrompt(
            preset: AiPhotoStylePreset,
            customDetails: String,
            subjectGender: String?,
            hasReference: Boolean
        ): String {
            val cleanCustom = customDetails.trim()
            val subjectHint = when (subjectGender) {
                "Woman" -> "adult woman"
                "Man" -> "adult man"
                "Non-binary" -> "stylish adult person"
                else -> "photogenic adult"
            }

            return buildString {
                if (hasReference) {
                    append("Enhance and restyle the person in the reference photo while strictly preserving their exact facial identity, features, and likeness. ")
                } else {
                    append("Create a realistic, high-resolution, single-person dating profile portrait photograph of a $subjectHint. ")
                }
                append(preset.promptGuidance)
                if (cleanCustom.isNotBlank()) {
                    append(" Additional styling notes: ")
                    append(cleanCustom)
                    append(".")
                }
                append(" Realistic photography only, no text overlays, no watermarks, no collages, single subject facing camera naturally.")
            }
        }
    }
}
