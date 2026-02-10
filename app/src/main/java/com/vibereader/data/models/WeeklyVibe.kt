package com.vibereader.data.models

/**
 * Data class representing a generated Weekly Vibe insight.
 * Structured for Spotify Wrapped-style presentation.
 */
data class WeeklyVibe(
    val generatedAt: Long,
    val weekStart: Long,
    val weekEnd: Long,
    // Structured vibe data
    val vibeTitle: String,           // e.g., "Existential Explorer"
    val vibeEmoji: String,           // e.g., "🌌"
    val themeTags: List<String>,     // e.g., ["Power", "Mortality", "Language"]
    val insightNuggets: List<String>,// 2-3 short punchy insights
    val wordSpotlight: String,       // Standout word
    val quoteSpotlight: String,      // Standout quote snippet
    // Stats
    val wordCount: Int,
    val quoteCount: Int,
    val sessionCount: Int,
    val bookTitles: List<String>
)
