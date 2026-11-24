@file:OptIn(ExperimentalMaterial3Api::class)

package com.mudita.mmd.samples.hermes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// All screens follow the same E Ink rules: static lists, explicit "Refresh" instead of
// auto-polling, and agent replies rendered once when complete rather than token by token.

@Composable
fun SessionsScreen(
    onOpen: (sessionId: String, title: String, profileSlug: String, source: String) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(SettingsStore.load(context)) }
    // A new client (and session list) whenever the active profile changes.
    val client = remember(settings.active) { HermesClient(settings) }
    val profileName = ProfileNames.label(client.profile)
    LaunchedEffect(client) { client.profileName() }
    var sessions by remember { mutableStateOf<List<SessionSummary>>(emptyList()) }
    var status by remember { mutableStateOf("Loading…") }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    fun refresh() = scope.launch {
        status = "Loading…"
        status = runCatching { sessions = client.listSessions(); "" }
            .getOrElse { "Could not reach Hermes: ${it.message}" }
    }
    LaunchedEffect(client) {
        sessions = emptyList()
        refresh()
    }

    fun switchProfile() {
        val all = settings.profiles
        val next = all[(all.indexOf(settings.active) + 1) % all.size]
        SettingsStore.setActiveProfile(context, next.slug)
        settings = settings.copy(activeProfile = next.slug)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBarMMD(
            title = { TextMMD("$profileName sessions", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            actions = {
                // Cycles through profiles; with only the default profile there is nothing to switch.
                if (settings.profiles.size > 1) {
                    OutlinedButtonMMD(onClick = { switchProfile() }) { TextMMD("Profile") }
                }
            },
        )
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ButtonMMD(onClick = {
                scope.launch {
                    // Hermes requires unique session titles, so stamp each one with the time.
                    val title = "Kompakt ${uniqueStamp()}"
                    runCatching { client.createSession(title) }
                        .onSuccess { onOpen(it, title, client.profile.slug, "api_server") }
                        .onFailure { status = "Could not start a session: ${it.message}" }
                }
            }) { TextMMD("New") }
            OutlinedButtonMMD(onClick = { refresh() }) { TextMMD("Refresh") }
            OutlinedButtonMMD(onClick = onSettings) { TextMMD("⚙") }
        }
        if (status.isNotBlank()) TextMMD(status, modifier = Modifier.padding(16.dp))
        LazyColumnMMD(Modifier.fillMaxSize()) {
            items(sessions, key = { it.id }) { s ->
                Column(
                    Modifier.fillMaxWidth().clickable { onOpen(s.id, s.title, client.profile.slug, s.source) }.padding(16.dp),
                ) {
                    TextMMD(s.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextMMD(
                        listOf(
                            s.source,
                            if (s.isReadOnly) "read-only" else "",
                            formatUpdated(s.updated, fmt),
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDividerMMD(thickness = 1.dp)
            }
        }
    }
}

@Composable
fun ChatScreen(
    sessionId: String,
    title: String,
    profileSlug: String,
    source: String,
    onOpenSession: (sessionId: String, title: String) -> Unit,
    onBack: () -> Unit,
) {
    val readOnly = isReadOnlySource(source)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember {
        val settings = SettingsStore.load(context)
        HermesClient(settings, settings.profile(profileSlug))
    }
    val profileName = ProfileNames.label(client.profile)
    LaunchedEffect(Unit) { client.profileName() }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var draft by remember { mutableStateOf("") }
    var working by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(sessionId) {
        runCatching { client.messages(sessionId) }
            .onSuccess { messages.clear(); messages.addAll(it) }
            .onFailure { working = "Could not load history: ${it.message}" }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || working == "Thinking…") return
        // Close the keyboard so the reply has the whole screen.
        keyboard?.hide()
        focusManager.clearFocus()
        draft = ""
        messages.add(ChatMessage("user", text))
        working = "Thinking…"
        scope.launch {
            runCatching {
                client.chatStream(sessionId, text).collect { e ->
                    when (e) {
                        // Show which tool is running, but don't repaint per token.
                        is ChatEvent.Tool ->
                            working = "Working: ${e.name}…" + if (e.detail.isNotBlank()) " ${e.detail}" else ""
                        is ChatEvent.Done -> {
                            e.finalText?.let { messages.add(ChatMessage("assistant", it)) }
                            working = null
                        }
                        is ChatEvent.Failed -> working = "Run failed: ${e.reason}"
                        is ChatEvent.Delta -> Unit
                    }
                }
            }.onFailure {
                working = if ((it as? HermesApiException)?.code == 409) {
                    "This session belongs to another surface. Use Continue here."
                } else {
                    "Send failed: ${it.message}"
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBarMMD(
            title = { TextMMD(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { OutlinedButtonMMD(onClick = onBack) { TextMMD("Back") } },
        )
        LazyColumnMMD(Modifier.weight(1f).fillMaxWidth()) {
            items(messages) { m ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    TextMMD(
                        if (m.role == "user") "You" else profileName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    TextMMD(m.text)
                }
                HorizontalDividerMMD(thickness = 1.dp)
            }
        }
        working?.let { TextMMD(it, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (readOnly) {
            // Hermes refuses API turns on sessions owned by cron, kanban or messaging; fork instead.
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextMMD("Started from $source, so it's read-only here.")
                ButtonMMD(onClick = {
                    working = "Copying…"
                    scope.launch {
                        val newTitle = "$title (Kompakt ${uniqueStamp()})"
                        runCatching { client.forkSession(sessionId, newTitle) }
                            .onSuccess { onOpenSession(it, newTitle) }
                            .onFailure { working = "Could not continue: ${it.message}" }
                    }
                }) { TextMMD("Continue here") }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextFieldMMD(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { TextMMD("Message your agent") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                )
                ButtonMMD(onClick = { send() }) { TextMMD("Send") }
            }
        }
    }
}

@Composable
fun SettingsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(SettingsStore.load(context)) }
    var status by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TopAppBarMMD(title = { TextMMD("Connect to Hermes") })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field("Hermes URL", s.baseUrl) { s = s.copy(baseUrl = it) }
            Field("API key (API_SERVER_KEY)", s.profiles.first().apiKey) { key ->
                s = s.copy(profiles = listOf(s.profiles.first().copy(apiKey = key)) + s.profiles.drop(1))
            }
            ProfilesSection(s.profiles) { s = s.copy(profiles = it) }
            if (status.isNotBlank()) TextMMD(status)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButtonMMD(onClick = {
                    scope.launch {
                        val result = HermesClient(s).healthCheck()
                        status = if (result == "OK") "Hermes is reachable." else "No answer from Hermes: $result"
                    }
                }) { TextMMD("Test") }
                ButtonMMD(onClick = {
                    SettingsStore.save(context, s)
                    onDone()
                }) { TextMMD("Save") }
            }
        }
    }
}

/** Extra profiles reached under `/p/<slug>/`; the default profile's key is the field above. */
@Composable
private fun ProfilesSection(profiles: List<HermesProfile>, onChange: (List<HermesProfile>) -> Unit) {
    var slug by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    TextMMD("Other profiles", fontWeight = FontWeight.Bold)
    profiles.drop(1).forEach { p ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextMMD(
                "${p.slug} · ${if (p.apiKey.isBlank()) "no key" else "key set"}",
                modifier = Modifier.weight(1f),
            )
            OutlinedButtonMMD(onClick = { onChange(profiles - p) }) { TextMMD("Remove") }
        }
    }
    Field("Profile name (as on the server)", slug) { slug = it.trim().lowercase() }
    Field("That profile's API key", key) { key = it }
    if (error.isNotBlank()) TextMMD(error)
    OutlinedButtonMMD(onClick = {
        error = when {
            !isValidProfileSlug(slug) -> "Use the profile's name: lowercase letters, digits, - or _."
            profiles.any { it.slug == slug } -> "That profile is already added."
            key.isBlank() -> "Enter that profile's API key."
            else -> {
                onChange(profiles + HermesProfile(slug, key.trim()))
                slug = ""
                key = ""
                ""
            }
        }
    }) { TextMMD("Add profile") }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    TextFieldMMD(
        value = value,
        onValueChange = onChange,
        label = { TextMMD(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Hermes reports times as epoch seconds (e.g. `1.7954E9`); show those as a date, else as given. */
private fun formatUpdated(raw: String, fmt: DateFormat): String =
    raw.toDoubleOrNull()?.let { fmt.format(Date((it * 1000).toLong())) } ?: raw

/** Date and time to the second, e.g. "Nov 23 16:21:05", to keep session titles unique. */
private fun uniqueStamp(): String = java.text.SimpleDateFormat("MMM d HH:mm:ss", java.util.Locale.getDefault()).format(Date())
