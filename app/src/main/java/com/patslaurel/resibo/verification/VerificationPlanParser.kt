package com.patslaurel.resibo.verification

object VerificationPlanParser {
    fun parse(
        raw: String,
        fallbackClaim: String,
    ): VerificationPlan =
        runCatching {
            val json = JsonObjectParser(extractJsonObject(raw)).parseObject()
            VerificationPlan(
                claim = json.string("claim", fallbackClaim).ifBlank { fallbackClaim },
                language = json.string("language", "unknown").ifBlank { "unknown" },
                claimCategory = parseClaimCategory(json.string("claim_category")),
                timeSensitivity = parseTimeSensitivity(json.string("time_sensitivity")),
                requiresLiveEvidence = json.boolean("requires_live_evidence", true),
                requiredFreshnessHours =
                    if (json.containsKey("required_freshness_hours") && json["required_freshness_hours"] != null) {
                        json.int("required_freshness_hours")
                    } else {
                        null
                    },
                toolCalls = parseToolCalls(json),
                preferredSources = json.stringList("preferred_sources"),
                abstentionReasonIfNoEvidence =
                    json.string(
                        "abstention_reason_if_no_evidence",
                        "I could not find enough fresh evidence from the allowed sources to verify this claim.",
                    ),
            )
        }.getOrElse {
            fallbackPlan(fallbackClaim)
        }

    private fun extractJsonObject(raw: String): String {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start >= 0 && end > start) { "No JSON object found" }
        return trimmed.substring(start, end + 1)
    }

    private fun parseToolCalls(json: Map<String, Any?>): List<VerificationToolCall> {
        val calls = json.array("tool_calls")
        return buildList {
            for (call in calls) {
                val item = call as? Map<*, *> ?: continue
                val toolName = normalizeToolName(item.string("tool_name"))
                if (toolName.isBlank()) continue
                add(
                    VerificationToolCall(
                        toolName = toolName,
                        query = item.string("query"),
                        url = item.string("url"),
                        maxResults = item.int("max_results", 5).coerceIn(1, 10),
                        preferredDomains = item.stringList("preferred_domains"),
                    ),
                )
            }
        }
    }

    private fun normalizeToolName(raw: String): String =
        when (raw.trim().lowercase()) {
            "perplexity", "perplexity_discovery", "search_web" -> VerificationToolNames.PERPLEXITY_DISCOVERY
            "official", "official_source", "official_source_search" -> VerificationToolNames.OFFICIAL_SOURCE
            "local", "local_evidence", "local_cache" -> VerificationToolNames.LOCAL_EVIDENCE
            else -> ""
        }

    private fun parseClaimCategory(raw: String): ClaimCategory =
        when (raw.trim().lowercase()) {
            "public_policy", "policy", "government_policy" -> ClaimCategory.PUBLIC_POLICY
            "disaster_weather", "weather", "disaster" -> ClaimCategory.DISASTER_WEATHER
            "election_government", "election", "government" -> ClaimCategory.ELECTION_GOVERNMENT
            "transport", "transportation" -> ClaimCategory.TRANSPORT
            "scam", "fraud" -> ClaimCategory.SCAM
            "health", "medical" -> ClaimCategory.HEALTH
            else -> ClaimCategory.OTHER
        }

    private fun parseTimeSensitivity(raw: String): TimeSensitivity =
        when (raw.trim().lowercase()) {
            "evergreen" -> TimeSensitivity.EVERGREEN
            "recent" -> TimeSensitivity.RECENT
            "current" -> TimeSensitivity.CURRENT
            "breaking" -> TimeSensitivity.BREAKING
            else -> TimeSensitivity.RECENT
        }

    private fun fallbackPlan(claim: String): VerificationPlan =
        VerificationPlan(
            claim = claim,
            claimCategory = ClaimCategory.OTHER,
            timeSensitivity = TimeSensitivity.RECENT,
            requiresLiveEvidence = true,
            requiredFreshnessHours = 48,
            toolCalls =
                listOf(
                    VerificationToolCall(
                        toolName = VerificationToolNames.LOCAL_EVIDENCE,
                        query = claim,
                    ),
                    VerificationToolCall(
                        toolName = VerificationToolNames.PERPLEXITY_DISCOVERY,
                        query = claim,
                    ),
                ),
        )
}

