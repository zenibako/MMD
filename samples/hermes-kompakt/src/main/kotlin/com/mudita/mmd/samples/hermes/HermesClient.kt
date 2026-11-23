package com.mudita.mmd.samples.hermes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SessionSummary(
    val id: String,
    val title: String,
    val updated: String,
    val source: String,
)

data class ChatMessage(
    val role: String,
    val text: String,
)

/** One event from `POST /api/sessions/{id}/chat/stream`. */
sealed interface ChatEvent {
    data class Delta(val text: String) : ChatEvent
    data class Tool(val name: String, val phase: String) : ChatEvent
    data class Done(val finalText: String?) : ChatEvent
    data class Failed(val reason: String) : ChatEvent
}

class HermesApiException(val code: Int, message: String) : IOException(message)

/**
 * Minimal client for the Hermes Agent API server (`hermes gateway` with
 * `API_SERVER_ENABLED=true`). Uses only the platform HTTP stack and org.json so the
 * sample carries no extra dependencies.
 *
 * Endpoint shapes follow https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server.
 * Response field names are parsed defensively because the session endpoints are not yet
 * documented with full JSON examples.
 */
class HermesClient(private val settings: HermesSettings) {

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        runCatching { request("GET", "/health", auth = false).first in 200..299 }.getOrDefault(false)
    }

    suspend fun listSessions(limit: Int = 30): List<SessionSummary> = withContext(Dispatchers.IO) {
        val body = requestOk("GET", "/api/sessions?limit=$limit")
        arrayIn(body, "sessions", "data", "items").objects().map { o ->
            SessionSummary(
                id = o.str("id", "session_id"),
                title = o.str("title", "name").ifBlank { "(untitled)" },
                updated = o.str("updated_at", "last_active", "last_activity", "created_at"),
                source = o.str("source", "platform"),
            )
        }.filter { it.id.isNotBlank() }
    }

    suspend fun createSession(title: String): String = withContext(Dispatchers.IO) {
        val body = requestOk("POST", "/api/sessions", JSONObject().put("title", title))
        val o = JSONObject(body)
        o.optJSONObject("session")?.str("id", "session_id")?.takeIf { it.isNotBlank() }
            ?: o.str("id", "session_id")
    }

    suspend fun messages(sessionId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val body = requestOk("GET", "/api/sessions/${enc(sessionId)}/messages")
        arrayIn(body, "messages", "data", "items").objects()
            .map { ChatMessage(it.str("role"), contentText(it.opt("content"))) }
            .filter { it.role in setOf("user", "assistant") && it.text.isNotBlank() }
    }

    /**
     * Sends [input] to the session and streams the reply. On E Ink we deliberately do not
     * redraw per token: callers should collect into a buffer and render on [ChatEvent.Done]
     * (or at a slow, fixed cadence).
     */
    fun chatStream(sessionId: String, input: String): Flow<ChatEvent> = flow {
        val conn = open("POST", "/api/sessions/${enc(sessionId)}/chat/stream")
        conn.setRequestProperty("Accept", "text/event-stream")
        conn.readTimeout = 0 // server sends `: keepalive` every 10 s; the run can be long.
        writeJson(conn, JSONObject().put("input", input))
        val code = conn.responseCode
        if (code !in 200..299) {
            throw HermesApiException(code, conn.errorStream?.bufferedReader()?.readText().orEmpty())
        }
        val text = StringBuilder()
        conn.inputStream.bufferedReader().use { reader ->
            for (e in reader.sseEvents()) {
                val data = runCatching { JSONObject(e.data) }.getOrNull() ?: JSONObject()
                when (e.event.ifBlank { data.str("type", "event") }) {
                    "assistant.delta", "message.delta", "response.output_text.delta" -> {
                        val d = data.str("delta", "text", "content")
                        text.append(d)
                        emit(ChatEvent.Delta(d))
                    }
                    "tool.started", "tool.completed", "tool.failed" ->
                        emit(ChatEvent.Tool(data.str("name", "tool"), e.event.substringAfter('.')))
                    "run.completed" -> {
                        val final = data.str("output", "final", "text", "content").ifBlank { text.toString() }
                        emit(ChatEvent.Done(final))
                        return@use
                    }
                    "run.failed", "run.cancelled", "run.interrupted" -> {
                        emit(ChatEvent.Failed(data.str("error", "message", "reason").ifBlank { e.event }))
                        return@use
                    }
                }
            }
            // Stream ended without a terminal event.
            emit(ChatEvent.Done(text.toString().ifBlank { null }))
        }
        conn.disconnect()
    }.flowOn(Dispatchers.IO)

    // --- HTTP plumbing --------------------------------------------------------------

    private fun open(method: String, path: String, auth: Boolean = true): HttpURLConnection =
        (URL(settings.baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            if (auth) setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
        }

    private fun writeJson(conn: HttpURLConnection, body: JSONObject) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: Boolean = true,
    ): Pair<Int, String> {
        val conn = open(method, path, auth)
        try {
            if (body != null) writeJson(conn, body)
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return code to (stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty())
        } finally {
            conn.disconnect()
        }
    }

    private fun requestOk(method: String, path: String, body: JSONObject? = null): String {
        val (code, text) = request(method, path, body)
        if (code !in 200..299) throw HermesApiException(code, "HTTP $code on $path: ${text.take(200)}")
        return text
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

// --- JSON helpers -------------------------------------------------------------------

internal fun JSONObject.str(vararg keys: String): String =
    keys.firstNotNullOfOrNull { k -> optString(k, "").takeIf { has(k) && !isNull(k) && it.isNotEmpty() } }
        .orEmpty()

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

/** Accepts either a bare JSON array or an object wrapping one under any of [keys]. */
internal fun arrayIn(body: String, vararg keys: String): JSONArray {
    val trimmed = body.trimStart()
    if (trimmed.startsWith("[")) return JSONArray(trimmed)
    val o = JSONObject(trimmed)
    return keys.firstNotNullOfOrNull { o.optJSONArray(it) } ?: JSONArray()
}

/** Message content may be a string or an OpenAI-style list of parts. */
internal fun contentText(content: Any?): String = when (content) {
    is String -> content
    is JSONArray -> content.objects().joinToString("") { it.str("text") }
    is JSONObject -> content.str("text")
    else -> ""
}
