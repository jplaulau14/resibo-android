package com.patslaurel.resibo.factcheck

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerplexityClient
    @Inject
    constructor() {
        fun search(claim: String): PerplexityResult {
            if (claim.isBlank()) return PerplexityResult.EMPTY
            Log.i(TAG, "Searching: '${claim.take(80)}...'")

            val requestBody =
                JSONObject().apply {
                    put("model", "sonar")
                    put(
                        "messages",
                        JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("role", "system")
                                    put("content", SYSTEM_PROMPT)
                                },
                            )
                            put(
                                JSONObject().apply {
                                    put("role", "user")
                                    put("content", claim.take(500))
                                },
                            )
                        },
                    )
                }

            return try {
                val conn = URL(API_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $API_KEY")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.doOutput = true
                conn.outputStream.use { it.write(requestBody.toString().toByteArray()) }

                if (conn.responseCode != 200) {
                    Log.w(TAG, "Perplexity returned ${conn.responseCode}")
                    return PerplexityResult.EMPTY
                }

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parseResponse(body)
            } catch (e: Exception) {
                Log.w(TAG, "Perplexity error: ${e.message}")
                PerplexityResult.EMPTY
            }
        }

        private fun parseResponse(json: String): PerplexityResult {
            val root = JSONObject(json)
            val choices = root.optJSONArray("choices")
            val text =
                choices
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content", "") ?: ""

            val citationsArray = root.optJSONArray("citations")
            val citations = mutableListOf<String>()
            if (citationsArray != null) {
                for (i in 0 until citationsArray.length()) {
                    citations.add(citationsArray.optString(i, ""))
                }
            }

            val searchResults = root.optJSONArray("search_results")
            val sources = mutableListOf<FactCheckResult>()
            if (searchResults != null) {
                for (i in 0 until searchResults.length()) {
                    val sr = searchResults.getJSONObject(i)
                    sources.add(
                        FactCheckResult(
                            claimText = sr.optString("snippet", "").take(200),
                            claimant = "",
                            rating = "",
                            reviewUrl = sr.optString("url", ""),
                            reviewTitle = sr.optString("title", ""),
                            publisherName = extractDomain(sr.optString("url", "")),
                            publisherSite = sr.optString("url", ""),
                            reviewDate = sr.optString("date", sr.optString("last_updated", "")),
                        ),
                    )
                }
            } else if (citations.isNotEmpty()) {
                citations.forEach { url ->
                    sources.add(
                        FactCheckResult(
                            claimText = "",
                            claimant = "",
                            rating = "",
                            reviewUrl = url,
                            reviewTitle = "",
                            publisherName = extractDomain(url),
                            publisherSite = url,
                            reviewDate = "",
                        ),
                    )
                }
            }

            Log.i(TAG, "Got ${sources.size} sources, ${text.length}-char evidence text")
            return PerplexityResult(text = text, sources = sources)
        }

        private fun extractDomain(url: String): String =
            runCatching {
                URL(url).host.removePrefix("www.").removePrefix("en.")
            }.getOrDefault(url.take(30))

        companion object {
            private const val TAG = "PerplexityClient"
            private const val API_URL = "https://api.perplexity.ai/chat/completions"
            private const val API_KEY = com.patslaurel.resibo.BuildConfig.PERPLEXITY_API_KEY
            private const val SYSTEM_PROMPT =
                "You are a fact-check research assistant. For the given claim, find relevant fact-checks " +
                    "and evidence. Be concise (3-4 sentences max). Focus on what credible sources say about " +
                    "this specific claim. If it's a joke or meme, say so in one sentence."
        }
    }

data class PerplexityResult(
    val text: String = "",
    val sources: List<FactCheckResult> = emptyList(),
) {
    companion object {
        val EMPTY = PerplexityResult()
    }
}
