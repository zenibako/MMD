package com.mudita.mmd.samples.hermes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import kotlinx.coroutines.launch
import com.mudita.mmd.R as MmdR

/** Where Settings is: the top list or one of its sub-screens. */
private sealed interface SettingsPage {
    data object Main : SettingsPage
    data object Server : SettingsPage
    data object Profiles : SettingsPage

    /** One profile's screen; [slug] null adds a new one. */
    data class Profile(val slug: String?) : SettingsPage
}

/**
 * Settings as the stock Kompakt app lays it out: a list of rows (Server, Profiles, Text size),
 * each opening a sub-screen with a back arrow, or a choice sheet. Each sub-screen saves with
 * its own Save; Back with unsaved edits asks first. [onDone] leaves Settings.
 */
@Composable
fun SettingsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(SettingsStore.load(context)) }
    var page by remember { mutableStateOf<SettingsPage>(SettingsPage.Main) }
    var textSizeSheet by remember { mutableStateOf(false) }

    fun save(new: HermesSettings) {
        SettingsStore.save(context, new)
        settings = new
    }

    when (val p = page) {
        SettingsPage.Main -> {
            BackHandler(onBack = onDone)
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    MessagesTopBar(label = "Hermes", title = "Settings", onBack = onDone)
                    NavRow("Server", settings.baseUrl.ifBlank { "Not set" }) { page = SettingsPage.Server }
                    NavRow("Profiles", profilesSummary(settings.profiles)) { page = SettingsPage.Profiles }
                    NavRow("Text size", MessageTextSize.current.label) { textSizeSheet = true }
                }
                if (textSizeSheet) TextSizeSheet { textSizeSheet = false }
            }
        }
        SettingsPage.Server -> ServerPage(settings, onSave = { save(it) }, onBack = { page = SettingsPage.Main })
        SettingsPage.Profiles -> ProfilesPage(
            settings,
            onOpen = { page = SettingsPage.Profile(it) },
            onBack = { page = SettingsPage.Main },
        )
        is SettingsPage.Profile -> ProfilePage(
            settings,
            slug = p.slug,
            onSave = { save(it) },
            onBack = { page = SettingsPage.Profiles },
        )
    }
}

/** "5 profiles · 1 without a key", for the Settings row. */
fun profilesSummary(profiles: List<HermesProfile>): String {
    val missing = profiles.count { it.apiKey.isBlank() }
    val count = if (profiles.size == 1) "1 profile" else "${profiles.size} profiles"
    return if (missing == 0) count else "$count · $missing without a key"
}

/** Server: the Hermes URL, a Test button and Save. */
@Composable
private fun ServerPage(settings: HermesSettings, onSave: (HermesSettings) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(settings.baseUrl) }
    var status by remember { mutableStateOf("") }
    // Bumped by every URL edit and Test tap, so a slow Test can't overwrite a newer result.
    var testSeq by remember { mutableStateOf(0) }
    val dirty = url != settings.baseUrl
    SubScreen("Server", dirty = dirty, onBack = onBack) {
        SettingsField("Hermes URL", url) {
            url = it
            testSeq++
            status = ""
        }
        if (status.isNotBlank()) TextMMD(status, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                SheetButton("Test") {
                    val seq = ++testSeq
                    if (isCleartextUrl(url)) {
                        status = CLEARTEXT_MESSAGE
                    } else {
                        val tested = settings.copy(baseUrl = url)
                        status = "Testing…"
                        scope.launch {
                            val result = HermesClient(tested).healthCheck()
                            if (seq != testSeq) return@launch
                            status = if (result == "OK") "Hermes is reachable." else "No answer from Hermes: $result"
                        }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                SheetButton("Save", filled = true) {
                    if (isCleartextUrl(url)) {
                        status = CLEARTEXT_MESSAGE
                    } else {
                        onSave(settings.copy(baseUrl = url))
                        onBack()
                    }
                }
            }
        }
    }
}

/** Profiles: one row per profile (the default first), then "Add profile". */
@Composable
private fun ProfilesPage(settings: HermesSettings, onOpen: (String?) -> Unit, onBack: () -> Unit) {
    SubScreen("Profiles", dirty = false, onBack = onBack, padded = false) {
        settings.profiles.forEach { p ->
            NavRow(
                ProfileNames.label(settings.baseUrl, p),
                profileRowSummary(p),
            ) { onOpen(p.slug) }
        }
        NavRow("Add profile", null) { onOpen(null) }
    }
}

/** "default · key set", "lead-dev · no key": the server name and whether a key is saved. */
fun profileRowSummary(p: HermesProfile): String =
    "${p.slug.ifEmpty { "default" }} · ${if (p.apiKey.isBlank()) "no key" else "key set"}"

/**
 * Why a profile can't be saved yet, or null if it can. A new profile needs a valid, unused
 * server name; every profile needs a key.
 */
fun profileError(slug: String, apiKey: String, isNew: Boolean, existing: List<HermesProfile>): String? = when {
    isNew && !isValidProfileSlug(slug) -> "Use the profile's name on the server: lowercase letters, digits, - or _."
    isNew && existing.any { it.slug == slug } -> "That profile is already added."
    apiKey.isBlank() -> "Enter the profile's API key."
    else -> null
}

