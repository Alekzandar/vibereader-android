package com.vibereader.data.network

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.vibereader.BuildConfig
import com.vibereader.data.db.Quote
import com.vibereader.data.db.Word
import org.json.JSONObject

/**
 * Singleton client for Gemini AI integration.
 * Used to generate "Weekly Vibe" insights from reading captures.
 */
object GeminiClient {

    private const val TAG = "GeminiClient"

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                temperature = 0.5f
                maxOutputTokens = 256
            }
        )
    }

    /**
     * Response structure for parsed vibe JSON.
     */
    data class VibeResponse(
        val vibeTitle: String,
        val vibeEmoji: String,
        val themeTags: List<String>,
        val insights: List<String>,
        val wordSpotlight: String,
        val quoteSpotlight: String
    )

    /**
     * Generates a "Weekly Vibe" reflection based on reading captures.
     * Returns structured JSON data for Wrapped-style display.
     *
     * @param words List of words defined during the time period
     * @param quotes List of quotes saved during the time period
     * @param bookTitles List of book titles read during the time period
     * @param sessionCount Number of reading sessions
     * @return Result containing the parsed VibeResponse or an error
     */
    suspend fun generateWeeklyVibe(
        words: List<Word>,
        quotes: List<Quote>,
        bookTitles: List<String>,
        sessionCount: Int
    ): Result<VibeResponse> {
        // Handle empty data case
        if (words.isEmpty() && quotes.isEmpty()) {
            return Result.failure(Exception("No captures this week. Start reading to generate your vibe!"))
        }

        return try {
            val prompt = buildVibePrompt(words, quotes, bookTitles, sessionCount)
            Log.d(TAG, "Generating vibe with ${words.size} words, ${quotes.size} quotes")

            val response = generativeModel.generateContent(prompt)
            val responseText = response.text

            if (responseText.isNullOrBlank()) {
                Result.failure(Exception("Empty response from Gemini"))
            } else {
                Log.d(TAG, "Raw response: $responseText")
                parseVibeResponse(responseText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate vibe", e)
            Result.failure(e)
        }
    }

    private fun buildVibePrompt(
        words: List<Word>,
        quotes: List<Quote>,
        bookTitles: List<String>,
        sessionCount: Int
    ): String {
        // Take top 10 words (terms only)
        val wordTerms = words.take(10).map { it.term }

        // Take top 5 quotes, truncated to 50 chars
        val truncatedQuotes = quotes.take(5).map {
            if (it.content.length > 50) it.content.take(50) + "..." else it.content
        }

        return """
You analyze reading captures. Respond ONLY with valid JSON, no markdown.

Words: ${wordTerms.joinToString(", ")}
Quotes: ${truncatedQuotes.map { "\"$it\"" }.joinToString(", ")}
Books: ${bookTitles.distinct().joinToString(", ")}
Sessions: $sessionCount

Return this exact JSON structure:
{
  "vibe_title": "2-3 word theme title",
  "vibe_emoji": "single emoji",
  "theme_tags": ["tag1", "tag2", "tag3"],
  "insights": ["insight1 (max 12 words)", "insight2 (max 12 words)"],
  "word_spotlight": "most interesting word from list",
  "quote_spotlight": "most evocative quote snippet (max 60 chars)"
}
        """.trimIndent()
    }

    private fun parseVibeResponse(responseText: String): Result<VibeResponse> {
        return try {
            // Clean up response - remove markdown code blocks if present
            val cleanJson = responseText
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val json = JSONObject(cleanJson)

            val vibeResponse = VibeResponse(
                vibeTitle = json.optString("vibe_title", "Reading Vibe"),
                vibeEmoji = json.optString("vibe_emoji", "📚"),
                themeTags = json.optJSONArray("theme_tags")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                insights = json.optJSONArray("insights")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                wordSpotlight = json.optString("word_spotlight", ""),
                quoteSpotlight = json.optString("quote_spotlight", "")
            )

            Log.d(TAG, "Parsed vibe: $vibeResponse")
            Result.success(vibeResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse vibe JSON: $responseText", e)
            Result.failure(Exception("Failed to parse vibe response: ${e.message}"))
        }
    }
}
