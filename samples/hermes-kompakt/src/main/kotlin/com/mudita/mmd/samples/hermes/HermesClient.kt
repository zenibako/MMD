package com.mudita.mmd.samples.hermes

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
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
    /** A message preview, if Hermes includes one in the list (field names vary). */
    val preview: String = "",
) {
    val isReadOnly: Boolean get() = isReadOnlySource(source)
}

/** Hermes lets API clients continue only sessions from these sources; the rest are read-only. */
fun isReadOnlySource(source: String): Boolean = source.isNotBlank() && source !in setOf("api_server", "bot_room")

data class ChatMessage(
    val role: String,
    val text: String,
    /** When it was sent, epoch millis; null if Hermes didn't say. */
    val time: Long? = null,
)

/** One event from `POST /api/sessions/{id}/chat/stream`. */
sealed interface ChatEvent {
    data class Delta(val text: String) : ChatEvent
    data class Tool(val name: String, val phase: String, val detail: String = "") : ChatEvent
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
class HermesClient(
    private val settings: HermesSettings,
    /** Profile whose routes and key are used; the default profile uses the root routes. */
    val profile: HermesProfile = settings.active,
) {
    private val prefix = if (profile.isDefault) "" else "/p/${URLEncoder.encode(profile.slug, "UTF-8")}"

    /** Calls `GET /health` and returns "OK", "HTTP <code>" or the exception, for display. */
    suspend fun healthCheck(): String = withContext(Dispatchers.IO) {
        val result = runCatching { request("GET", "/health", auth = false).first }.fold(
            onSuccess = { code -> if (code in 200..299) "OK" else "HTTP $code" },
            onFailure = { e -> "${e.javaClass.simpleName}: ${e.message}" },
        )
        if (result != "OK") Log.w("Hermes", "Health check against ${normalizeUrl(settings.baseUrl)} failed: $result")
        result
    }

    /**
     * The profile's name, from the model id `GET /v1/models` reports. Hermes sets that id to
     * the profile name (`API_SERVER_MODEL_NAME` overrides it), or `hermes-agent` for the
     * default profile, which is shown as "Hermes". Null if it can't be fetched.
     */
    suspend fun profileName(): String? = withContext(Dispatchers.IO) {
        ProfileNames.names[profile.slug]?.let { return@withContext it }
        runCatching {
            val id = arrayIn(requestOk("GET", "/v1/models"), "data").objects().firstOrNull()?.str("id").orEmpty()
            when {
                id.isBlank() -> null
                id == "hermes-agent" -> "Hermes"
                else -> id
            }
        }.onSuccess { name -> name?.let { ProfileNames.names[profile.slug] = it } }.onFailure { Log.w("Hermes", "Could not read profile name: $it") }.getOrNull()
    }

    suspend fun listSessions(limit: Int = 30): List<SessionSummary> = withContext(Dispatchers.IO) {
        val body = requestOk("GET", "/api/sessions?limit=$limit")
        arrayIn(body, "sessions", "data", "items").objects().map { o ->
            SessionSummary(
                id = o.str("id", "session_id"),
                title = o.str("title", "name").ifBlank { "(untitled)" },
                updated = o.str("updated_at", "last_active", "last_activity", "created_at"),
                source = o.str("source", "platform"),
                preview = o.str("preview", "last_message", "snippet", "summary")
                    .ifBlank { o.optJSONObject("last_message")?.let { contentText(it.opt("content")) }.orEmpty() },
            )
        }.filter { it.id.isNotBlank() }
    }

    /**
     * Copies a session into a new API-owned one. Hermes only lets the API continue sessions
     * whose source is `api_server` or `bot_room`; forking is how a cron, kanban or messaging
     * conversation is carried on from the phone.
     */
    suspend fun forkSession(sessionId: String, title: String): String = withContext(Dispatchers.IO) {
        val body = requestOk("POST", "/api/sessions/${enc(sessionId)}/fork", JSONObject().put("title", title))
        val o = JSONObject(body)
        (o.optJSONObject("session")?.str("id", "session_id")?.takeIf { it.isNotBlank() } ?: o.str("id", "session_id", "new_session_id"))
            .ifBlank { throw IOException("Fork response had no session id: ${body.take(200)}") }
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
            .map { ChatMessage(it.str("role"), contentText(it.opt("content")), epochMillis(it.str("timestamp", "created_at"))) }
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
                Log.d("Hermes", "SSE ${e.event.ifBlank { "(message)" }}: ${e.data.take(300)}")
                val data = runCatching { JSONObject(e.data) }.getOrNull() ?: JSONObject()
                when (e.event.ifBlank { data.str("type", "event") }) {
                    "assistant.delta", "message.delta", "response.output_text.delta" -> {
                        val d = data.str("delta", "text", "content")
                        text.append(d)
                        emit(ChatEvent.Delta(d))
                    }
                    "tool.started", "tool.completed", "tool.failed" ->
                        emit(ChatEvent.Tool(data.str("name", "tool"), e.event.substringAfter('.')))
                    // Named progress frames (platforms.api_server.tool_progress_events).
                    "hermes.tool.progress" -> emit(
                        ChatEvent.Tool(
                            name = data.str("name", "tool", "tool_name").ifBlank { "tool" },
                            phase = "progress",
                            detail = data.str("message", "status", "summary", "label"),
                        ),
                    )
                    "run.completed" -> {
                        val final = data.str("output", "final", "text", "content").ifBlank { text.toString() }
                        emit(ChatEvent.Done(final))
                        return@use
                    }
                    "run.failed", "run.cancelled", "run.interrupted" -> {
                        Log.w("Hermes", "Run ended with ${e.event}: ${e.data.take(1000)}")
                        val reason = (data.optJSONObject("error") ?: data.optJSONObject("run")?.optJSONObject("error"))
                            ?.str("message", "detail", "type")
                            ?: data.str("error", "message", "reason", "detail")
                        emit(ChatEvent.Failed(reason.ifBlank { "${e.event} ${e.data.take(300)}" }))
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
        // /health is served once at the root; everything else is per profile.
        (URL(normalizeUrl(settings.baseUrl) + (if (path == "/health") path else prefix + path)).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            if (auth) setRequestProperty("Authorization", "Bearer ${profile.apiKey}")
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

/** Display names fetched from each profile's `/v1/models`, keyed by slug, for this process. */
object ProfileNames {
    val names = mutableStateMapOf<String, String>()

    fun label(profile: HermesProfile): String = names[profile.slug] ?: profile.slug.ifEmpty { "Default" }
}

/** Hermes sends times as epoch seconds, sometimes fractional (`1795466275.71`). */
internal fun epochMillis(raw: String): Long? = raw.toDoubleOrNull()?.let { (it * 1000).toLong() }

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
