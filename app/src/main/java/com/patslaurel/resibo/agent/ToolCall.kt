package com.patslaurel.resibo.agent

import org.json.JSONObject

sealed interface ToolCall {
    data class SearchWeb(
        val query: String,
    ) : ToolCall

    data object AnalyzeImage : ToolCall
}

data class ToolResult(
    val toolName: String,
    val input: String,
    val output: String,
)

object ToolCallParser {
    private const val OPEN_TAG = "<tool>"
    private const val CLOSE_TAG = "</tool>"

    fun parse(text: String): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        var searchFrom = 0
        while (true) {
            val start = text.indexOf(OPEN_TAG, searchFrom)
            if (start == -1) break
            val end = text.indexOf(CLOSE_TAG, start + OPEN_TAG.length)
            if (end == -1) break

            val jsonStr = text.substring(start + OPEN_TAG.length, end).trim()
            runCatching {
                val json = JSONObject(jsonStr)
                when (json.optString("name")) {
                    "search_web" -> calls.add(ToolCall.SearchWeb(json.optString("query", "")))
                    "analyze_image" -> calls.add(ToolCall.AnalyzeImage)
                }
            }
            searchFrom = end + CLOSE_TAG.length
        }
        return calls
    }

    fun hasToolCalls(text: String): Boolean =
        text.contains(OPEN_TAG) && text.contains(CLOSE_TAG)

    fun stripToolCalls(text: String): String {
        var result = text
        while (true) {
            val start = result.indexOf(OPEN_TAG)
            if (start == -1) break
            val end = result.indexOf(CLOSE_TAG, start)
            if (end == -1) break
            result = result.removeRange(start, end + CLOSE_TAG.length)
        }
        return result.trim()
    }
}
