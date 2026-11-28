package com.mudita.mmd.samples.hermes

/** A model a chat can run on: [provider] slug plus [model] id, as Hermes's model picker lists them. */
data class ModelChoice(val provider: String, val model: String) {
    /** Short label for the picker and the chat header, e.g. "claude-opus-5-5 (anthropic)". */
    val label: String get() = if (provider.isBlank()) model else "$model ($provider)"

    /** One-line form for settings storage. */
    fun encode(): String = "$provider\t$model"

    companion object {
        fun decode(raw: String?): ModelChoice? {
            val parts = raw?.split('\t') ?: return null
            if (parts.size != 2 || parts[1].isBlank()) return null
            return ModelChoice(parts[0], parts[1])
        }
    }
}

/** One provider row from `GET /api/model/options`. */
data class ProviderModels(
    val slug: String,
    val name: String,
    val models: List<String>,
    /** Set when the provider's credentials are rate-limited or over quota. */
    val limited: Boolean = false,
)

/**
 * Every model the profile can use, in Hermes's catalog order, each once. Providers with no
 * models are skipped; limited ones are kept (another model there may still work) and sorted last.
 */
fun modelChoices(providers: List<ProviderModels>): List<ModelChoice> =
    providers.sortedBy { it.limited }
        .flatMap { p -> p.models.map { it.trim() }.filter { it.isNotEmpty() }.map { ModelChoice(p.slug, it) } }
        .distinct()

/**
 * The model to lock a session to for a pick: the chosen model, or for "Profile default"
 * ([choice] null) the profile's default model. Hermes has no way to clear a session's lock, so
 * going back to the default means locking to it. Null if the default isn't known.
 *
 * TODO: once Hermes offers `DELETE /api/sessions/{id}/model`, "Profile default" should clear the
 * lock instead, so the chat follows later changes to the profile's default.
 */
fun lockTarget(choice: ModelChoice?, profileDefault: ModelChoice?): ModelChoice? = choice ?: profileDefault

/** Settings key for a session's chosen model, scoped to server, profile and session. */
fun sessionModelKey(baseUrl: String, profileSlug: String, sessionId: String): String =
    "model|${normalizeUrl(baseUrl)}|$profileSlug|$sessionId"

/**
 * Whether a row of the model sheet carries the check: the row the current choice came from.
 * [row] null is the "Profile default" row; [pinned] null means the chat inherits the default.
 * A pinned model keeps its check even when it is the same model as the default, so a pinned
 * Granite and an inherited Granite stay distinguishable.
 */
fun isCurrentChoice(row: ModelChoice?, pinned: ModelChoice?): Boolean = row == pinned
