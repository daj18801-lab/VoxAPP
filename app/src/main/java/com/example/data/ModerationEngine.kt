package com.example.data

sealed interface AutoModEvaluation {
    data class Clean(val text: String) : AutoModEvaluation
    data class Masked(val sanitizedText: String, val matchedTerms: List<String>) : AutoModEvaluation
    data class Blocked(val reason: String, val matchedTerms: List<String>) : AutoModEvaluation
}

object ModerationEngine {
    // Baseline community safety filter terms combined with per-room custom blocked words.
    private val baselineBlockedTerms = setOf(
        "scam",
        "phishing",
        "doxx",
        "doxxing",
        "swat",
        "slur",
        "idiot",
        "moron",
        "loser",
        "trash",
        "stfu",
        "spammer",
        "hacklink"
    )

    private val urlRegex = Regex(
        pattern = """(?i)(https?://\S+|www\.\S+|\b[a-z0-9\-]+\.(com|net|org|io|gg|xyz|ru|co)\b(/\S*)?)"""
    )

    fun evaluateMessage(
        rawText: String,
        room: ChatRoom
    ): AutoModEvaluation {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return AutoModEvaluation.Blocked("Message cannot be empty.", emptyList())
        }

        if (room.linksBlocked && urlRegex.containsMatchIn(trimmed)) {
            return AutoModEvaluation.Blocked(
                reason = "Links are disabled by room moderators.",
                matchedTerms = listOf("URL")
            )
        }

        if (!room.autoModEnabled) {
            return AutoModEvaluation.Clean(trimmed)
        }

        val combinedTerms = (baselineBlockedTerms + room.blockedWords.map { it.trim().lowercase() })
            .filter { it.length >= 2 }

        val matched = mutableListOf<String>()
        var sanitized = trimmed

        for (term in combinedTerms) {
            val escaped = Regex.escape(term)
            val pattern = Regex("""(?i)\b$escaped\b""")
            if (pattern.containsMatchIn(sanitized)) {
                matched += term
                sanitized = pattern.replace(sanitized) { match ->
                    "*".repeat(match.value.length.coerceAtLeast(3))
                }
            }
        }

        if (matched.isEmpty()) {
            return AutoModEvaluation.Clean(trimmed)
        }

        return if (room.autoModBlockMode) {
            AutoModEvaluation.Blocked(
                reason = "Blocked by Auto-Mod filter (${matched.joinToString(", ")}).",
                matchedTerms = matched
            )
        } else {
            AutoModEvaluation.Masked(
                sanitizedText = sanitized,
                matchedTerms = matched
            )
        }
    }

    fun remainingSlowModeSeconds(
        lastSentEpochMs: Long,
        slowModeSeconds: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        if (slowModeSeconds <= 0L || lastSentEpochMs <= 0L) return 0
        val elapsedSeconds = (nowMs - lastSentEpochMs) / 1000L
        val remaining = slowModeSeconds - elapsedSeconds
        return remaining.coerceAtLeast(0L).toInt()
    }
}
