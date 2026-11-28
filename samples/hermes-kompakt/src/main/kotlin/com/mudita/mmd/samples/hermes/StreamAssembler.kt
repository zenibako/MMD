package com.mudita.mmd.samples.hermes

import org.json.JSONObject

/**
 * Turns the session stream's events into [ChatEvent]s for one turn. Once a terminal event
 * (run.completed, run.failed, error, …) has been emitted it is [finished] and ignores the rest,
 * so a repeated run.completed can't add a second reply; and the first non-empty
 * assistant.completed is kept, so a repeat can't replace the final text.
 */
class StreamAssembler {
    private val text = StringBuilder()
    private var completed = ""

    var finished = false
        private set

    /** The [ChatEvent]s for one SSE event named [event] (or its `type`) with payload [data]. */
    fun accept(event: String, data: JSONObject): List<ChatEvent> {
        if (finished) return emptyList()
        val name = event.ifBlank { data.str("type", "event") }
        return when (name) {
            "assistant.delta", "message.delta", "response.output_text.delta" -> {
                val d = data.str("delta", "text", "content")
                text.append(d)
                listOf(ChatEvent.Delta(d))
            }
            // The session stream names the tool in `tool_name` (Hermes 0.23), older frames in `name`.
            "tool.started", "tool.completed", "tool.failed" -> {
                val tool = data.str("tool_name", "name", "tool")
                if (tool.isEmpty()) emptyList() else listOf(ChatEvent.Tool(tool, name.substringAfter('.')))
            }
            // Named progress frames (platforms.api_server.tool_progress_events).
            "hermes.tool.progress" -> listOf(
                ChatEvent.Tool(
                    name = data.str("name", "tool", "tool_name").ifBlank { "tool" },
                    phase = "progress",
                    detail = data.str("message", "status", "summary", "label"),
                ),
            )
            // A tool call waiting on you (Hermes 0.23: answered at POST /v1/runs/{id}/approval).
            "approval.request" -> listOfNotNull(parseApproval(data)?.let { ChatEvent.Approval(it) })
            // The whole reply (or, for a failed turn, Hermes's account of the failure).
            "assistant.completed" -> {
                if (completed.isEmpty()) completed = data.str("content", "text")
                emptyList()
            }
            "run.completed" -> {
                finished = true
                val final = data.str("output", "final", "text", "content")
                    .ifBlank { completed }.ifBlank { text.toString() }
                val served = data.optJSONObject("runtime")?.str("model")?.ifBlank { null }
                listOf(ChatEvent.Done(final, served))
            }
            "run.failed", "run.cancelled", "run.interrupted", "error" -> {
                finished = true
                val reason = (data.optJSONObject("error") ?: data.optJSONObject("run")?.optJSONObject("error"))
                    ?.str("message", "detail", "type")
                    ?: data.str("error", "message", "reason", "detail")
                listOf(
                    ChatEvent.Failed(
                        reason.ifBlank { completed }.ifBlank { data.str("turn_exit_reason") }
                            .ifBlank { name.substringAfter('.') },
                    ),
                )
            }
            else -> emptyList()
        }
    }
}