private fun Map<*, *>.string(
    key: String,
    default: String = "",
): String = (this[key] as? String) ?: default

private fun Map<*, *>.boolean(
    key: String,
    default: Boolean = false,
): Boolean = (this[key] as? Boolean) ?: default

private fun Map<*, *>.int(
    key: String,
    default: Int = 0,
): Int =
    when (val value = this[key]) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }

private fun Map<*, *>.array(key: String): List<Any?> = this[key] as? List<Any?> ?: emptyList()

private fun Map<*, *>.stringList(key: String): List<String> =
    array(key)
        .mapNotNull { (it as? String)?.trim() }
        .filter { it.isNotBlank() }

private class JsonObjectParser(
    private val input: String,
) {
    private var index = 0

    fun parseObject(): Map<String, Any?> {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        require(index == input.length) { "Trailing characters after JSON object" }
        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, Any?> ?: error("Root JSON value is not an object")
    }

    private fun parseValue(): Any? {
        skipWhitespace()
        return when (peek()) {
            '{' -> parseJsonObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> parseLiteral("true", true)
            'f' -> parseLiteral("false", false)
            'n' -> parseLiteral("null", null)
            else -> parseNumber()
        }
    }

    private fun parseJsonObject(): Map<String, Any?> {
        expect('{')
        skipWhitespace()
        if (consumeIf('}')) return emptyMap()

        val result = linkedMapOf<String, Any?>()
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            result[key] = parseValue()
            skipWhitespace()
            when {
                consumeIf('}') -> return result
                consumeIf(',') -> Unit
                else -> error("Expected ',' or '}'")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        skipWhitespace()
        if (consumeIf(']')) return emptyList()

        return buildList {
            while (true) {
                add(parseValue())
                skipWhitespace()
                when {
                    consumeIf(']') -> return@buildList
                    consumeIf(',') -> Unit
                    else -> error("Expected ',' or ']'")
                }
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (index < input.length) {
            when (val char = input[index++]) {
                '"' -> return result.toString()
                '\\' -> result.append(parseEscape())
                else -> result.append(char)
            }
        }
        error("Unterminated string")
    }

    private fun parseEscape(): Char {
        require(index < input.length) { "Unterminated escape sequence" }
        return when (val escaped = input[index++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> parseUnicodeEscape()
            else -> error("Unsupported escape sequence")
        }
    }

    private fun parseUnicodeEscape(): Char {
        require(index + UNICODE_ESCAPE_LENGTH <= input.length) { "Invalid unicode escape" }
        val hex = input.substring(index, index + UNICODE_ESCAPE_LENGTH)
        index += UNICODE_ESCAPE_LENGTH
        return hex.toInt(16).toChar()
    }

    private fun parseNumber(): Number {
        val start = index
        if (peek() == '-') index++
        while (peekOrNull()?.isDigit() == true) index++
        if (peekOrNull() == '.') {
            index++
            while (peekOrNull()?.isDigit() == true) index++
        }
        if (peekOrNull() == 'e' || peekOrNull() == 'E') {
            index++
            if (peekOrNull() == '+' || peekOrNull() == '-') index++
            while (peekOrNull()?.isDigit() == true) index++
        }
        require(index > start) { "Expected JSON value" }

        val raw = input.substring(start, index)
        return if (raw.contains('.') || raw.contains('e', ignoreCase = true)) {
            raw.toDouble()
        } else {
            raw.toLong()
        }
    }

    private fun parseLiteral(
        literal: String,
        value: Any?,
    ): Any? {
        require(input.startsWith(literal, index)) { "Expected $literal" }
        index += literal.length
        return value
    }

    private fun skipWhitespace() {
        while (peekOrNull()?.isWhitespace() == true) index++
    }

    private fun expect(expected: Char) {
        require(peek() == expected) { "Expected '$expected'" }
        index++
    }

    private fun consumeIf(expected: Char): Boolean {
        if (peekOrNull() != expected) return false
        index++
        return true
    }

    private fun peek(): Char = peekOrNull() ?: error("Unexpected end of JSON")

    private fun peekOrNull(): Char? = input.getOrNull(index)

    private companion object {
        const val UNICODE_ESCAPE_LENGTH = 4
    }
}
