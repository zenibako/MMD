package com.mudita.mmd.samples.hermes

/** Model ids that mean the default profile. */
private val DEFAULT_IDS = setOf("hermes-agent", "default", "hermes")

/**
 * A profile's display name from the model id Hermes reports for it (the profile name, or
 * `API_SERVER_MODEL_NAME` when set). Display only: ids, slugs and URLs stay as they are.
 *
 * - An id that already looks like a name (has a space or an uppercase letter) is shown as-is,
 *   so a name set on the server takes over by itself.
 * - The default profile's ids ("hermes-agent", "default", "hermes", or blank) show "Hermes".
 * - Anything else is split on `-` and `_` with each word capitalized: "lead-dev" is "Lead Dev".
 */
fun displayNameFor(id: String): String {
    val trimmed = id.trim()
    if (trimmed.any { it == ' ' || it.isUpperCase() }) return trimmed
    if (trimmed.isEmpty() || trimmed in DEFAULT_IDS) return "Hermes"
    return trimmed.split('-', '_')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        .ifEmpty { trimmed }
}
