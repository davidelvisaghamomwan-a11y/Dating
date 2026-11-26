package com.example.util

import com.example.model.PublicProfile

data class ProfileCompletionSummary(
    val percentage: Int,
    val missingSteps: List<String>
)

object ProfileCompletionUtils {
    fun calculate(
        profile: PublicProfile?,
        videoCount: Int = 0
    ): ProfileCompletionSummary {
        if (profile == null) {
            return ProfileCompletionSummary(
                percentage = 0,
                missingSteps = listOf("Save basic details", "Add a bio", "Upload profile photos", "Pick interests")
            )
        }
        var score = 0
        val missing = mutableListOf<String>()

        if (profile.displayName.trim().length >= 2 && profile.age in 18..99 && profile.gender.isNotBlank()) {
            score += 20
        } else {
            missing += "Complete basic info"
        }

        if (profile.bio.trim().length >= 20) {
            score += 15
        } else {
            missing += "Write a bio (20+ chars)"
        }

        if (profile.cityLabel.trim().isNotBlank() && profile.country.trim().isNotBlank()) {
            score += 15
        } else {
            missing += "Set city and country"
        }

        val interestCount = profile.interests.size
        when {
            interestCount >= 3 -> score += 15
            interestCount >= 1 -> {
                score += 8
                missing += "Add ${3 - interestCount} more interests"
            }
            else -> missing += "Pick at least 3 interests"
        }

        val photoCount = profile.allProfilePhotos().size
        when {
            photoCount >= 2 -> score += 25
            photoCount == 1 -> {
                score += 12
                missing += "Add a second profile photo"
            }
            else -> missing += "Upload at least 2 photos"
        }

        if (videoCount > 0) {
            score += 10
        } else {
            missing += "Add a showcase video clip"
        }

        return ProfileCompletionSummary(
            percentage = score.coerceIn(0, 100),
            missingSteps = missing
        )
    }

    fun canViewerSeeSection(
        visibility: String,
        isOwner: Boolean,
        isFollowing: Boolean = false,
        isFriend: Boolean = false,
        isMatch: Boolean = false
    ): Boolean {
        if (isOwner) return true
        return when (visibility.uppercase()) {
            "FOLLOWERS_ONLY", "FRIENDS_ONLY" -> isFollowing || isFriend
            "MATCHES_AND_FRIENDS" -> isFollowing || isFriend || isMatch
            else -> true
        }
    }
}
