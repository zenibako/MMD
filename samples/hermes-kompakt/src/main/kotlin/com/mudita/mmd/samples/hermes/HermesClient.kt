package com.mudita.mmd.samples.hermes

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
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
    /** Pinned on the server (the same flag the Hermes dashboard's sidebar uses). */
    val pinned: Boolean = false,
) {
    val isReadOnly: Boolean get() = isReadOnlySession(source, title)
}

/**
 * Whether the app offers a compose bar for a session. API and bot-room sessions take turns from
 * the API, and since Hermes 0.23.4 so does a profile's canonical Bot Chat (a `tui` session): a
 * turn sent there goes to the live Bot Chat, as `hermes peer dm` does. Other terminal, cron,
 * kanban and messaging sessions stay read-only here ("Continue here" copies them).
 */
fun isReadOnlySession(source: String, title: String): Boolean = when {
    source.isBlank() || source in setOf("api_server", "bot_room") -> false
    source == "tui" && title.trim() == BOT_CHAT_TITLE -> false
    else -> true
}

data class ChatMessage(
    val role: String,
    val text: String,
    /** When it was sent, epoch millis; null if Hermes didn't say. */
    val time: Long? = null,
    /** Conversation, or a tool/system entry the chat can hide. */
    val kind: MessageKind = MessageKind.CONVERSATION,
    /** Short heading for tool and system entries, e.g. the tool's name. */
    val label: String = "",
    /** For [MessageKind.TOOL_CALL]: the tools called. */
    val tools: List<String> = emptyList(),
    /** A quiet note under a reply, e.g. "Answered by granite-4" after a fallback. */
    val footer: String? = null,
    /** For a message from another agent ([AGENT_ROLE]): its name, shown above the bubble. */
    val sender: String? = null,
)

/** One event from `POST /api/sessions/{id}/chat/stream`. */
sealed interface ChatEvent {
    data class Delta(val text: String) : ChatEvent
    data class Tool(val name: String, val phase: String, val detail: String = "") : ChatEvent
    /** [servedModel]: the model that actually answered (the run's runtime), if Hermes said. */
    data class Done(val finalText: String?, val servedModel: String? = null) : ChatEvent
    data class Failed(val reason: String) : ChatEvent
    /** The run is waiting for you to allow a tool call. */
    data class Approval(val request: ApprovalRequest) : ChatEvent
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
     * The model id `GET /v1/models` reports for this profile: the profile name
     * (`API_SERVER_MODEL_NAME` overrides it), or `hermes-agent` for the default profile.
     * Cached raw; [ProfileNames.label] turns it into a display name. Null if it can't be fetched.
     */
    /** This profile's display name on this client's server; see [ProfileNames.label]. */
    fun profileLabel(): String = ProfileNames.label(settings.baseUrl, profile)

    suspend fun profileName(): String? = withContext(Dispatchers.IO) {
        ProfileNames.get(settings.baseUrl, profile.slug)?.let { return@withContext it }
        runCatching {
            val id = arrayIn(requestOk("GET", "/v1/models"), "data").objects().firstOrNull()?.str("id").orEmpty()
            id.ifBlank { null }
        }.onSuccess { name -> name?.let { ProfileNames.put(settings.baseUrl, profile.slug, it) } }.onFailure { Log.w("Hermes", "Could not read profile name: $it") }.getOrNull()
    }

    /**
     * Recent sessions, pinned ones first. Hermes keeps the canonical "Bot Chat" session hidden
     * from ordinary listings, so it is looked up by title and shown right after the pins.
     */
    suspend fun listSessions(limit: Int = 30): List<SessionSummary> = withContext(Dispatchers.IO) {
        val recent = parseSessions(requestOk("GET", "/api/sessions?limit=$limit"))
        val botChat = runCatching {
            parseSessions(requestOk("GET", "/api/sessions?limit=1&include_hidden=true&title=${enc(BOT_CHAT_TITLE)}"))
                .firstOrNull()
        }.onFailure { Log.w("Hermes", "Bot Chat lookup failed: ${it.javaClass.simpleName}") }.getOrNull()
        arrangeSessions(recent, botChat)
    }

