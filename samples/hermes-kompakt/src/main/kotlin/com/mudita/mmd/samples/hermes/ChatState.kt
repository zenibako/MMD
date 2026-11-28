package com.mudita.mmd.samples.hermes

import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

// --- Reading position --------------------------------------------------------------------

/**
 * Where you were reading in a chat: the first visible row and how far it's scrolled, plus
 * what the chat looked like then, so a changed chat opens at the newest message instead.
 */
data class ReadingPosition(
    val index: Int,
    val offset: Int,
    val count: Int,
    val lastMessageTime: Long?,
    val showTools: Boolean,
) {
    fun encode(): String = listOf(index, offset, count, lastMessageTime ?: "", if (showTools) 1 else 0).joinToString(",")

    companion object {
        fun decode(raw: String?): ReadingPosition? {
            val p = raw?.split(',') ?: return null
            if (p.size != 5) return null
            return ReadingPosition(
                index = p[0].toIntOrNull() ?: return null,
                offset = p[1].toIntOrNull() ?: return null,
                count = p[2].toIntOrNull() ?: return null,
                lastMessageTime = p[3].toLongOrNull(),
                showTools = p[4] == "1",
            )
        }
    }
}

/**
 * The saved position to go back to, or null to open at the newest message: only when the
 * chat still has the same rows and newest message as when you left, with the same switch.
 */
fun positionToRestore(saved: ReadingPosition?, count: Int, lastMessageTime: Long?, showTools: Boolean): ReadingPosition? =
    saved?.takeIf {
        it.count == count && it.lastMessageTime == lastMessageTime && it.showTools == showTools && it.index in 0 until count
    }

// --- Reconciling a dropped send ----------------------------------------------------------

/**
 * After the connection dropped mid-turn: the reply Hermes finished anyway, if the history now
 * has one after your last message with [sentText]. Null means nothing came back, so Retry.
 */
fun completedReplyAfter(history: List<ChatMessage>, sentText: String): ChatMessage? {
    val sent = sentText.trim()
    val at = history.indexOfLast { it.role == "user" && it.kind == MessageKind.CONVERSATION && it.text.trim() == sent }
    if (at < 0) return null
    return history.drop(at + 1).firstOrNull { it.kind == MessageKind.CONVERSATION && it.role != "user" }
}

// --- The gateway's model for a session ---------------------------------------------------

/**
 * The model a session is pinned to on the gateway, from `GET /api/sessions/{id}`:
 * `model_config.browser_model_lock` (Hermes 0.23), set by `POST …/model`. Null when the
 * session inherits the profile's default.
 */
fun gatewayPin(session: JSONObject): ModelChoice? {
    val config = session.opt("model_config").let {
        when (it) {
            is JSONObject -> it
            is String -> runCatching { JSONObject(it) }.getOrNull()
            else -> null
        }
    } ?: return null
    val lock = config.optJSONObject("browser_model_lock") ?: return null
    val model = lock.str("model")
    if (model.isEmpty() || !lock.optBoolean("confirmed", true)) return null
    return ModelChoice(lock.str("provider"), model)
}

/**
 * What the Model sheet marks as chosen, trusting the gateway: no pin is "Profile default";
 * a pin is that model, except a pin this app made for "Profile default" ([local] null) that
 * still equals the profile's default, which stays "Profile default".
 */
fun selectedModel(pin: ModelChoice?, local: ModelChoice?, profileDefault: ModelChoice?): ModelChoice? = when {
    pin == null -> null
    local == null && profileDefault != null && pin.model == profileDefault.model -> null
    else -> pin
}

/**
 * The endpoint that clears a session's model pin, if the gateway's `/v1/capabilities`
 * advertises one (a DELETE on the session's `/model`), as `method to path`. Hermes 0.23.4
 * has none.
 */
fun clearModelEndpoint(capabilities: JSONObject): Pair<String, String>? {
    val endpoints = capabilities.optJSONObject("endpoints") ?: return null
    for (name in endpoints.keys()) {
        val e = endpoints.optJSONObject(name) ?: continue
        val method = e.str("method").uppercase()
        val path = e.str("path")
        if (method == "DELETE" && path.endsWith("/model") && "{session_id}" in path) return method to path
    }
    return null
}

// --- Deep links ----------------------------------------------------------------------------

/** A link to one chat: `hermes://chat/<profile>/<session>[#<message>]`. */
data class ChatLink(val profile: String, val session: String, val message: String? = null)

/** Profile names in links that mean the default profile. */
private val DEFAULT_PROFILE_NAMES = setOf("", "default", "hermes", "-")

/**
 * Reads a chat link: `hermes://chat/<profile>/<session>[#<message>]`, or
 * `hermes-kompakt://chat?profile=…&session=…[&message=…]`. The default profile may be written
 * "default", "hermes" or "-"; it comes back as "". Null for anything else.
 */
fun parseChatLink(link: String): ChatLink? {
    val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
    if (uri.host != "chat") return null
    fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
    val (profile, session, message) = when (uri.scheme) {
        "hermes" -> {
            val parts = uri.rawPath.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
            if (parts.size != 2) return null
            Triple(dec(parts[0]), dec(parts[1]), uri.rawFragment?.let(::dec))
        }
        "hermes-kompakt" -> {
            val q = uri.rawQuery.orEmpty().split('&').mapNotNull {
                val kv = it.split('=', limit = 2)
                if (kv.size == 2) kv[0] to dec(kv[1]) else null
            }.toMap()
            Triple(q["profile"].orEmpty(), q["session"] ?: return null, q["message"])
        }
        else -> return null
    }
    if (session.isBlank()) return null
    val slug = profile.trim().lowercase().let { if (it in DEFAULT_PROFILE_NAMES) "" else it }
    return ChatLink(slug, session.trim(), message?.trim()?.ifEmpty { null })
}

/** Said under "Profile default" when the gateway can't clear a pin: it pins [model] instead. */
fun pinsDefaultNote(model: String): String =
    "On this gateway, Profile default pins $model; the chat won't follow later changes to the default."
