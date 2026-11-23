package com.mudita.mmd.samples.hermes

import android.content.Context

/**
 * Connection settings, kept in plain SharedPreferences for the prototype.
 *
 * TODO: move the API key and ntfy token to EncryptedSharedPreferences / Android Keystore
 * before using this outside a private tailnet.
 */
data class HermesSettings(
    /** Hermes API server base URL, e.g. `https://hermes.my-tailnet.ts.net` (no trailing slash). */
    val baseUrl: String = "",
    /** Value of `API_SERVER_KEY` on the Hermes host. Sent as `Authorization: Bearer`. */
    val apiKey: String = "",
    /** ntfy server, `https://ntfy.sh` by default. Use a self-hosted one for anything sensitive. */
    val ntfyServer: String = "https://ntfy.sh",
    /** Topic Hermes publishes to (`NTFY_PUBLISH_TOPIC`, or `NTFY_TOPIC` if unset). */
    val ntfyTopic: String = "",
    /** Optional ntfy access token (`tk_...`). */
    val ntfyToken: String = "",
    /** ntfy priority (1-5) at or above which a message breaks through as a full-screen alert. */
    val urgentPriority: Int = 5,
) {
    val isHermesConfigured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
    val isNtfyConfigured: Boolean get() = ntfyServer.isNotBlank() && ntfyTopic.isNotBlank()
}

object SettingsStore {
    private const val PREFS = "hermes_settings"

    fun load(context: Context): HermesSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = HermesSettings()
        return HermesSettings(
            baseUrl = p.getString("baseUrl", defaults.baseUrl).orEmpty(),
            apiKey = p.getString("apiKey", defaults.apiKey).orEmpty(),
            ntfyServer = p.getString("ntfyServer", defaults.ntfyServer).orEmpty(),
            ntfyTopic = p.getString("ntfyTopic", defaults.ntfyTopic).orEmpty(),
            ntfyToken = p.getString("ntfyToken", defaults.ntfyToken).orEmpty(),
            urgentPriority = p.getInt("urgentPriority", defaults.urgentPriority),
        )
    }

    fun save(context: Context, s: HermesSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("baseUrl", normalizeUrl(s.baseUrl))
            .putString("apiKey", s.apiKey.trim())
            .putString("ntfyServer", normalizeUrl(s.ntfyServer))
            .putString("ntfyTopic", s.ntfyTopic.trim())
            .putString("ntfyToken", s.ntfyToken.trim())
            .putInt("urgentPriority", s.urgentPriority.coerceIn(1, 5))
            .apply()
    }
}

/** Trims, drops trailing slashes and defaults to `https://` when no scheme was typed. */
fun normalizeUrl(raw: String): String {
    val url = raw.trim().trimEnd('/')
    if (url.isEmpty()) return url
    val hasScheme = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    return if (hasScheme) url else "https://$url"
}
