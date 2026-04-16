package com.patslaurel.resibo.factcheck

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queries the Google Fact Check Tools API for real-time ClaimReview results.
 *
 * Free tier, no quota concerns for hackathon volumes. Returns structured
 * fact-check data from IFCN-certified publishers (Rappler, Vera Files,
 * AFP Fact Check, FactCheck.org, Snopes, etc.).
 *
 * Docs: https://developers.google.com/fact-check/tools/api/reference/rest
 */
@Singleton
class FactCheckApiClient
    @Inject
    constructor() {
        /**
         * Search for fact-checks related to [query]. Returns up to [maxResults]
         * structured results. Returns empty list on network error or no matches.
         * Must be called off the main thread.
         */
        /**
         * Search with raw keywords (already extracted by Gemma).
         * No keyword extraction — pass directly to the API.
         */
        fun searchRaw(
            keywords: String,
            maxResults: Int = 5,
        ): List<FactCheckResult> {
            if (keywords.isBlank()) return emptyList()
            Log.i(TAG, "Searching raw: '$keywords'")
            val encoded = URLEncoder.encode(keywords.take(200), "UTF-8")
            return executeSearch(encoded, maxResults)
        }

        fun search(
            query: String,
            maxResults: Int = 5,
        ): List<FactCheckResult> {
            if (query.isBlank()) return emptyList()

            val keywords = extractKeywords(query)
            if (keywords.isBlank()) return emptyList()

            Log.i(TAG, "Searching: '$keywords' (from: '${query.take(60)}...')")
            val encoded = URLEncoder.encode(keywords, "UTF-8")
            val url =
                "$BASE_URL?query=$encoded&pageSize=$maxResults&key=$API_KEY"

            return executeSearch(encoded, maxResults)
        }

        private fun executeSearch(
            encodedQuery: String,
            maxResults: Int,
        ): List<FactCheckResult> {
            val url = "$BASE_URL?query=$encodedQuery&pageSize=$maxResults&key=$API_KEY"
            return try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000

                if (conn.responseCode != 200) {
                    Log.w(TAG, "Fact Check API returned ${conn.responseCode}")
                    return emptyList()
                }

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val results = parseResponse(body)
                Log.i(TAG, "API returned ${results.size} fact-check results")
                results
            } catch (e: Exception) {
                Log.w(TAG, "Fact Check API error: ${e.message}")
                emptyList()
            }
        }

        private fun parseResponse(json: String): List<FactCheckResult> {
            val root = JSONObject(json)
            val claims = root.optJSONArray("claims") ?: return emptyList()

            return (0 until claims.length()).mapNotNull { i ->
                val claim = claims.getJSONObject(i)
                val reviews = claim.optJSONArray("claimReview") ?: return@mapNotNull null
                if (reviews.length() == 0) return@mapNotNull null

                val review = reviews.getJSONObject(0)
                val publisher = review.optJSONObject("publisher")

                FactCheckResult(
                    claimText = claim.optString("text", ""),
                    claimant = claim.optString("claimant", ""),
                    rating = review.optString("textualRating", ""),
                    reviewUrl = review.optString("url", ""),
                    reviewTitle = review.optString("title", ""),
                    publisherName = publisher?.optString("name", "") ?: "",
                    publisherSite = publisher?.optString("site", "") ?: "",
                    reviewDate = review.optString("reviewDate", claim.optString("claimDate", "")),
                )
            }
        }

        /**
         * Extract search-friendly keywords from potentially Tagalog/Taglish input.
         * Strategy: keep proper nouns (capitalized), numbers, and English content words.
         * Drops Tagalog function words (na, ng, sa, mga, at, ay, ang, etc.).
         */
        private fun extractKeywords(text: String): String {
            val tagalogStopWords =
                setOf(
                    "na",
                    "ng",
                    "sa",
                    "mga",
                    "at",
                    "ay",
                    "ang",
                    "ni",
                    "si",
                    "ko",
                    "mo",
                    "po",
                    "ba",
                    "ito",
                    "yan",
                    "yun",
                    "daw",
                    "raw",
                    "din",
                    "rin",
                    "pa",
                    "lang",
                    "naman",
                    "kasi",
                    "pero",
                    "sabi",
                    "nag",
                    "may",
                    "wala",
                    "kung",
                    "para",
                    "ka",
                    "ako",
                    "siya",
                    "sila",
                    "nila",
                    "namin",
                    "natin",
                    "kami",
                    "tayo",
                    "ikaw",
                    "kanila",
                    "kumakalat",
                    "totoo",
                    "hindi",
                    "talaga",
                    "dito",
                    "doon",
                    "galing",
                    "pag",
                    "dahil",
                    "tungkol",
                    "nang",
                    "noong",
                    "the",
                    "is",
                    "a",
                    "an",
                    "in",
                    "of",
                    "to",
                    "and",
                    "that",
                    "this",
                    "it",
                    "for",
                    "with",
                    "from",
                    "not",
                    "are",
                    "was",
                )

            val words =
                text
                    .replace(Regex("[^\\w\\s]"), " ")
                    .split(Regex("\\s+"))
                    .filter { word ->
                        word.length > 2 && word.lowercase() !in tagalogStopWords
                    }.map { it.lowercase() }
                    .distinct()
                    .sortedByDescending { it.length }
                    .take(6)

            return words.joinToString(" ")
        }

        companion object {
            private const val TAG = "FactCheckApi"
            private const val BASE_URL =
                "https://factchecktools.googleapis.com/v1alpha1/claims:search"

            // Scoped to factchecktools.googleapis.com only
            private const val API_KEY = "REDACTED_GOOGLE_API_KEY"
        }
    }

data class FactCheckResult(
    val claimText: String,
    val claimant: String,
    val rating: String,
    val reviewUrl: String,
    val reviewTitle: String,
    val publisherName: String,
    val publisherSite: String,
    val reviewDate: String,
)
