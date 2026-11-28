package com.mudita.mmd.samples.hermes

/** Title of the hidden, canonical bot session (`hermes peer dm`). */
const val BOT_CHAT_TITLE = "Bot Chat"

/** What the app shows for a session Hermes hasn't named yet. */
const val UNTITLED = "New chat"

/** A session's title for display: Hermes's title, or [UNTITLED] until it has one. */
fun sessionTitleOrPlaceholder(title: String): String = title.trim().ifEmpty { UNTITLED }

/**
 * Orders the session list: pinned sessions first (in Hermes's order), then [botChat] if it
 * isn't already there, then the rest. Each session appears once.
 */
fun arrangeSessions(recent: List<SessionSummary>, botChat: SessionSummary?): List<SessionSummary> {
    val all = (recent + listOfNotNull(botChat)).distinctBy { it.id }
    val pinned = all.filter { it.pinned }
    val bot = botChat?.takeIf { b -> pinned.none { it.id == b.id } }
    val rest = all.filter { !it.pinned && it.id != bot?.id }
    return pinned + listOfNotNull(bot) + rest
}
