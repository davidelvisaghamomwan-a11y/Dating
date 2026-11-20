package com.example.communication

import androidx.compose.ui.graphics.Color

data class KindredGifReaction(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val accentHex: Long,
    val secondaryHex: Long,
    val badgeText: String
)

object GifCatalog {
    val CATEGORIES = listOf("All", "Spark", "Dates", "Vibes", "Cheers")

    val REACTIONS = listOf(
        KindredGifReaction(
            id = "gif_spark_hello",
            title = "Hey You ✨",
            subtitle = "Golden spark hello",
            category = "Spark",
            accentHex = 0xFFE94E65,
            secondaryHex = 0xFFF5B041,
            badgeText = "GIF • SPARK"
        ),
        KindredGifReaction(
            id = "gif_blush_smile",
            title = "Made Me Smile",
            subtitle = "Warm heartbeat glow",
            category = "Spark",
            accentHex = 0xFFD946EF,
            secondaryHex = 0xFFE94E65,
            badgeText = "GIF • FLIRT"
        ),
        KindredGifReaction(
            id = "gif_coffee_cheers",
            title = "Coffee Date?",
            subtitle = "Pour-over steam swirl",
            category = "Dates",
            accentHex = 0xFFB45309,
            secondaryHex = 0xFFF59E0B,
            badgeText = "GIF • COFFEE"
        ),
        KindredGifReaction(
            id = "gif_sunset_walk",
            title = "Golden Hour Walk",
            subtitle = "Rooftop twilight pulse",
            category = "Dates",
            accentHex = 0xFFEA580C,
            secondaryHex = 0xFFE94E65,
            badgeText = "GIF • DATE"
        ),
        KindredGifReaction(
            id = "gif_cheers_clink",
            title = "Cheers Tonight",
            subtitle = "Celebration toast sparkle",
            category = "Cheers",
            accentHex = 0xFF10B981,
            secondaryHex = 0xFF3B82F6,
            badgeText = "GIF • TOAST"
        ),
        KindredGifReaction(
            id = "gif_haha_vibe",
            title = "HahaWaitWhat 😂",
            subtitle = "Pure laugh energy",
            category = "Vibes",
            accentHex = 0xFF6366F1,
            secondaryHex = 0xFFEC4899,
            badgeText = "GIF • LAUGH"
        ),
        KindredGifReaction(
            id = "gif_on_my_way",
            title = "On My Way!",
            subtitle = "Fast city dash",
            category = "Vibes",
            accentHex = 0xFF0EA5E9,
            secondaryHex = 0xFF10B981,
            badgeText = "GIF • OMW"
        ),
        KindredGifReaction(
            id = "gif_night_stars",
            title = "Under The Stars",
            subtitle = "Midnight constellation",
            category = "Cheers",
            accentHex = 0xFF312E81,
            secondaryHex = 0xFFA855F7,
            badgeText = "GIF • NIGHT"
        )
    )

    fun findById(id: String?): KindredGifReaction? {
        if (id.isNullOrBlank()) return null
        return REACTIONS.firstOrNull { it.id == id }
    }

    fun isHttpGifUrl(value: String?): Boolean {
        val clean = value?.trim()?.lowercase() ?: return false
        return clean.startsWith("https://") && (clean.contains(".gif") || clean.contains("giphy") || clean.contains("tenor"))
    }

    fun primaryColor(reaction: KindredGifReaction): Color = Color(reaction.accentHex)
    fun secondaryColor(reaction: KindredGifReaction): Color = Color(reaction.secondaryHex)
}
