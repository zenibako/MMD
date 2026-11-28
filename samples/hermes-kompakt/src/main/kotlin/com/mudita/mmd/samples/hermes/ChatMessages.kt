package com.mudita.mmd.samples.hermes

/** What a history entry is, for how the chat shows it. */
enum class MessageKind {
    /** What you typed and what the agent answered. Always shown. */
    CONVERSATION,

    /** The agent calling tools ([ChatMessage.tools] names them). Summarized in one line by default. */
    TOOL_CALL,

    /** A tool's result. Shown only with "Show tool and system messages". */
    TOOL_RESULT,

    /** System prompts, status and other non-conversation events. Shown only with the switch. */
    SYSTEM,
}

/** `display_kind` values that still mean an ordinary message. */
private val PLAIN_DISPLAY_KINDS = setOf("", "message", "text", "chat")

/**
 * Turns one `/messages` entry into what the chat shows. Grouping:
 * - another agent's message (see AgentMessages.kt) is [MessageKind.CONVERSATION] with role
 *   [AGENT_ROLE] and its sender;
 * - `user` / `assistant` text is [MessageKind.CONVERSATION];
 * - an `assistant` entry's tool calls become a [MessageKind.TOOL_CALL] (after its text, if any);
 * - `tool` entries are [MessageKind.TOOL_RESULT];
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
    // Messages from other agents: a stamped DM, a DM's reply, or a subagent's result. Shown as
    // received under the agent's name; Hermes's framing is dropped.
    if (r == "user") {
        val fromAgent = when (kind) {
            in PLAIN_DISPLAY_KINDS -> parseStampedMessage(body)
            "process_complete" -> parseAgentReply(body)
            "async_delegation_complete" -> parseDelegationResult(body)
            else -> null
        }
        if (fromAgent != null) return listOf(ChatMessage(AGENT_ROLE, fromAgent.text, time, sender = fromAgent.sender))
    }
    if (kind !in PLAIN_DISPLAY_KINDS && r != "tool") {
        return listOfNotNull(body.takeIf { it.isNotEmpty() }?.let { ChatMessage(r, it, time, MessageKind.SYSTEM, label = kind) })
    }
    return when (r) {
        "user" -> listOfNotNull(body.takeIf { it.isNotEmpty() }?.let { ChatMessage("user", it, time) })
        "assistant" -> listOfNotNull(
            body.takeIf { it.isNotEmpty() }?.let { ChatMessage("assistant", it, time) },
            toolCallEntry(toolCalls, time),
        )
        "tool" -> listOf(
            ChatMessage("tool", body.ifEmpty { "(no output)" }, time, MessageKind.TOOL_RESULT, label = toolName.ifBlank { "Tool" }),
        )
        else -> listOfNotNull(body.takeIf { it.isNotEmpty() }?.let { ChatMessage(r, it, time, MessageKind.SYSTEM, label = r) })
    }
}

/** A [MessageKind.TOOL_CALL] entry for [names] (deduped, in first-use order), or null if there are none. */
fun toolCallEntry(names: List<String>, time: Long?): ChatMessage? {
    val tools = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (tools.isEmpty()) return null
    return ChatMessage("assistant", "Called ${tools.joinToString(", ")}", time, MessageKind.TOOL_CALL, label = "Tool call", tools = tools)
}

/** One-line, high-level summary of tool use, e.g. "Used web_search, read_file". */
fun toolSummary(tools: List<String>): String = "Used ${tools.distinct().joinToString(", ")}"

/**
 * What the chat shows. With [showTools] off: the conversation, plus one summary line wherever
 * the agent used tools between two messages (consecutive calls merged and deduped; results and
 * system entries hidden). With it on: every entry, in order.
 */
fun visibleMessages(all: List<ChatMessage>, showTools: Boolean): List<ChatMessage> {
    if (showTools) return all
    val out = mutableListOf<ChatMessage>()
    for (m in all) {
        when (m.kind) {
            MessageKind.CONVERSATION -> out += m
            MessageKind.TOOL_CALL -> {
                val prev = out.lastOrNull()
                if (prev != null && prev.kind == MessageKind.TOOL_CALL) {
                    val tools = (prev.tools + m.tools).distinct()
                    out[out.lastIndex] = prev.copy(tools = tools, text = toolSummary(tools))
                } else {
                    out += m.copy(text = toolSummary(m.tools))
                }
            }
            MessageKind.TOOL_RESULT, MessageKind.SYSTEM -> Unit
        }
    }
    return out
}
