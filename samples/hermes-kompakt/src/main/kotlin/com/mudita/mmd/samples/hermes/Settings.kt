package com.mudita.mmd.samples.hermes

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One Hermes profile on the server. [slug] "" is the default profile, served at the root
 * routes; any other slug is reached under `/p/<slug>/` (multiplexed gateway). Each profile
 * has its own `API_SERVER_KEY`.
 */
data class HermesProfile(
    val slug: String,
    val apiKey: String,
    /** A name the user chose in Settings; blank uses the name derived from Hermes. Display only. */
    val displayName: String = "",
) {
    val isDefault: Boolean get() = slug.isEmpty()
}

/**
 * Connection settings, kept in plain SharedPreferences for the prototype.
 *
 * TODO: move the API keys to EncryptedSharedPreferences / Android Keystore
 * before using this outside a private tailnet.
 */
data class HermesSettings(
    /** Hermes API server base URL, e.g. `https://hermes.my-tailnet.ts.net` (no trailing slash). */
    val baseUrl: String = "",
    /** Profiles to talk to; the first is always the default profile. */
    val profiles: List<HermesProfile> = listOf(HermesProfile("", "")),
    /** Slug of the profile the app is currently using. */
    val activeProfile: String = "",
) {
    val active: HermesProfile get() = profile(activeProfile)
    val isHermesConfigured: Boolean get() = baseUrl.isNotBlank() && active.apiKey.isNotBlank()

    /** The profile with [slug], or the default profile if there is none. */
    fun profile(slug: String): HermesProfile = profiles.firstOrNull { it.slug == slug } ?: profiles.first()
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
        )
    }

    /** Saves [s]; with [sync] the write is committed to disk and its success returned. */
    fun save(context: Context, s: HermesSettings, sync: Boolean = false): Boolean {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("baseUrl", normalizeUrl(s.baseUrl))
            .putString("profiles", writeProfiles(s.profiles))
            .putString("activeProfile", s.profile(s.activeProfile).slug)
            .remove("apiKey")
            // Notifications moved to the ntfy Kompakt app; drop this app's old ntfy settings.
            .remove("ntfyServer")
            .remove("ntfyTopic")
            .remove("ntfyToken")
            .remove("urgentPriority")
        if (sync) return edit.commit()
        edit.apply()
        return true
    }

    /** Name of the one-time import file in the app's external files dir. */
    const val IMPORT_FILE = "profiles-import.json"

    /**
     * Merges profiles from a one-time import file, then deletes the file. tools/import-profiles.py
     * pushes it over adb so long API keys never have to be typed on the phone. The format is
     * `[{"slug": "researcher", "apiKey": "…"}]`. The default profile is never touched, and a
     * slug that already exists gets the imported key. Returns how many profiles were merged.
     */
    fun importPendingProfiles(context: Context): Int {
        val file = File(context.getExternalFilesDir(null) ?: return 0, IMPORT_FILE)
        if (!file.exists()) return 0
        val incoming = try {
            JSONArray(file.readText()).objects()
                .map { HermesProfile(it.optString("slug").trim(), it.optString("apiKey").trim()) }
                .filter { isValidProfileSlug(it.slug) && it.apiKey.isNotBlank() }
                .distinctBy { it.slug }
        } catch (e: Exception) {
            // Unreadable, so a retry can't help; the file holds API keys, so don't leave it.
            Log.w("Hermes", "Profile import failed: ${e.javaClass.simpleName}")
            file.delete()
            return 0
        }
        if (incoming.isNotEmpty()) {
            val s = load(context)
            val kept = s.profiles.filterNot { p -> incoming.any { it.slug == p.slug } }
            // A re-import replaces the key but keeps a display name the user chose.
            val merged = incoming.map { inc ->
                inc.copy(displayName = s.profiles.firstOrNull { it.slug == inc.slug }?.displayName.orEmpty())
            }
            if (!save(context, s.copy(profiles = kept + merged), sync = true)) {
                // Keep the file so the next start retries the import.
                Log.w("Hermes", "Profile import not saved; will retry on next start")
                return 0
            }
        }
        Log.i("Hermes", "Imported ${incoming.size} profile(s): ${incoming.joinToString { it.slug }}")
        // Saved (or nothing to save): the keys are in the app's private settings now.
        file.delete()
        return incoming.size
    }

    /** Whether chats show tool calls/results and system entries as well as the conversation. */
    fun showToolMessages(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("showToolMessages", false)

    fun setShowToolMessages(context: Context, show: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("showToolMessages", show).apply()
    }

    /** Session groups hidden from the list (none by default). */
    fun hiddenSessionGroups(context: Context): Set<SessionGroup> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("hiddenSessionGroups", emptySet()).orEmpty()
            .mapNotNull { name -> SessionGroup.entries.firstOrNull { it.name == name } }.toSet()

    fun setHiddenSessionGroups(context: Context, hidden: Set<SessionGroup>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet("hiddenSessionGroups", hidden.map { it.name }.toSet()).apply()
    }

    /** The model chosen for one session (see [sessionModelKey]); null means the profile's default. */
    fun sessionModel(context: Context, key: String): ModelChoice? =
        ModelChoice.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null))

    fun setSessionModel(context: Context, key: String, choice: ModelChoice?) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (choice == null) edit.remove(key) else edit.putString(key, choice.encode())
        edit.apply()
    }

    fun setActiveProfile(context: Context, slug: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("activeProfile", slug).apply()
    }

    // Older builds stored a single key; it becomes the default profile's key.
    private fun readProfiles(json: String?, legacyKey: String): List<HermesProfile> {
        val stored = json?.let { raw ->
            runCatching {
                JSONArray(raw).objects().map {
                    HermesProfile(it.optString("slug"), it.optString("apiKey"), it.optString("displayName"))
                }
            }.getOrNull()
        }.orEmpty()
        val default = stored.firstOrNull { it.isDefault } ?: HermesProfile("", legacyKey)
        return listOf(default) + stored.filterNot { it.isDefault }.distinctBy { it.slug }
    }

    private fun writeProfiles(profiles: List<HermesProfile>): String = JSONArray().apply {
        profiles.forEach {
            val o = JSONObject().put("slug", it.slug.trim()).put("apiKey", it.apiKey.trim())
            if (it.displayName.isNotBlank()) o.put("displayName", it.displayName.trim())
            put(o)
        }
    }.toString()
}

/** Profile slugs as Hermes names them: lowercase letters, digits, `-` and `_`. */
fun isValidProfileSlug(slug: String): Boolean = Regex("[a-z0-9][a-z0-9_-]*").matches(slug)

/** Android blocks cleartext traffic for this app, so a plain `http://` URL can never connect. */
fun isCleartextUrl(raw: String): Boolean = raw.trim().startsWith("http://", ignoreCase = true)

const val CLEARTEXT_MESSAGE = "Android blocks plain http:// here. Use an https:// URL (Tailscale serve or a TLS proxy)."

/** Trims, drops trailing slashes and defaults to `https://` when no scheme was typed. */
fun normalizeUrl(raw: String): String {
    val url = raw.trim().trimEnd('/')
    if (url.isEmpty()) return url
    val hasScheme = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    return if (hasScheme) url else "https://$url"
}
