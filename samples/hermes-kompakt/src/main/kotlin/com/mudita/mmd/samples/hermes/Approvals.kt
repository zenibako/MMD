package com.mudita.mmd.samples.hermes

import org.json.JSONObject

/**
 * A tool call the agent is waiting on you to allow, from the session stream's
 * `approval.request` event (Hermes ≥ 0.23: `POST /v1/runs/{run_id}/approval` answers it).
 */
data class ApprovalRequest(
    val runId: String,
    /** The exact prompt to answer; null when Hermes didn't name one (it then resolves the oldest). */
    val requestId: String?,
    /** Set when the run belongs to a session authority, which fences answers by generation. */
    val generation: Long?,
    /** What wants to run, e.g. "terminal"; "Command" when Hermes didn't say. */
    val tool: String,
    /** One line about what it will do: the description, else the command. */
    val summary: String,
    /** The answers Hermes accepts, e.g. once, session, always, deny. */
    val choices: List<String>,
) {
    /** The ways to approve, in Hermes's order; more than one means "Approve" asks which. */
    val approveChoices: List<String> get() = choices.filter { it != "deny" }.ifEmpty { listOf("once") }
}

/** Reads an `approval.request` event; null without a run id, which the answer needs. */
fun parseApproval(data: JSONObject): ApprovalRequest? {
    val runId = data.str("run_id")
    if (runId.isEmpty()) return null
    val generation = listOf("execution_generation", "generation")
        .firstOrNull { data.has(it) && !data.isNull(it) }
        ?.let { data.opt(it) }
        ?.let { (it as? Number)?.toLong() ?: it.toString().toLongOrNull() }
    val choices = data.optJSONArray("choices")?.let { a -> (0 until a.length()).map { a.optString(it) } }
        ?.filter { it.isNotBlank() }
        ?.ifEmpty { null }
        ?: listOf("once", "deny")
    val summary = data.str("description", "command", "summary").lineSequence()
        .map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    return ApprovalRequest(
        runId = runId,
        requestId = data.str("request_id", "prompt_id", "approval_id").ifEmpty { null },
        generation = generation,
        tool = data.str("tool_name", "tool", "name").ifEmpty { "Command" },
        summary = summary.ifEmpty { data.str("command") },
        choices = choices,
    )
}

/**
 * The body that answers [request] with [choice]. A run fenced by generation takes exactly
 * `{request_id, execution_generation, choice}` (any other key is refused as invalid_params);
 * otherwise `{choice}` plus `request_id` to answer that exact prompt. (The older `approval_id`
 * key isn't read by Hermes 0.23.4, which matches prompts by `request_id`.)
 */
fun approvalBody(request: ApprovalRequest, choice: String): Map<String, Any> =
    if (request.generation != null && request.requestId != null) {
        mapOf("request_id" to request.requestId, "execution_generation" to request.generation, "choice" to choice)
    } else {
        buildMap {
            put("choice", choice)
            request.requestId?.let { put("request_id", it) }
        }
    }

/** How an answer went, for the quiet line under the card. */
sealed interface ApprovalOutcome {
    data class Answered(val choice: String) : ApprovalOutcome
    /** Someone answered elsewhere first, or the run moved on. */
    data object AlreadyResolved : ApprovalOutcome
    data class Failed(val message: String) : ApprovalOutcome
}

/** Codes Hermes uses when the prompt is no longer open to this answer. */
private val RESOLVED_CODES = setOf("stale_generation", "approval_not_pending", "approval_not_active", "not_found")

/** Classifies a failed answer: a 409, or a 400/404 naming a closed prompt, means it was already handled. */
fun approvalFailure(status: Int, body: String): ApprovalOutcome {
    val code = apiErrorCode(body)
    return if (status == 409 || code in RESOLVED_CODES) {
        ApprovalOutcome.AlreadyResolved
    } else {
        ApprovalOutcome.Failed(code ?: "HTTP $status")
    }
}

/** The quiet line after answering. */
fun approvalOutcomeLine(outcome: ApprovalOutcome, tool: String): String = when (outcome) {
    is ApprovalOutcome.Answered -> when (outcome.choice) {
        "deny" -> "Denied $tool."
        "session" -> "Approved $tool for this session."
        "always" -> "Approved $tool always."
        else -> "Approved $tool once."
    }
    ApprovalOutcome.AlreadyResolved -> "This approval was already answered elsewhere."
    is ApprovalOutcome.Failed -> "Couldn't answer: ${outcome.message}. Answer it in another Hermes client."
}

/** Label for an approve choice in the sheet. */
fun approveChoiceLabel(choice: String): String = when (choice) {
    "once" -> "Approve once"
    "session" -> "Approve for this session"
    "always" -> "Always approve"
    else -> "Approve ($choice)"
}

private val CODE_FIELD = Regex(""""code"\s*:\s*"([^"]+)"""")

/** The `code` of a Hermes error body (top level or under `error`), e.g. "profile_unknown". */
fun apiErrorCode(body: String): String? = CODE_FIELD.find(body)?.groupValues?.get(1)

/** Thrown for a 404 whose code says the `/p/<profile>/` prefix names no profile on the server. */
class ProfileUnknownException(val slug: String) : java.io.IOException("Profile '$slug' isn't on the server")

const val PROFILE_UNKNOWN_CODE = "profile_unknown"

/** The line for a request refused because the server has no profile named [slug]. */
fun profileUnknownLine(what: String, slug: String): String =
    "$what. Profile '$slug' isn't on the server; check its name in Settings."
