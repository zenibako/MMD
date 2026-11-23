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
    onOpen: (String, String) -> Unit,
    onInbox: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { HermesClient(SettingsStore.load(context)) }
    var sessions by remember { mutableStateOf<List<SessionSummary>>(emptyList()) }
    var status by remember { mutableStateOf("Loading…") }
    val inbox by Inbox.items.collectAsState()
    val unread = inbox.count { !it.read }

    fun refresh() = scope.launch {
        status = "Loading…"
        status = runCatching { sessions = client.listSessions(); "" }
            .getOrElse { "Could not reach Hermes: ${it.message}" }
    }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize()) {
        TopAppBarMMD(title = { TextMMD("Agent sessions") })
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ButtonMMD(onClick = {
                scope.launch {
                    runCatching { client.createSession("From Kompakt") }
                        .onSuccess { onOpen(it, "From Kompakt") }
                        .onFailure { status = "Could not start a session: ${it.message}" }
                }
            }) { TextMMD("New") }
            OutlinedButtonMMD(onClick = { refresh() }) { TextMMD("Refresh") }
            OutlinedButtonMMD(onClick = onInbox) { TextMMD(if (unread > 0) "Inbox ($unread)" else "Inbox") }
            OutlinedButtonMMD(onClick = onSettings) { TextMMD("⚙") }
        }
        if (status.isNotBlank()) TextMMD(status, modifier = Modifier.padding(16.dp))
        LazyColumnMMD(Modifier.fillMaxSize()) {
            items(sessions, key = { it.id }) { s ->
                Column(
                    Modifier.fillMaxWidth().clickable { onOpen(s.id, s.title) }.padding(16.dp),
                ) {
                    TextMMD(s.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextMMD(
                        listOf(s.source, s.updated).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDividerMMD(thickness = 1.dp)
            }
        }
    }
}

@Composable
fun ChatScreen(sessionId: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { HermesClient(SettingsStore.load(context)) }
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
                        is ChatEvent.Tool -> working = "Working: ${e.name}…"
                        is ChatEvent.Done -> {
                            e.finalText?.let { messages.add(ChatMessage("assistant", it)) }
                            working = null
                        }
                        is ChatEvent.Failed -> working = "Run failed: ${e.reason}"
                        is ChatEvent.Delta -> Unit
                    }
                }
            }.onFailure { working = "Send failed: ${it.message}" }
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
                        if (m.role == "user") "You" else "Agent",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    TextMMD(m.text)
                }
                HorizontalDividerMMD(thickness = 1.dp)
            }
        }
        working?.let { TextMMD(it, modifier = Modifier.padding(horizontal = 16.dp)) }
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

@Composable
fun InboxScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val items by Inbox.items.collectAsState()
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    LaunchedEffect(Unit) { Inbox.markAllRead(context) }

    Column(Modifier.fillMaxSize()) {
        TopAppBarMMD(
            title = { TextMMD("Inbox") },
            navigationIcon = { OutlinedButtonMMD(onClick = onBack) { TextMMD("Back") } },
        )
        if (items.isEmpty()) TextMMD("Nothing from your agent yet.", modifier = Modifier.padding(16.dp))
        LazyColumnMMD(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { i ->
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    TextMMD(
                        (if (i.priority >= 4) "! " else "") + i.title.ifBlank { "Hermes" },
                        fontWeight = FontWeight.Bold,
                    )
                    TextMMD(fmt.format(Date(i.time)), style = MaterialTheme.typography.bodySmall)
                    TextMMD(i.message)
                }
                HorizontalDividerMMD(thickness = 1.dp)
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
            Field("API key (API_SERVER_KEY)", s.apiKey) { s = s.copy(apiKey = it) }
            Field("ntfy server", s.ntfyServer) { s = s.copy(ntfyServer = it) }
            Field("ntfy topic", s.ntfyTopic) { s = s.copy(ntfyTopic = it) }
            Field("ntfy token (optional)", s.ntfyToken) { s = s.copy(ntfyToken = it) }
            Field("Urgent at ntfy priority (1-5)", s.urgentPriority.toString()) {
                it.toIntOrNull()?.let { p -> s = s.copy(urgentPriority = p) }
            }
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
                    NtfyListenerService.stop(context)
                    NtfyListenerService.start(context)
                    onDone()
                }) { TextMMD("Save") }
            }
        }
    }
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
