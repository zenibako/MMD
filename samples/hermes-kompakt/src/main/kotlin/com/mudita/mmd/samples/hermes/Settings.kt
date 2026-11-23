package com.mudita.mmd.samples.hermes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One Hermes profile on the server. [slug] "" is the default profile, served at the root
 * routes; any other slug is reached under `/p/<slug>/` (multiplexed gateway). Each profile
 * has its own `API_SERVER_KEY`.
 */
data class HermesProfile(val slug: String, val apiKey: String) {
    val isDefault: Boolean get() = slug.isEmpty()
}

/**
 * Connection settings, kept in plain SharedPreferences for the prototype.
 *
 * TODO: move the API key and ntfy token to EncryptedSharedPreferences / Android Keystore
 * before using this outside a private tailnet.
 */
data class HermesSettings(
    /** Hermes API server base URL, e.g. `https://hermes.my-tailnet.ts.net` (no trailing slash). */
    val baseUrl: String = "",
    /** Profiles to talk to; the first is always the default profile. */
    val profiles: List<HermesProfile> = listOf(HermesProfile("", "")),
    /** Slug of the profile the app is currently using. */
    val activeProfile: String = "",
    /** ntfy server, `https://ntfy.sh` by default. Use a self-hosted one for anything sensitive. */
    val ntfyServer: String = "https://ntfy.sh",
    /** Topic Hermes publishes to (`NTFY_PUBLISH_TOPIC`, or `NTFY_TOPIC` if unset). */
    val ntfyTopic: String = "",
    /** Optional ntfy access token (`tk_...`). */
    val ntfyToken: String = "",
    /** ntfy priority (1-5) at or above which a message breaks through as a full-screen alert. */
    val urgentPriority: Int = 5,
) {
    val active: HermesProfile get() = profile(activeProfile)
    val isHermesConfigured: Boolean get() = baseUrl.isNotBlank() && active.apiKey.isNotBlank()

    /** The profile with [slug], or the default profile if there is none. */
    fun profile(slug: String): HermesProfile = profiles.firstOrNull { it.slug == slug } ?: profiles.first()
    val isNtfyConfigured: Boolean get() = ntfyServer.isNotBlank() && ntfyTopic.isNotBlank()
}

object SettingsStore {
    private const val PREFS = "hermes_settings"

    fun load(context: Context): HermesSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = HermesSettings()
        return HermesSettings(
            baseUrl = p.getString("baseUrl", defaults.baseUrl).orEmpty(),
            profiles = readProfiles(p.getString("profiles", null), legacyKey = p.getString("apiKey", "").orEmpty()),
            activeProfile = p.getString("activeProfile", "").orEmpty(),
            ntfyServer = p.getString("ntfyServer", defaults.ntfyServer).orEmpty(),
            ntfyTopic = p.getString("ntfyTopic", defaults.ntfyTopic).orEmpty(),
            ntfyToken = p.getString("ntfyToken", defaults.ntfyToken).orEmpty(),
            urgentPriority = p.getInt("urgentPriority", defaults.urgentPriority),
        )
    }

    fun save(context: Context, s: HermesSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("baseUrl", normalizeUrl(s.baseUrl))
            .putString("profiles", writeProfiles(s.profiles))
            .putString("activeProfile", s.profile(s.activeProfile).slug)
            .remove("apiKey")
            .putString("ntfyServer", normalizeUrl(s.ntfyServer))
            .putString("ntfyTopic", s.ntfyTopic.trim())
            .putString("ntfyToken", s.ntfyToken.trim())
            .putInt("urgentPriority", s.urgentPriority.coerceIn(1, 5))
            .apply()
    }

    fun setActiveProfile(context: Context, slug: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("activeProfile", slug).apply()
    }

    // Older builds stored a single key; it becomes the default profile's key.
    private fun readProfiles(json: String?, legacyKey: String): List<HermesProfile> {
        val stored = json?.let { raw ->
            runCatching {
                JSONArray(raw).objects().map { HermesProfile(it.optString("slug"), it.optString("apiKey")) }
            }.getOrNull()
        }.orEmpty()
        val default = stored.firstOrNull { it.isDefault } ?: HermesProfile("", legacyKey)
        return listOf(default) + stored.filterNot { it.isDefault }.distinctBy { it.slug }
    }

    private fun writeProfiles(profiles: List<HermesProfile>): String = JSONArray().apply {
        profiles.forEach { put(JSONObject().put("slug", it.slug.trim()).put("apiKey", it.apiKey.trim())) }
    }.toString()
}

/** Profile slugs as Hermes names them: lowercase letters, digits, `-` and `_`. */
fun isValidProfileSlug(slug: String): Boolean = Regex("[a-z0-9][a-z0-9_-]*").matches(slug)

/** Trims, drops trailing slashes and defaults to `https://` when no scheme was typed. */
fun normalizeUrl(raw: String): String {
    val url = raw.trim().trimEnd('/')
    if (url.isEmpty()) return url
    val hasScheme = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    return if (hasScheme) url else "https://$url"
}
