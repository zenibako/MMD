@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.mudita.mmd.samples.hermes

import androidx.compose.foundation.clickable
import com.mudita.mmd.components.switcher.SwitchMMD
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.widthIn
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.filled.MoreVert
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Edit
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
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val profileName = client.profileLabel()
    LaunchedEffect(client) { client.profileName() }
    var sessions by remember { mutableStateOf<List<SessionSummary>>(emptyList()) }
    var hiddenGroups by remember { mutableStateOf(SettingsStore.hiddenSessionGroups(context)) }
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
                .onFailure { status = connectionError("Couldn't load sessions", it) }
        }
    }
    LaunchedEffect(client) {
        sessions = emptyList()
        refresh()
    }

    fun switchProfile(to: HermesProfile) {
        if (to.slug == settings.active.slug) return
        SettingsStore.setActiveProfile(context, to.slug)
        settings = settings.copy(activeProfile = to.slug)
    }

    fun newSession() {
        val c = client
        scope.launch {
            // No title: Hermes names the session from its first message (auto-titling).
            runCatching { c.createSession() }
                .onSuccess { if (c === currentClient) onOpen(it, "", c.profile.slug, "api_server") }
                // An error from a profile we've since left belongs to that profile, not this list.
                .onFailure { if (c === currentClient) status = connectionError("Couldn't start a session", it) }
        }
    }

    var profileMenu by remember { mutableStateOf(false) }
    var moreSheet by remember { mutableStateOf(false) }
    // The session whose long-press sheet (pin / unpin) is open.
    var sessionSheet by remember { mutableStateOf<SessionSummary?>(null) }
    var deleteConfirm by remember { mutableStateOf<SessionSummary?>(null) }
    var filterSheet by remember { mutableStateOf(false) }

    fun delete(s: SessionSummary) {
        val c = client
        scope.launch {
            runCatching { c.deleteSession(s.id) }
                .onSuccess { if (c === currentClient) refresh() }
                .onFailure { if (c === currentClient) status = connectionError("Couldn't delete the session", it) }
        }
    }

    fun setPinned(s: SessionSummary, pinned: Boolean) {
        val c = client
        scope.launch {
            runCatching { c.setPinned(s.id, pinned) }
                .onSuccess { if (c === currentClient) refresh() }
                .onFailure { if (c === currentClient) status = connectionError("Couldn't update the pin", it) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Like the Messages list: one bold title (the profile, as Messages shows "SMS"), plain
            // icons, and the main action as a filled "+".
            // The profile name is the switcher (with more than one profile), so the name gets the
            // width a separate icon would take.
            MessagesTopBar(
                label = "",
                title = profileName,
                onTitleClick = if (settings.profiles.size > 1) ({ profileMenu = true }) else null,
                titleClickLabel = "Switch profile",
                titleMenu = {
                    ProfileDropdown(
                        settings,
                        expanded = profileMenu,
                        onSelect = {
                            profileMenu = false
                            switchProfile(it)
                        },
                        onDismiss = { profileMenu = false },
                    )
                },
            ) {
                // Refresh and Settings live in the ⋮ sheet, leaving the width to the name.
                BarIcon(Icons.Filled.MoreVert, "More") { moreSheet = true }
                PrimaryBarIcon(Icons.Outlined.Add, "New session") { newSession() }
            }
            if (status.isNotBlank()) TextMMD(status, modifier = Modifier.padding(16.dp))
            LazyColumnMMD(Modifier.fillMaxSize()) {
                items(filterSessions(sessions, hiddenGroups), key = { it.id }) { s ->
                    // Messages-list row: bold name on one line, date at the right, then one line of
                    // preview (Hermes's preview, else the source), flagged when read-only.
                    Column(
                        Modifier.fillMaxWidth()
                            .combinedClickable(
                                onClickLabel = "Open",
                                onLongClickLabel = if (s.pinned) "Unpin" else "Pin",
                                onLongClick = { sessionSheet = s },
                                onClick = { onOpen(s.id, s.title, client.profile.slug, s.source) },
                            )
                            // No end padding: the scrollbar already sits to the right of the row.
                            .padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (s.pinned) {
                                Icon(PinOutline, contentDescription = "Pinned", tint = Color.Black, modifier = Modifier.padding(end = 6.dp).size(20.dp))
                            }
                            TextMMD(
                                sessionTitleOrPlaceholder(s.title),
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
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (s.isReadOnly) ReadOnlyIcons(s.source, 18.dp)
                            TextMMD(
                                preview,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    DashedRowDivider()
                }
            }
        }
        sessionSheet?.let { s ->
            KompaktSheet(sessionTitleOrPlaceholder(s.title), onDismiss = { sessionSheet = null }) {
                SheetButton(if (s.pinned) "Unpin" else "Pin to top") {
                    sessionSheet = null
                    setPinned(s, !s.pinned)
                }
                SheetButton("Delete") {
                    sessionSheet = null
                    deleteConfirm = s
                }
            }
        }
        deleteConfirm?.let { s ->
            KompaktSheet("Delete this session?", onDismiss = { deleteConfirm = null }, cancelFilled = false) {
                TextMMD(
                    sessionTitleOrPlaceholder(s.title),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                SheetButton("Delete", filled = true) {
                    deleteConfirm = null
                    delete(s)
                }
            }
        }
        if (filterSheet) {
            KompaktSheet("Show", onDismiss = { filterSheet = false }, cancelLabel = "Done") {
                SessionGroup.entries.forEach { g ->
                    SheetToggle(g.label, checked = g !in hiddenGroups) { show ->
                        hiddenGroups = if (show) hiddenGroups - g else hiddenGroups + g
                        SettingsStore.setHiddenSessionGroups(context, hiddenGroups)
                    }
                }
            }
        }
        if (moreSheet) {
            KompaktSheet("Menu", onDismiss = { moreSheet = false }) {
                SheetButton("Refresh") {
                    moreSheet = false
                    refresh()
                }
                SheetButton(if (hiddenGroups.isEmpty()) "Filter" else "Filter (some hidden)") {
                    moreSheet = false
                    filterSheet = true
                }
                SheetButton("Settings") {
                    moreSheet = false
                    onSettings()
                }
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
    // Hermes names a new session from its first message; shown once it has.
    var shownTitle by remember { mutableStateOf(title) }
    val client = remember {
        val settings = SettingsStore.load(context)
        HermesClient(settings, settings.profile(profileSlug))
    }
    val profileName = client.profileLabel()
    // The model this session runs on: saved per server, profile and session; null is the
    // profile's default. Sent with each run as a one-off override, so "Profile default" is
    // simply not sending one.
    val modelKey = remember { sessionModelKey(SettingsStore.load(context).baseUrl, profileSlug, sessionId) }
    var model by remember { mutableStateOf(SettingsStore.sessionModel(context, modelKey)) }
    LaunchedEffect(Unit) { client.profileName() }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var draft by remember { mutableStateOf("") }
    var working by remember { mutableStateOf<String?>("Loading…") }

    // Explicit flags rather than the status text, which changes while a turn runs.
    var historyLoaded by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    // Whether new messages should scroll the chat; off while you are reading further up.
    var followBottom by remember { mutableStateOf(true) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var historyFailed by remember { mutableStateOf(false) }
    // Fork progress is shown apart from history status, which a late history load would clear.
    var forking by remember { mutableStateOf(false) }
    var forkStatus by remember { mutableStateOf<String?>(null) }
    var historyAttempt by remember { mutableStateOf(0) }
    LaunchedEffect(sessionId, historyAttempt) {
        historyFailed = false
        working = "Loading…"
        // One quiet retry: after waking or switching networks, Tailscale can take a few
        // seconds to reconnect, and the first connect times out.
        val result = runCatching { client.messages(sessionId) }.recoverCatching {
            if (it is CancellationException) throw it
            delay(3_000)
            client.messages(sessionId)
        }
        result
            .onSuccess {
                messages.clear()
                messages.addAll(it)
                working = null
                // Sending waits for this, so the history snapshot can't replace a message sent
                // meanwhile, and a later Retry can't wipe a message that never reached Hermes.
                historyLoaded = true
            }
            .onFailure {
                if (it is CancellationException) throw it
                Log.w("Hermes", "History load failed", it)
                working = connectionError("Couldn't load history", it)
                historyFailed = true
            }
    }

    // Hermes writes a quick title at the turn's start and may upgrade it with a model shortly
    // after; read it once now and once a little later.
    fun refreshTitle() {
        scope.launch {
            for (wait in listOf(0L, 8_000L)) {
                delay(wait)
                client.sessionTitle(sessionId)?.let { shownTitle = it }
            }
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || !historyLoaded || sending) return
        // Close the keyboard so the reply has the whole screen.
        keyboard?.hide()
        focusManager.clearFocus()
        draft = ""
        messages.add(ChatMessage("user", text, System.currentTimeMillis()))
        // You just sent it: show it, even if you had scrolled up.
        followBottom = true
        working = "Thinking…"
        sending = true
        scope.launch {
            try {
                // Tools this turn used, for the "Used …" line placed before the reply.
                val usedTools = mutableListOf<String>()
                runCatching {
                    client.chatStream(sessionId, text, model).collect { e ->
                        when (e) {
                            // Show which tool is running, but don't repaint per token.
                            is ChatEvent.Tool -> {
                                working = "Working: ${e.name}…" + if (e.detail.isNotBlank()) " ${e.detail}" else ""
                                usedTools += e.name
                            }
                            is ChatEvent.Done -> {
                                toolCallEntry(usedTools, System.currentTimeMillis())?.let { messages.add(it) }
                                e.finalText?.let { messages.add(ChatMessage("assistant", it, System.currentTimeMillis())) }
                                working = null
                                if (shownTitle.isBlank()) refreshTitle()
                            }
                            is ChatEvent.Failed -> working = model?.let { m ->
                                // A model the server can't route or the provider refuses fails the run.
                                "Run failed on ${m.model}: ${e.reason} Pick another model in ⋮."
                            } ?: "Run failed: ${e.reason}"
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

    var showTools by remember { mutableStateOf(SettingsStore.showToolMessages(context)) }
    var chatSheet by remember { mutableStateOf(false) }
    var modelSheet by remember { mutableStateOf(false) }
    // Loaded when the model sheet opens: the choices and the profile's default.
    var catalog by remember { mutableStateOf<Pair<List<ModelChoice>, ModelChoice?>?>(null) }
    var catalogError by remember { mutableStateOf<String?>(null) }

    fun openModelSheet() {
        modelSheet = true
        if (catalog != null) return
        catalogError = null
        scope.launch {
            runCatching { client.modelOptions() }
                .onSuccess { (providers, default) -> catalog = modelChoices(providers) to default }
                .onFailure { catalogError = connectionError("Couldn't load models", it) }
        }
    }

    fun chooseModel(choice: ModelChoice?) {
        model = choice
        SettingsStore.setSessionModel(context, modelKey, choice)
        modelSheet = false
    }
    val shown = visibleMessages(messages, showTools)

    // Newest at the bottom, like a Messages thread. Opening (and each history load) lands on
    // the very end of the latest message; later messages keep following the bottom only while
    // the reader is there, so scrolling up to read isn't undone when a reply arrives.
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) followBottom = !listState.canScrollForward
        }
    }
    LaunchedEffect(shown.size, showTools, historyLoaded) {
        if (shown.isNotEmpty() && followBottom) {
            // The offset is clamped, so this shows the end of the last item, not just its top.
            listState.scrollToItem(shown.size - 1, scrollOffset = Int.MAX_VALUE)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        // Like a Messages thread: who you're talking to (the profile) above the conversation.
        MessagesTopBar(
            // The model shows only when this chat overrides the profile's default.
            label = model?.let { "$profileName · ${it.model}" } ?: profileName,
            title = sessionTitleOrPlaceholder(shownTitle),
            onBack = onBack,
        ) {
            BarIcon(Icons.Filled.MoreVert, "More") { chatSheet = true }
        }
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
            itemsIndexed(shown) { i, m ->
                val day = m.time?.let { dayFmt.format(Date(it)) }
                val previousDay = shown.getOrNull(i - 1)?.time?.let { dayFmt.format(Date(it)) }
                if (day != null && day != previousDay) DaySeparator(day)
                when {
                    m.kind == MessageKind.CONVERSATION ->
                        MessageBubble(m.text, mine = m.role == "user", time = m.time?.let { timeFmt.format(Date(it)) })
                    m.kind == MessageKind.TOOL_CALL && !showTools -> ToolSummaryLine(m.text)
                    else -> EventLine(m.label, m.text)
                }
            }
        }
        (forkStatus ?: working)?.let {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextMMD(it, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                if (historyFailed && !sending) {
                    BarIcon(Icons.Outlined.Refresh, "Retry") {
                        historyLoaded = false
                        historyAttempt++
                    }
                }
            }
        }
        if (readOnly) {
            // Hermes refuses API turns on sessions owned by cron, kanban or messaging. In place
            // of the compose bar: a lock and the source's icon, and a pencil that copies the
            // session into a writable one ("Continue here").
            ComposeBar {
                ReadOnlyIcons(source, 24.dp)
                Spacer(Modifier.weight(1f))
                OutlinedSquareButton(Icons.Outlined.Edit, "Continue here", enabled = !forking) {
                    forking = true
                    forkStatus = "Copying…"
                    scope.launch {
                        val newTitle = "${sessionTitleOrPlaceholder(shownTitle)} (Kompakt ${uniqueStamp()})"
                        runCatching { client.forkSession(sessionId, newTitle) }
                            .onSuccess { onOpenSession(it, newTitle) }
                            .onFailure {
                                forkStatus = connectionError("Couldn't continue", it)
                                forking = false
                            }
                    }
                }
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
                SendButton(onClick = { send() }, enabled = historyLoaded && !sending)
            }
        }
    }
        if (chatSheet) {
            KompaktSheet("Chat", onDismiss = { chatSheet = false }) {
                SheetButton("Model: ${model?.model ?: "Profile default"}") {
                    chatSheet = false
                    openModelSheet()
                }
                SheetToggle("Show tool and system messages", checked = showTools) {
                    showTools = it
                    SettingsStore.setShowToolMessages(context, it)
                }
            }
        }
        if (modelSheet) {
            KompaktSheet("Model", onDismiss = { modelSheet = false }) {
                val c = catalog
                // Always offered, so a chat can go back to the default even if the catalog fails.
                ChoiceButton(
                    "Profile default" + (c?.second?.let { " (${it.model})" } ?: ""),
                    selected = model == null,
                ) { chooseModel(null) }
                when {
                    catalogError != null -> TextMMD(catalogError.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    c == null -> TextMMD("Loading…", style = MaterialTheme.typography.bodyLarge)
                    else -> Column(
                        Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        c.first.forEach { m -> ChoiceButton(m.label, selected = m == model) { chooseModel(m) } }
                    }
                }
            }
        }
    }
}

/** "Used web_search, read_file": one small line between messages where the agent used tools. */
@Composable
private fun ToolSummaryLine(text: String) {
    TextMMD(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A tool call/result or system entry: a small bold heading over a few lines of its text. */
@Composable
private fun EventLine(label: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (label.isNotBlank()) {
            TextMMD(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        TextMMD(text, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
    DashedRowDivider()
}

/** A picker row: full-width outlined 48 dp button, label at the start, bold with a check when selected. */
@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .border(if (selected) 3.dp else 2.dp, Color.Black, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = "Current model", tint = Color.Black, modifier = Modifier.padding(start = 4.dp).size(24.dp))
        }
    }
}

/** A sheet row with a label and a switch; the whole 48 dp row toggles it. */
@Composable
private fun SheetToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = label) { onChange(!checked) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 21.sp),
        )
        SwitchMMD(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(SettingsStore.load(context)) }
    var status by remember { mutableStateOf("") }
    // Bumped by every URL edit and Test tap, so a slow Test can't overwrite a newer result.
    var testSeq by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        MessagesTopBar(label = "Hermes", title = "Settings")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field("Hermes URL", s.baseUrl) {
                s = s.copy(baseUrl = it)
                testSeq++
            }
            Field("API key (API_SERVER_KEY)", s.profiles.first().apiKey) { key ->
                s = s.copy(profiles = listOf(s.profiles.first().copy(apiKey = key)) + s.profiles.drop(1))
            }
            ProfilesSection(s.baseUrl, s.profiles) { s = s.copy(profiles = it) }
            DisplayNamesSection(s.baseUrl, s.profiles) { s = s.copy(profiles = it) }
            if (status.isNotBlank()) TextMMD(status)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButtonMMD(onClick = {
                    val seq = ++testSeq
                    if (isCleartextUrl(s.baseUrl)) {
                        status = CLEARTEXT_MESSAGE
                    } else {
                        val tested = s
                        scope.launch {
                            val result = HermesClient(tested).healthCheck()
                            if (seq != testSeq) return@launch
                            status = if (result == "OK") "Hermes is reachable." else "No answer from Hermes: $result"
                        }
                    }
                }) { TextMMD("Test") }
                ButtonMMD(onClick = {
                    if (isCleartextUrl(s.baseUrl)) {
                        status = CLEARTEXT_MESSAGE
                    } else {
                        SettingsStore.save(context, s)
                        onDone()
                    }
                }) { TextMMD("Save") }
            }
        }
    }
}

/** Extra profiles reached under `/p/<slug>/`; the default profile's key is the field above. */
@Composable
private fun ProfilesSection(baseUrl: String, profiles: List<HermesProfile>, onChange: (List<HermesProfile>) -> Unit) {
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
                "${ProfileNames.label(baseUrl, p)} · ${if (p.apiKey.isBlank()) "no key" else "key set"}",
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

/**
 * Optional custom names, one field per profile. A saved name wins over the one derived from
 * Hermes; leaving a field blank goes back to the derived name. Display only.
 */
@Composable
private fun DisplayNamesSection(baseUrl: String, profiles: List<HermesProfile>, onChange: (List<HermesProfile>) -> Unit) {
    TextMMD("Display names", fontWeight = FontWeight.Bold)
    profiles.forEach { p ->
        val derived = ProfileNames.derived(baseUrl, p.slug)
        Field("${p.slug.ifEmpty { "default" }} (blank: $derived)", p.displayName) { name ->
            onChange(profiles.map { if (it.slug == p.slug) it.copy(displayName = name) else it })
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
private fun uniqueStamp(): String =
    java.text.SimpleDateFormat("MMM d HH:mm:ss", java.util.Locale.getDefault()).format(Date()) +
        // Two taps within the same second would otherwise get the same title.
        " " + java.util.UUID.randomUUID().toString().take(4)

/** One short line for a failed request; a network failure gets a hint instead of a stack of hosts. */
fun connectionError(what: String, e: Throwable): String = when (e) {
    is java.net.SocketTimeoutException, is java.net.ConnectException, is java.net.UnknownHostException ->
        "$what. Can't reach Hermes; check Tailscale."
    else -> "$what: ${e.message ?: e.javaClass.simpleName}"
}

/**
 * A ⋮ menu as the stock apps (Calendar, Settings) show it: a full-width white sheet at the
 * bottom with a 3 dp top rule, a centred 25 sp Black title, stacked full-width outlined
 * buttons with centred 21 sp Black labels, and a filled black Cancel; 16 dp between them and
 * 12 dp at the sides. Back and a tap above the sheet close it; no dimming, which E Ink
 * renders as grey.
 */
@Composable
private fun KompaktSheet(
    title: String,
    onDismiss: () -> Unit,
    cancelLabel: String = "Cancel",
    // A confirm sheet makes its action the filled button and Cancel the outlined one.
    cancelFilled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                // Swallow taps on the sheet itself so they don't dismiss it.
                .clickable(interactionSource = null, indication = null, onClick = {}),
        ) {
            HorizontalDividerMMD(thickness = 3.dp)
            Column(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TextMMD(
                    title,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp),
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                content()
                SheetButton(cancelLabel, filled = cancelFilled, onClick = onDismiss)
            }
        }
    }
}

/** A stock button: full width, 48 dp, 12 dp corners, 2 dp outline or filled black, 21 sp Black label. */
@Composable
private fun SheetButton(label: String, filled: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (filled) Modifier.background(Color.Black, shape) else Modifier.border(2.dp, Color.Black, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(
            label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 21.sp),
            fontWeight = FontWeight.Black,
            color = if (filled) Color.White else Color.Black,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Profile picker, opened from the profile name: an outlined dropdown under the title listing
 * every profile by name, the current one bold with a check. Names may wrap to two lines.
 */
@Composable
private fun ProfileDropdown(
    settings: HermesSettings,
    expanded: Boolean,
    onSelect: (HermesProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    // Names come from each profile's /v1/models; fetch the missing ones when the menu opens.
    LaunchedEffect(expanded) {
        if (expanded) {
            settings.profiles
                .filter { ProfileNames.get(settings.baseUrl, it.slug) == null }
                .forEach { p -> launch { HermesClient(settings, p).profileName() } }
        }
    }
    DropdownMenuMMD(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 220.dp, max = (LocalConfiguration.current.screenWidthDp - 16).dp),
    ) {
        settings.profiles.forEach { p ->
            val active = p.slug == settings.active.slug
            DropdownMenuItemMMD(
                text = {
                    TextMMD(
                        ProfileNames.label(settings.baseUrl, p),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = if (active) {
                    { Icon(Icons.Outlined.Check, contentDescription = "Current profile", tint = Color.Black) }
                } else {
                    null
                },
                onClick = { onSelect(p) },
            )
        }
    }
}
