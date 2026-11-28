package com.mudita.mmd.samples.hermes

/**
 * A message another agent sent into this chat: shown as received, under [sender]'s name, with
 * Hermes's own framing (stamp, notification wrapper) removed from [text].
 */
data class AgentMessage(val sender: String, val text: String)

/** Role used for [AgentMessage]s in the chat: received, like the agent's own replies. */
const val AGENT_ROLE = "agent"

/**
 * `message_agent` deliveries arrive as a user turn stamped by the server (tools/bot_mode_dm.py):
 * `Message from 🤖 <Name> (@<handle>): <text>`.
 */
private val STAMP = Regex("""^Message from 🤖 (.+?) \(@([A-Za-z0-9_-]+)\): ?""", RegexOption.DOT_MATCHES_ALL)

/** The stamped message in [content], or null if it isn't one. */
fun parseStampedMessage(content: String): AgentMessage? {
    val m = STAMP.find(content.trimStart()) ?: return null
    val body = content.trimStart().substring(m.range.last + 1).trim()
    return AgentMessage(m.groupValues[1].trim(), body)
}

/** One finished background process (tools/process_registry_notifications.format_process_notification). */
private val COMPLETED_HEAD = Regex("""^\[IMPORTANT: Background process \S+ completed\b""")

/** `--run-delivery … hermes -p <profile> chat …`: the profile that answered. */
private val DELIVERY_PROFILE = Regex("""\s-p\s+'?([A-Za-z0-9_-]+)'?""")

/** `--wait-reply <file> '<label>' <budget>`: a relayed agent on another machine. */
private val WAIT_REPLY_LABEL = Regex("""--wait-reply\s+\S+\s+(?:'([^']+)'|"([^"]+)"|(\S+))""")

/**
 * The reply another agent sent back to a `message_agent` DM. Hermes stores it as a
 * `process_complete` notice: `[IMPORTANT: Background process <id> completed …]` with a
 * `Command:` line naming the delivery (`bot_mode_dm.py --run-delivery … -p <profile>`, or
 * `--wait-reply … '<label>'` for a relay) and the reply under `Output:`. Null for anything
 * else: other background processes, several at once, or a delivery that failed.
 */
fun parseAgentReply(content: String): AgentMessage? {
    if (!COMPLETED_HEAD.containsMatchIn(content)) return null
    val command = content.lineSequence().firstOrNull { it.startsWith("Command: ") }?.removePrefix("Command: ") ?: return null
    if ("bot_mode_dm" !in command) return null
    val sender = when {
        "--run-delivery" in command ->
            DELIVERY_PROFILE.findAll(command).lastOrNull()?.groupValues?.get(1)?.let { displayNameFor(it) }
        "--wait-reply" in command ->
            WAIT_REPLY_LABEL.find(command)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
        else -> null
    } ?: return null
    val marker = "\nOutput:\n"
    val at = content.indexOf(marker)
    if (at < 0) return null
    var body = content.substring(at + marker.length).trimEnd()
    // The notice closes the "[IMPORTANT: …" bracket after the output.
    if (body.endsWith("]")) body = body.dropLast(1).trimEnd()
    // A relay's waiter prints "Reply from <label>:" before the reply.
    body = body.removePrefix("Reply from $sender:").trim()
    if (body.isEmpty()) return null
    return AgentMessage(sender, body)
}

/**
 * A subagent's result from an async delegation (`[ASYNC DELEGATION COMPLETE — <id>]`): the text
 * after `--- RESULT ---`, under "Subagent". Null without a result (failures, batches).
 */
fun parseDelegationResult(content: String): AgentMessage? {
    if (!content.trimStart().startsWith("[ASYNC DELEGATION COMPLETE")) return null
    val marker = "--- RESULT ---"
    val at = content.indexOf(marker)
    if (at < 0) return null
    val body = content.substring(at + marker.length).trim()
    return body.takeIf { it.isNotEmpty() }?.let { AgentMessage("Subagent", it) }
}

/** An @handle: not inside a word or an email address. */
private val MENTION = Regex("""(?<![\w@.])@[A-Za-z][A-Za-z0-9_-]*""")

/** Where [text] mentions a handle (`@lead-dev`), for drawing them bold. */
fun mentionRanges(text: String): List<IntRange> = MENTION.findAll(text).map { it.range }.toList()

/** The failure line for a turn Hermes refused or that never reached it. */
fun sendFailureLine(e: Throwable): String = when {
    e is HermesApiException && e.code == 409 && apiErrorCode(e.message.orEmpty()) == "session_active_turn" ->
        "Hermes is still answering this chat elsewhere. Try again in a moment."
    e is HermesApiException && e.code == 409 ->
        "Hermes refused the message: " + runFailureLine(apiErrorMessage(e.message.orEmpty()) ?: "conflict")
    else -> connectionError("Couldn't send", e)
}

private val MESSAGE_FIELD = Regex(""""(?:message|error)"\s*:\s*"([^"]+)"""")

/** The human-readable text of a Hermes error body (`error.message`, or a string `error`). */
fun apiErrorMessage(body: String): String? = MESSAGE_FIELD.find(body)?.groupValues?.get(1)
