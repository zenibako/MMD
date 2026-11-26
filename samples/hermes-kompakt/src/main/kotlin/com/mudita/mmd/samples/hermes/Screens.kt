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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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
    val fmt = remember { RowTimeFormat() }

    val currentClient by rememberUpdatedState(client)
    var refreshJob by remember { mutableStateOf<Job?>(null) }

    fun refresh() {
        refreshJob?.cancel()
        val c = client
        refreshJob = scope.launch {
            status = "Loading…"
            val result = runCatching { c.listSessions() }
            // A late answer for a profile we've since switched away from must not land here:
            // its session ids would be opened with the new profile's routes and key.
            if (!isActive || c !== currentClient) return@launch
            result
                .onSuccess { sessions = it; status = "" }
                .onFailure { status = "Could not reach Hermes: ${it.message}" }
        }
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

    fun newSession() = scope.launch {
        // Hermes requires unique session titles, so stamp each one with the time.
        val title = "Kompakt ${uniqueStamp()}"
        runCatching { client.createSession(title) }
            .onSuccess { onOpen(it, title, client.profile.slug, "api_server") }
            .onFailure { status = "Could not start a session: ${it.message}" }
    }

    Column(Modifier.fillMaxSize()) {
        // Like the Messages list: one bold title (the profile, as Messages shows "SMS"), plain
        // icons, and the main action as a filled "+".
        MessagesTopBar(label = "", title = profileName) {
            // Cycles through profiles; with only the default profile there is nothing to switch.
            if (settings.profiles.size > 1) BarIcon(Icons.Outlined.Person, "Switch profile") { switchProfile() }
            BarIcon(Icons.Outlined.Refresh, "Refresh") { refresh() }
            BarIcon(Icons.Outlined.Settings, "Settings", onSettings)
            PrimaryBarIcon(Icons.Outlined.Add, "New session") { newSession() }
        }
        if (status.isNotBlank()) TextMMD(status, modifier = Modifier.padding(16.dp))
        LazyColumnMMD(Modifier.fillMaxSize()) {
            items(sessions, key = { it.id }) { s ->
                // Messages-list row: bold name on one line, date at the right, then one line of
                // preview (Hermes's preview, else the source), flagged when read-only.
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { onOpen(s.id, s.title, client.profile.slug, s.source) }
                        // No end padding: the scrollbar already sits to the right of the row.
                        .padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextMMD(
                            s.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextMMD(
                            formatUpdated(s.updated, fmt),
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val preview = s.preview.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: s.source
                    TextMMD(
                        if (s.isReadOnly) "read-only · $preview" else preview,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DashedRowDivider()
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
    var working by remember { mutableStateOf<String?>("Loading…") }

    // Explicit flags rather than the status text, which changes while a turn runs.
    var historyLoaded by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(sessionId) {
        runCatching { client.messages(sessionId) }
            .onSuccess {
                messages.clear()
                messages.addAll(it)
                working = null
            }
            .onFailure { working = "Could not load history: ${it.message}" }
        // Sending waits for this, so the history snapshot can't replace a message sent meanwhile.
        historyLoaded = true
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || !historyLoaded || sending) return
        // Close the keyboard so the reply has the whole screen.
        keyboard?.hide()
        focusManager.clearFocus()
        draft = ""
        messages.add(ChatMessage("user", text, System.currentTimeMillis()))
        working = "Thinking…"
        sending = true
        scope.launch {
            try {
                runCatching {
                    client.chatStream(sessionId, text).collect { e ->
                        when (e) {
                            // Show which tool is running, but don't repaint per token.
                            is ChatEvent.Tool ->
                                working = "Working: ${e.name}…" + if (e.detail.isNotBlank()) " ${e.detail}" else ""
                            is ChatEvent.Done -> {
                                e.finalText?.let { messages.add(ChatMessage("assistant", it, System.currentTimeMillis())) }
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
            } finally {
                sending = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Like a Messages thread: who you're talking to (the profile) above the conversation.
        MessagesTopBar(label = profileName, title = title, onBack = onBack)
        // Newest at the bottom, like a Messages thread; jump there when messages change.
        val listState = rememberLazyListState()
        LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1) }
        val timeFmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
        val dayFmt = remember { java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()) }
        // Scrolls like a Messages thread: follows the finger (scrollStep = 0), no scrollbar. The
        // default (jump 4 items, aligned to an item's top) never shows the rest of a long reply.
        LazyColumnMMD(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            scrollStep = 0,
            isScrollbarVisible = false,
        ) {
            itemsIndexed(messages) { i, m ->
                val day = m.time?.let { dayFmt.format(Date(it)) }
                val previousDay = messages.getOrNull(i - 1)?.time?.let { dayFmt.format(Date(it)) }
                if (day != null && day != previousDay) DaySeparator(day)
                MessageBubble(m.text, mine = m.role == "user", time = m.time?.let { timeFmt.format(Date(it)) })
            }
        }
        working?.let { TextMMD(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        if (readOnly) {
            // Hermes refuses API turns on sessions owned by cron, kanban or messaging; fork instead.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDividerMMD(thickness = 3.dp)
                TextMMD("Started from $source, so it's read-only here.", modifier = Modifier.padding(horizontal = 12.dp))
                ButtonMMD(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), onClick = {
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
            ComposeBar {
                MessageField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = "Message",
                    onSend = { send() },
                    modifier = Modifier.weight(1f),
                )
                SendButton(onClick = { send() })
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
        MessagesTopBar(label = "Hermes", title = "Settings")
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

/** Like a messages list: today's times as "4:19 PM", older ones as "23 Nov". */
private class RowTimeFormat {
    private val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    private val day = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())

    fun format(date: Date): String {
        val today = android.text.format.DateUtils.isToday(date.time)
        return if (today) time.format(date) else day.format(date)
    }
}

/** Hermes reports times as epoch seconds (e.g. `1.7954E9`); show those as a date, else as given. */
private fun formatUpdated(raw: String, fmt: RowTimeFormat): String =
    raw.toDoubleOrNull()?.let { fmt.format(Date((it * 1000).toLong())) } ?: raw

/** Date and time to the second, e.g. "Nov 23 16:21:05", to keep session titles unique. */
private fun uniqueStamp(): String = java.text.SimpleDateFormat("MMM d HH:mm:ss", java.util.Locale.getDefault()).format(Date())