    private fun parseSessions(body: String): List<SessionSummary> =
        arrayIn(body, "sessions", "data", "items").objects().map { o ->
            SessionSummary(
                id = o.str("id", "session_id"),
                title = o.str("title", "name"),
                updated = o.str("updated_at", "last_active", "last_activity", "created_at"),
                source = o.str("source", "platform"),
                preview = o.str("preview", "last_message", "snippet", "summary")
                    .ifBlank { o.optJSONObject("last_message")?.let { contentText(it.opt("content")) }.orEmpty() },
                pinned = o.optBoolean("pinned", false),
            )
        }.filter { it.id.isNotBlank() }

    /** Pins or unpins a session on the server; it then sorts to the top of every client's list. */
    /**
     * The profile's model catalog (`GET /api/model/options`, configured providers only) and the
     * profile's default model, for the chat's model picker.
     */
    suspend fun modelOptions(): Pair<List<ProviderModels>, ModelChoice?> = withContext(Dispatchers.IO) {
        val o = JSONObject(requestOk("GET", "/api/model/options?include_unconfigured=false"))
        val providers = (o.optJSONArray("providers") ?: JSONArray()).objects().map { p ->
            ProviderModels(
                slug = p.str("slug", "id"),
                name = p.str("name", "label", "slug"),
                models = (p.optJSONArray("models") ?: JSONArray()).let { a -> (0 until a.length()).map { a.optString(it) } },
                limited = p.opt("limit").let { it != null && it != JSONObject.NULL },
            )
        }
        val default = o.str("model").takeIf { it.isNotBlank() }?.let { ModelChoice(o.str("provider"), it) }
        providers to default
    }

