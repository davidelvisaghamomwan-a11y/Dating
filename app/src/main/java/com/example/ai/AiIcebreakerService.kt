package com.example.ai

import android.util.Log
import com.example.model.IcebreakerSuggestion
import com.example.model.PublicProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiIcebreakerService"
private const val ICEBREAKER_MODEL = "gemini-3.8-flash"
private const val ICEBREAKER_TIMEOUT_MS = 30_000L

sealed interface IcebreakerAiUiState {
    data object Idle : IcebreakerAiUiState
    data object Loading : IcebreakerAiUiState
    data class Success(val suggestions: List<IcebreakerSuggestion>) : IcebreakerAiUiState
    data class Error(val message: String) : IcebreakerAiUiState
}

/**
 * Generates tailored conversation openers from both users' profiles using Firebase AI Logic
 * (`Firebase.ai` with `gemini-3.8-flash` and strict JSON `responseSchema`), plus instant
 * local profile-derived openers when offline or before AI refresh.
 */
class AiIcebreakerService(
    private val modelProvider: () -> GenerativeModel = {
        val suggestionItem = Schema.obj(
            properties = mapOf(
                "prompt" to Schema.string(description = "A concise, warm, respectful social DM opener message under 140 characters"),
                "tone" to Schema.string(description = "One-word or two-word tone label like Friendly, Curious, Shared Vibe, Hangout Idea"),
                "reason" to Schema.string(description = "Brief explanation of what profile detail inspired this opener")
            )
        )
        val rootSchema = Schema.obj(
            properties = mapOf(
                "openers" to Schema.array(
                    items = suggestionItem,
                    description = "Four distinct conversation openers tailored to the creator or friend profile"
                )
            )
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = rootSchema
        }
        Firebase.ai.generativeModel(
            modelName = ICEBREAKER_MODEL,
            generationConfig = config
        )
    }
) {
    private val model: GenerativeModel by lazy { modelProvider() }

    suspend fun generateAiOpeners(
        myProfile: PublicProfile?,
        partnerProfile: PublicProfile?,
        tonePreference: String = "Balanced"
    ): Result<List<IcebreakerSuggestion>> {
        val partnerName = partnerProfile?.displayName?.ifBlank { "them" } ?: "them"
        val partnerBio = partnerProfile?.bio?.ifBlank { "Enjoys thoughtful conversation and exploring the city." }
            ?: "Enjoys thoughtful conversation and exploring the city."
        val partnerInterests = partnerProfile?.interests.orEmpty()
        val partnerCity = partnerProfile?.cityLabel.orEmpty()
        val myInterests = myProfile?.interests.orEmpty()
        val sharedInterests = partnerInterests.filter { p ->
            myInterests.any { m -> m.equals(p, ignoreCase = true) }
        }

        val prompt = buildString {
            append("Generate 4 natural, friendly, respectful direct messages for a social app member to send to $partnerName.\n")
            append("Requested tone: $tonePreference.\n")
            if (partnerCity.isNotBlank()) append("Recipient city: $partnerCity.\n")
            if (partnerInterests.isNotEmpty()) append("Recipient interests: ${partnerInterests.joinToString(", ")}.\n")
            if (sharedInterests.isNotEmpty()) append("Shared interests between both users: ${sharedInterests.joinToString(", ")}.\n")
            append("Recipient bio: $partnerBio\n")
            if (myInterests.isNotEmpty()) append("Sender interests: ${myInterests.joinToString(", ")}.\n")
            append("Keep each opener conversational, specific to their profile details, free of clichés, and under 140 characters.")
        }

        return try {
            val suggestions = withTimeout(ICEBREAKER_TIMEOUT_MS) {
                val response = model.generateContent(prompt)
                val rawJson = response.text?.trim()
                    ?: throw IllegalStateException("Gemini returned an empty response.")
                parseStructuredOpeners(rawJson)
            }
            if (suggestions.isEmpty()) {
                Result.failure(IllegalStateException("No icebreaker suggestions were returned."))
            } else {
                Result.success(suggestions)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI icebreaker generation timed out", e)
            Result.failure(IllegalStateException("AI opener generation timed out. Try again or use a profile prompt below."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI icebreaker generation failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Failed to generate AI openers."))
        }
    }

    companion object {
        fun parseStructuredOpeners(jsonString: String): List<IcebreakerSuggestion> {
            val root = JSONObject(jsonString)
            val array = root.optJSONArray("openers") ?: return emptyList()
            val items = mutableListOf<IcebreakerSuggestion>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val prompt = obj.optString("prompt").trim()
                val tone = obj.optString("tone").trim().ifBlank { "Suggested" }
                val reason = obj.optString("reason").trim().ifBlank { "Based on profile" }
                if (prompt.isNotBlank()) {
                    items += IcebreakerSuggestion(prompt = prompt, tone = tone, reason = reason)
                }
            }
            return items.take(6)
        }

        fun buildProfileBasedOpeners(
            myProfile: PublicProfile?,
            partnerProfile: PublicProfile?
        ): List<IcebreakerSuggestion> {
            val name = partnerProfile?.displayName?.ifBlank { "there" } ?: "there"
            val myInterests = myProfile?.interests.orEmpty()
            val partnerInterests = partnerProfile?.interests.orEmpty()
            val shared = partnerInterests.firstOrNull { p ->
                myInterests.any { m -> m.equals(p, ignoreCase = true) }
            }
            val topInterest = shared ?: partnerInterests.firstOrNull()
            val secondInterest = partnerInterests.getOrNull(1)
            val city = partnerProfile?.cityLabel?.substringBefore(",")?.trim()?.takeIf { it.isNotBlank() }
            val bioSnippet = partnerProfile?.bio?.trim()?.takeIf { it.isNotBlank() }

            val list = mutableListOf<IcebreakerSuggestion>()

            if (shared != null) {
                list += IcebreakerSuggestion(
                    prompt = "We both love $shared — what's your all-time favorite spot or story around it?",
                    tone = "Shared Interest",
                    reason = "You both listed $shared"
                )
            } else if (topInterest != null) {
                list += IcebreakerSuggestion(
                    prompt = "I noticed $topInterest on your profile — what got you hooked on it?",
                    tone = "Curious",
                    reason = "From ${partnerProfile?.displayName ?: "their"} interests"
                )
            }

            if (city != null) {
                list += IcebreakerSuggestion(
                    prompt = if (secondInterest != null) {
                        "Quick vote in $city: $secondInterest hangout or afternoon coffee walk?"
                    } else {
                        "What's your favorite neighborhood spot in $city for good coffee?"
                    },
                    tone = "Local Vibe",
                    reason = "Local to $city"
                )
            }

            if (bioSnippet != null) {
                val keyword = bioSnippet.split(" ", ",", ".").firstOrNull { it.length >= 5 }
                list += IcebreakerSuggestion(
                    prompt = if (keyword != null) {
                        "Your bio caught my eye ($keyword!) — what's the story behind that?"
                    } else {
                        "Hey $name! What's been the highlight of your week so far?"
                    },
                    tone = "Bio Hook",
                    reason = "Inspired by profile bio"
                )
            }

            list += IcebreakerSuggestion(
                prompt = "What are you creating or obsessed with lately, $name?",
                tone = "Friendly",
                reason = "Easy conversation starter"
            )

            return list.distinctBy { it.prompt }.take(4)
        }
    }
}
