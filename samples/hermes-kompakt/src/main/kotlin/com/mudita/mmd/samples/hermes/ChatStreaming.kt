package com.mudita.mmd.samples.hermes

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The part of a streaming reply to show now: every paragraph that has ended (text before the
 * last blank line), so an E Ink screen redraws once per paragraph rather than once per token.
 * A blank line inside an open ``` code fence is not a boundary. Empty until a paragraph ends.
 */
fun completedParagraphs(buffer: String): String {
    var cut = buffer.lastIndexOf("\n\n")
    while (cut > 0) {
        val prefix = buffer.substring(0, cut)
        if (codeFences(prefix) % 2 == 0) return prefix.trimEnd()
        cut = buffer.lastIndexOf("\n\n", cut - 1)
    }
    return ""
}

private fun codeFences(text: String): Int = text.lineSequence().count { it.trimStart().startsWith("```") }

/** Providers named in a usage-limit line when the error mentions one. */
private val PROVIDERS = listOf("Anthropic", "OpenAI", "OpenRouter", "Gemini", "Google", "xAI", "Mistral", "DeepSeek", "Groq")

private val USAGE_LIMIT = Regex("""(?i)usage limit|quota (exceeded|reached)|regain access|limit resets""")
private val REGAIN_ON = Regex("""(?i)regain access on (\d{4}-\d{2}-\d{2})(?: at (\d{1,2}:\d{2}) ?UTC)?""")
private val RESETS_IN = Regex("""(?i)resets in ([0-9][^.;,\n]*)""")
private val RESETS_AT = Regex("""(?i)resets at (\d{1,2}:\d{2})""")
private val RESETS_ON = Regex("""(?i)resets (?:on )?([A-Z][a-z]{2,8}\.? \d{1,2})\b""")

/** Longest failure line shown under a message before it is cut with an ellipsis. */
private const val MAX_FAILURE_CHARS = 160

/**
 * One short, readable line for a failed run, shown under the message that failed. A usage-limit
 * error becomes "Anthropic usage limit reached. Resets 1 Dec." (the reset only when the error
 * names it); anything else is the error's first line, shortened.
 */
fun runFailureLine(raw: String, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String {
    val text = raw.trim()
    if (text.isEmpty()) return "The run failed."
    if (USAGE_LIMIT.containsMatchIn(text)) {
        val provider = PROVIDERS.firstOrNull { text.contains(it, ignoreCase = true) }
        val lead = provider?.let { "$it usage limit reached." } ?: "Usage limit reached."
        return listOfNotNull(lead, resetPhrase(text, locale, zone)).joinToString(" ")
    }
    val first = text.lineSequence().map { it.trim() }.first { it.isNotEmpty() }
    if (first.length <= MAX_FAILURE_CHARS) return first
    val cut = first.lastIndexOf(' ', MAX_FAILURE_CHARS).takeIf { it > MAX_FAILURE_CHARS / 2 } ?: MAX_FAILURE_CHARS
    return first.substring(0, cut).trimEnd(',', ';', ':', ' ') + "…"
}

private fun resetPhrase(text: String, locale: Locale, zone: ZoneId): String? {
    REGAIN_ON.find(text)?.let { m ->
        val date = runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull() ?: return@let
        // A UTC time can fall on another local day.
        val local = m.groupValues[2].takeIf { it.isNotEmpty() }
            ?.let { t -> runCatching { date.atTime(LocalTime.parse(t.padStart(5, '0'))) }.getOrNull() }
            ?.atOffset(ZoneOffset.UTC)?.atZoneSameInstant(zone)?.toLocalDate()
            ?: date
        return "Resets ${local.format(DateTimeFormatter.ofPattern("d MMM", locale))}."
    }
    RESETS_IN.find(text)?.let { return "Resets in ${it.groupValues[1].trim()}." }
    RESETS_AT.find(text)?.let { return "Resets at ${it.groupValues[1]}." }
    RESETS_ON.find(text)?.let { return "Resets ${it.groupValues[1].trimEnd('.')}." }
    return null
}

/**
 * "Answered by <model>" when the model that served a reply ([served], from the run's runtime)
 * differs from the one the chat expected ([expected]); null when they match or either is
 * unknown. Ids compare without case and without a "provider/" prefix.
 */
fun answeredByLabel(served: String?, expected: String?): String? {
    val s = served?.trim().orEmpty()
    val e = expected?.trim().orEmpty()
    if (s.isEmpty() || e.isEmpty()) return null
    fun bare(id: String) = id.substringAfterLast('/').lowercase()
    return if (bare(s) == bare(e)) null else "Answered by ${s.substringAfterLast('/')}"
}

/**
 * The jump button under a chat: "New reply ↓" when something landed below while you read
 * further up, "Latest ↓" when you've only scrolled up, nothing at the bottom.
 */
fun latestButtonLabel(atBottom: Boolean, unseenBelow: Boolean): String? = when {
    atBottom -> null
    unseenBelow -> "New reply ↓"
    else -> "Latest ↓"
}