    /**
     * Pins the session to [choice] with Hermes's session model lock (`POST
     * /api/sessions/{id}/model`). A per-request `model` isn't enough: after a session's first
     * turn Hermes stores the model it used on the session row, and that stored model wins over
     * request values unless a lock is set. Throws if Hermes refuses the model.
     */
    suspend fun lockSessionModel(sessionId: String, choice: ModelChoice) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("model", choice.model)
        if (choice.provider.isNotBlank()) body.put("provider", choice.provider)
        requestOk("POST", "/api/sessions/${enc(sessionId)}/model", body)
        Unit
    }

    /** Deletes a session on the server, with its messages. */
    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        requestOk("DELETE", "/api/sessions/${enc(sessionId)}")
        Unit
    }

    suspend fun setPinned(sessionId: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        requestOk("PATCH", "/api/sessions/${enc(sessionId)}", JSONObject().put("pinned", pinned))
        Unit
    }

    /** The session's current title (Hermes names untitled sessions from the first message). */
    suspend fun sessionTitle(sessionId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            JSONObject(requestOk("GET", "/api/sessions/${enc(sessionId)}")).optJSONObject("session")?.str("title")
        }.getOrNull()?.ifBlank { null }
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

    /** Creates an API session. Without a [title], Hermes names it from the first message. */
    suspend fun createSession(title: String? = null): String = withContext(Dispatchers.IO) {
        val body = requestOk("POST", "/api/sessions", JSONObject().apply { if (title != null) put("title", title) })
        val o = JSONObject(body)
        o.optJSONObject("session")?.str("id", "session_id")?.takeIf { it.isNotBlank() }
            ?: o.str("id", "session_id")
    }

    suspend fun messages(sessionId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val body = requestOk("GET", "/api/sessions/${enc(sessionId)}/messages")
        arrayIn(body, "messages", "data", "items").objects().flatMap { o ->
            historyEntries(
                role = o.str("role"),
                text = contentText(o.opt("content")),
                time = epochMillis(o.str("timestamp", "created_at")),
                toolName = o.str("tool_name", "name"),
                toolCalls = toolCallNames(o.opt("tool_calls")),
                displayKind = o.str("display_kind"),
            )
        }
    }

    /**
     * Sends [input] to the session and streams the reply. On E Ink we deliberately do not
     * redraw per token: callers should collect into a buffer and render whole paragraphs
     * (see [completedParagraphs]) and the final text on [ChatEvent.Done].
     */
    fun chatStream(sessionId: String, input: String): Flow<ChatEvent> = flow {
        val conn = open("POST", "/api/sessions/${enc(sessionId)}/chat/stream")
        coroutineScope {
            // A blocking readLine() ignores cancellation (keepalives never reach an emit), so
            // closing the connection is what unblocks it when the chat is left mid-run.
            // UNDISPATCHED so its finally is armed before any cancellation can arrive.
            val closer = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    conn.disconnect()
                }
            }
            try {
                conn.setRequestProperty("Accept", "text/event-stream")
                // The server sends `: keepalive` every 10 s, so a minute of silence means the
                // connection is gone; the run itself can still take as long as it needs.
                conn.readTimeout = 60_000
                writeJson(conn, JSONObject().put("input", input))
                // disconnect() is a no-op before the request starts, so re-check once it has.
                ensureActive()
                val code = conn.responseCode
                if (code !in 200..299) {
                    // Keep the status even if the optional, size-capped detail read fails.
                    val detail = runCatching {
                        conn.errorStream?.bufferedReader()?.use { r ->
                            val buf = CharArray(2048)
                            val n = r.read(buf)
                            if (n > 0) String(buf, 0, n) else ""
                        }
                    }.getOrNull().orEmpty()
                    throw apiError(code, "/api/sessions/…/chat/stream", detail)
                }
                val assembler = StreamAssembler()
                conn.inputStream.bufferedReader().use { reader ->
                    for (e in reader.sseEvents()) {
                        // Event names only: payloads carry conversation text.
                        Log.d("Hermes", "SSE ${e.event.ifBlank { "(message)" }}")
                        val data = runCatching { JSONObject(e.data) }.getOrNull() ?: JSONObject()
                        for (event in assembler.accept(e.event, data)) emit(event)
                        if (assembler.finished) return@use
                    }
                    // The stream ended without a terminal event, so the reply is incomplete.
                    emit(ChatEvent.Failed("the connection closed before the reply finished"))
                }
            } finally {
                closer.cancel() // also disconnects
            }
        }
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
        if (code !in 200..299) throw apiError(code, path, text)
        return text
    }

    /** A failed response as an exception; an unknown `/p/<profile>/` gets its own, matched on the code. */
    private fun apiError(code: Int, path: String, text: String): java.io.IOException =
        if (code == 404 && apiErrorCode(text) == PROFILE_UNKNOWN_CODE) {
            ProfileUnknownException(profile.slug)
        } else {
            HermesApiException(code, "HTTP $code on $path: ${text.take(200)}")
        }

    /**
     * Answers a waiting tool approval with [choice] (once, session, always or deny). A prompt
     * already answered elsewhere, or a run that moved on, comes back as
     * [ApprovalOutcome.AlreadyResolved] rather than an error.
     */
    suspend fun answerApproval(approval: ApprovalRequest, choice: String): ApprovalOutcome = withContext(Dispatchers.IO) {
        val (code, text) = request(
            "POST",
            "/v1/runs/${enc(approval.runId)}/approval",
            JSONObject(approvalBody(approval, choice)),
        )
        if (code in 200..299) ApprovalOutcome.Answered(choice) else approvalFailure(code, text)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

/**
 * Model ids fetched from each profile's `/v1/models`, for this process. Keyed by server URL
 * and slug, so after the URL changes, names from the old server are never shown.
 */
object ProfileNames {
    private val names = mutableStateMapOf<String, String>()

    private fun key(baseUrl: String, slug: String) = "${normalizeUrl(baseUrl)}|$slug"

    fun get(baseUrl: String, slug: String): String? = names[key(baseUrl, slug)]

    fun put(baseUrl: String, slug: String, id: String) {
        names[key(baseUrl, slug)] = id
    }

    /** The derived name for [slug] on [baseUrl]: the reported model id, else the slug. */
    fun derived(baseUrl: String, slug: String): String = displayNameFor(get(baseUrl, slug) ?: slug)

    /** What to show for [profile]: a name the user saved in Settings, else [derived]. */
    fun label(baseUrl: String, profile: HermesProfile): String =
        profile.displayName.trim().ifEmpty { derived(baseUrl, profile.slug) }
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
/** Names of the tools an assistant entry called (`tool_calls[].function.name` or `.name`). */
internal fun toolCallNames(calls: Any?): List<String> = when (calls) {
    is JSONArray -> calls.objects().map { c -> c.optJSONObject("function")?.str("name").orEmpty().ifBlank { c.str("name") } }
    else -> emptyList()
}.filter { it.isNotBlank() }

internal fun contentText(content: Any?): String = when (content) {
    is String -> content
    is JSONArray -> content.objects().joinToString("") { it.str("text") }
    is JSONObject -> content.str("text")
    else -> ""
}
