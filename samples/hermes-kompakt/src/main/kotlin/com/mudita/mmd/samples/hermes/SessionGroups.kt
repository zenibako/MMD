package com.mudita.mmd.samples.hermes

/** Kinds of session for the list filter, from where each one started. */
enum class SessionGroup(val label: String) {
    CHATS("Your chats"),
    BOT("Bot Chat"),
    SCHEDULED("Scheduled (cron)"),
    KANBAN("Kanban tasks"),
    MESSAGING("Messaging and integrations"),
    OTHER("Other (terminal, CLI)"),
}

/** Sources that are a person at a terminal or another Hermes client, not a chat app. */
private val OTHER_SOURCES = setOf("tui", "cli", "acp", "desktop", "web", "webui", "subagent")

/**
 * The group a session belongs to. The canonical Bot Chat and bot rooms are [SessionGroup.BOT];
 * `api_server` (sessions made here or by other API clients) is [SessionGroup.CHATS]; `cron` and
 * `kanban` have their own groups; terminal and CLI sessions are [SessionGroup.OTHER]; any other
 * source (Telegram, Home Assistant, email, …) is [SessionGroup.MESSAGING].
 */
fun sessionGroup(s: SessionSummary): SessionGroup {
    val source = s.source.trim().lowercase()
    return when {
        s.title.trim() == BOT_CHAT_TITLE || source == "bot_room" -> SessionGroup.BOT
        source == "api_server" || source.isEmpty() -> SessionGroup.CHATS
        source == "cron" || source == "schedule" || source == "scheduler" -> SessionGroup.SCHEDULED
        source == "kanban" -> SessionGroup.KANBAN
        source in OTHER_SOURCES -> SessionGroup.OTHER
        else -> SessionGroup.MESSAGING
    }
}

/** The sessions whose group isn't in [hidden]; pinned ones follow the same rule. */
fun filterSessions(sessions: List<SessionSummary>, hidden: Set<SessionGroup>): List<SessionSummary> =
    if (hidden.isEmpty()) sessions else sessions.filter { sessionGroup(it) !in hidden }