/**
 * One profile: its server name (editable only when adding), a display name (blank uses the one
 * derived from Hermes), its API key as a password field, Save, and Remove for added profiles.
 */
@Composable
private fun ProfilePage(settings: HermesSettings, slug: String?, onSave: (HermesSettings) -> Unit, onBack: () -> Unit) {
    val existing = slug?.let { s -> settings.profiles.firstOrNull { it.slug == s } }
    val isNew = existing == null
    var name by remember { mutableStateOf(existing?.slug.orEmpty()) }
    var displayName by remember { mutableStateOf(existing?.displayName.orEmpty()) }
    var key by remember { mutableStateOf(existing?.apiKey.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val dirty = name != existing?.slug.orEmpty() || displayName != existing?.displayName.orEmpty() || key != existing?.apiKey.orEmpty()
    val title = existing?.let { ProfileNames.label(settings.baseUrl, it) } ?: "New profile"

    Box(Modifier.fillMaxSize()) {
        SubScreen(title, dirty = dirty && !confirmRemove, onBack = onBack) {
            if (isNew) {
                SettingsField("Name on the server", name) { name = it.trim().lowercase() }
            } else {
                TextMMD("Name on the server", style = MaterialTheme.typography.labelMedium)
                TextMMD(existing!!.slug.ifEmpty { "default" }, style = MaterialTheme.typography.bodyLarge)
            }
            SettingsField(
                "Display name (blank: ${ProfileNames.derived(settings.baseUrl, name)})",
                displayName,
            ) { displayName = it }
            PasswordField("API key", key) { key = it.trim() }
            error?.let { TextMMD(it, style = MaterialTheme.typography.bodyLarge) }
            SheetButton("Save", filled = true) {
                val problem = profileError(name, key, isNew, settings.profiles)
                if (problem != null) {
                    error = problem
                } else {
                    val updated = HermesProfile(name, key, displayName.trim())
                    val profiles = if (isNew) settings.profiles + updated else settings.profiles.map { if (it.slug == name) updated else it }
                    onSave(settings.copy(profiles = profiles))
                    onBack()
                }
            }
            if (existing != null && !existing.isDefault) {
                SheetButton("Remove profile") { confirmRemove = true }
            }
        }
        if (confirmRemove && existing != null) {
            KompaktSheet("Remove this profile?", onDismiss = { confirmRemove = false }, cancelFilled = false) {
                TextMMD(
                    title,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                SheetButton("Remove profile", filled = true) {
                    val remaining = settings.profiles - existing
                    onSave(
                        settings.copy(
                            profiles = remaining,
                            activeProfile = if (settings.activeProfile == existing.slug) "" else settings.activeProfile,
                        ),
                    )
                    confirmRemove = false
                    onBack()
                }
            }
        }
    }
}

/**
 * A Settings sub-screen: back arrow and title, then [content] in a scrolling column. Back
 * (arrow or system) with unsaved edits ([dirty]) asks before discarding them.
 */
@Composable
private fun SubScreen(
    title: String,
    dirty: Boolean,
    onBack: () -> Unit,
    padded: Boolean = true,
    content: @Composable () -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val back = { if (dirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = back)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MessagesTopBar(label = "Settings", title = title, onBack = back)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .then(if (padded) Modifier.padding(16.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(if (padded) 12.dp else 0.dp),
            ) { content() }
        }
        if (confirmDiscard) {
            KompaktSheet("Discard changes?", onDismiss = { confirmDiscard = false }, cancelLabel = "Keep editing", cancelFilled = false) {
                SheetButton("Discard changes", filled = true) {
                    confirmDiscard = false
                    onBack()
                }
            }
        }
    }
}

/**
 * A stock Settings navigation row: 23 sp Black title over a 21 sp summary (or a 21 sp label
 * alone), a chevron at the right, a dashed divider under it. 64 dp, or 72 with a summary.
 */
@Composable
private fun NavRow(title: String, summary: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (summary == null) 64.dp else 72.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TextMMD(
                title,
                style = if (summary == null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                TextMMD(summary, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(painterResource(MmdR.drawable.chevron_filled_right), contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
    }
    DashedRowDivider()
}

@Composable
private fun SettingsField(label: String, value: String, onChange: (String) -> Unit) {
    TextFieldMMD(
        value = value,
        onValueChange = onChange,
        label = { TextMMD(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A key field: masked unless you tap Show, password keyboard, no autocorrect or suggestions.
 * The value never goes to the log.
 */
@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    TextFieldMMD(
        value = value,
        onValueChange = onChange,
        label = { TextMMD(label) },
        singleLine = true,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None,
        ),
        trailingIcon = {
            TextMMD(
                if (shown) "Hide" else "Show",
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = if (shown) "Hide key" else "Show key") { shown = !shown }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextSizeSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    KompaktSheet("Text size", onDismiss = onDismiss) {
        TextSize.entries.forEach { size ->
            ChoiceButton(size.label, selected = size == MessageTextSize.current) {
                MessageTextSize.set(context, size)
                onDismiss()
            }
        }
    }
}
