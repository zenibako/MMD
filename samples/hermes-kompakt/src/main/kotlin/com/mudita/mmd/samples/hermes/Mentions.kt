package com.mudita.mmd.samples.hermes

/**
 * @mentions in the compose bar, for chats where Hermes routes them: a bot room (the server
 * sends the turn to the members named, or to everyone when none is) and a profile's Bot Chat
 * (its agent DMs the named teammate with message_agent). A handle is a profile's name on the
 * server; the default profile answers to @hermes (tools/bot_mode_probe.py).
 */
data class MentionCandidate(val handle: String, val name: String)

/** The @word being typed at the cursor: where its "@" is and the letters after it. */
data class MentionQuery(val start: Int, val prefix: String)

/** Characters Hermes reads as part of a handle (gateway/hosted_room_discussion.py _MENTION_RE). */
private fun isHandleChar(c: Char) = c.isLetterOrDigit() || c in "._:-"

/** Handles that mean every member of a bot room. */
val ROOM_WIDE_HANDLES = setOf("all", "everyone")

/** Whether Hermes routes @mentions in this session: bot rooms and the canonical Bot Chat. */
fun mentionsRoute(source: String, title: String): Boolean =
    source == "bot_room" || (source == "tui" && title.trim() == BOT_CHAT_TITLE)

/** The mention handles for the phone's profiles: the slug, with the default profile as @hermes. */
fun profileCandidates(profiles: List<Pair<String, String>>): List<MentionCandidate> =
    profiles.map { (slug, name) -> MentionCandidate(if (slug.isEmpty()) "hermes" else slug, name) }
        .distinctBy { it.handle }

private val STAMPED_HANDLE = Regex("""Message from 🤖 (.+?) \(@([A-Za-z0-9_-]+)\)""")

/** Agents that wrote into this chat ("Message from 🤖 Name (@handle)"), as more candidates. */
fun handlesSeenIn(texts: List<String>): List<MentionCandidate> =
    texts.flatMap { t -> STAMPED_HANDLE.findAll(t).map { MentionCandidate(it.groupValues[2], it.groupValues[1]) }.toList() }
        .distinctBy { it.handle }

/**
 * The @word ending at [cursor] in [text], if one is being typed: an "@" at the start or after a
 * space or bracket, then handle characters up to the cursor, outside `code`.
 */
fun activeMentionQuery(text: String, cursor: Int): MentionQuery? {
    if (cursor < 1 || cursor > text.length) return null
    var i = cursor - 1
    while (i >= 0 && isHandleChar(text[i])) i--
    if (i < 0 || text[i] != '@') return null
    if (i > 0 && !(text[i - 1].isWhitespace() || text[i - 1] in "([{\"'")) return null
    if (insideCode(text, i)) return null
    return MentionQuery(i, text.substring(i + 1, cursor))
}

/** Whether [index] falls inside inline `code` or a ``` block. */
private fun insideCode(text: String, index: Int): Boolean {
    val before = text.substring(0, index)
    val fences = Regex("```").findAll(before).count()
    if (fences % 2 == 1) return true
    val inline = before.replace("```", "").count { it == '`' }
    return inline % 2 == 1
}

/**
 * Up to [limit] candidates for [prefix]: handles starting with it first, then display names
 * with a word starting with it; case doesn't matter. An empty prefix lists them all.
 */
fun mentionSuggestions(prefix: String, candidates: List<MentionCandidate>, limit: Int = 4): List<MentionCandidate> {
    val p = prefix.lowercase()
    val byHandle = candidates.filter { it.handle.lowercase().startsWith(p) }
    val byName = candidates.filter { c -> c !in byHandle && c.name.lowercase().split(' ', '-', '_').any { it.startsWith(p) } }
    return (byHandle + byName).take(limit)
}

/** [text] with the @word at [query] replaced by "@handle " and the new cursor position. */
fun applyMention(text: String, query: MentionQuery, cursor: Int, handle: String): Pair<String, Int> {
    val inserted = "@$handle "
    val rest = text.substring(cursor).let { if (it.startsWith(" ")) it.drop(1) else it }
    return text.substring(0, query.start) + inserted + rest to query.start + inserted.length
}

/** A mention Hermes won't recognise, and the handle it probably meant ("@lead-dev." → lead-dev). */
data class MentionIssue(val written: String, val meant: String?)

/** Every @handle in [text] as Hermes reads it (with trailing . : -), outside code and emails. */
fun mentionsIn(text: String): List<Pair<IntRange, String>> {
    val out = mutableListOf<Pair<IntRange, String>>()
    var i = 0
    while (i < text.length) {
        if (text[i] == '@' && (i == 0 || !(text[i - 1].isLetterOrDigit() || text[i - 1] in "@._")) && !insideCode(text, i)) {
            var j = i + 1
            while (j < text.length && isHandleChar(text[j])) j++
            if (j > i + 1 && text[i + 1].isLetterOrDigit()) out += (i until j) to text.substring(i + 1, j)
            i = j
        } else {
            i++
        }
    }
    return out
}

/**
 * The mentions in [text] that name nobody in [known] (handles, case-insensitive), each with the
 * known handle it most likely meant: the same name without trailing punctuation, which Hermes
 * would otherwise read as part of the handle.
 */
fun unknownMentions(text: String, known: Set<String>): List<MentionIssue> {
    val lower = known.map { it.lowercase() }.toSet()
    return mentionsIn(text).map { it.second }.distinct().filter { it.lowercase() !in lower }.map { written ->
        val trimmed = written.trimEnd('.', ':', '-')
        MentionIssue(written, trimmed.takeIf { it != written && it.lowercase() in lower })
    }
}

/** The one-line notice for [issues] under the compose bar. */
fun mentionNotice(issues: List<MentionIssue>): String {
    val first = issues.first()
    val more = if (issues.size > 1) " (and ${issues.size - 1} more)" else ""
    return if (first.meant != null) {
        "Hermes would read \"@${first.written}\" as another name; put a space after @${first.meant}.$more"
    } else {
        "@${first.written} isn't a bot in this chat.$more"
    }
}
