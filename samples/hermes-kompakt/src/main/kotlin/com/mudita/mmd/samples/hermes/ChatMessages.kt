package com.mudita.mmd.samples.hermes

/** What a history entry is, for the chat's "Show tool and system messages" switch. */
enum class MessageKind {
    /** What you typed and what the agent answered. Always shown. */
    CONVERSATION,

    /** The agent calling a tool, and the tool's result. */
    TOOL,

    /** System prompts, status and other non-conversation events. */
    SYSTEM,
}

/** `display_kind` values that still mean an ordinary message. */
private val PLAIN_DISPLAY_KINDS = setOf("", "message", "text", "chat")

/**
 * Turns one `/messages` entry into what the chat shows. Grouping:
 * - `user` / `assistant` text is [MessageKind.CONVERSATION];
 * - an `assistant` entry's tool calls become a [MessageKind.TOOL] line (after its text, if any),
 *   and `tool` entries (results) are [MessageKind.TOOL];
 * - `system` and any other role, or a non-plain `display_kind`, is [MessageKind.SYSTEM].
 * Entries with nothing to show are dropped.
 */
fun historyEntries(
    role: String,
    text: String,
    time: Long?,
    toolName: String = "",
    toolCalls: List<String> = emptyList(),
    displayKind: String = "",
): List<ChatMessage> {
    val r = role.trim().lowercase()
    val body = text.trim()
    val kind = displayKind.trim().lowercase()
    if (kind !in PLAIN_DISPLAY_KINDS && r != "tool") {
        return listOfNotNull(body.takeIf { it.isNotEmpty() }?.let { ChatMessage(r, it, time, MessageKind.SYSTEM, label = kind) })
    }
    return when (r) {
        "user" -> listOfNotNull(body.takeIf { it.isNotEmpty() }?.let { ChatMessage("user", it, time) })
        "assistant" -> listOfNotNull(
            body.takeIf { it.isNotEmpty() }?.let { ChatMessage("assistant", it, time) },
            toolCalls.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let {
                ChatMessage("assistant", "Called ${it.joinToString(", ")}", time, MessageKind.TOOL, label = "Tool call")
            },
        )
        "tool" -> listOf(
            ChatMessage("tool", body.ifEmpty { "(no output)" }, time, MessageKind.TOOL, label = toolName.ifBlank { "Tool" }),
        )
        else -> listOfNotNull(body.takeIf { it.isNotEmpty() }?.let { ChatMessage(r, it, time, MessageKind.SYSTEM, label = r) })
    }
}

/** The messages to show: the conversation only, or everything when [showTools] is on. */
fun visibleMessages(all: List<ChatMessage>, showTools: Boolean): List<ChatMessage> =
    if (showTools) all else all.filter { it.kind == MessageKind.CONVERSATION }
