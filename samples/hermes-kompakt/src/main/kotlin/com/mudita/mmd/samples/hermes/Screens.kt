@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.mudita.mmd.samples.hermes

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.height
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
    val today = rememberToday()
    val use24h = android.text.format.DateFormat.is24HourFormat(context)

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
                PrimaryBarIcon("New session") { newSession() }
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
                            // End padding too: LazyColumnMMD only draws its scrollbar when the
                            // list can scroll, so a short list would put the time at the edge.
                            // Stock message-list row: 72 dp, text at 16 dp.
                            .heightIn(min = 72.dp)
                            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (s.pinned) {
                                Icon(PinOutline, contentDescription = "Pinned", tint = Color.Black, modifier = Modifier.padding(end = 6.dp).size(20.dp))
                            }
                            TextMMD(
                                sessionTitleOrPlaceholder(s.title),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextMMD(
                                formatUpdated(s.updated, today, use24h),
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
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
    // The model picked for this session: locked on the server (see lockSessionModel) and
    // remembered here per server, profile and session for display; null is the profile's
    // default.
    val modelKey = remember { sessionModelKey(SettingsStore.load(context).baseUrl, profileSlug, sessionId) }
    var model by remember { mutableStateOf(SettingsStore.sessionModel(context, modelKey)) }
    // A pick saved by an older build was never locked on the server: lock it once on open, and
    // forget it if Hermes refuses, so the header never claims a model the chat isn't using.
    LaunchedEffect(sessionId) {
        model?.let { m ->
            runCatching { client.lockSessionModel(sessionId, m) }.onFailure {
                if (it is HermesApiException) {
                    model = null
                    SettingsStore.setSessionModel(context, modelKey, null)
                }
            }
        }
    }
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

    var modelSheet by remember { mutableStateOf(false) }
    // The choices and the profile's default, loaded on open so the header can name the model a
    // chat inherits, and again from the model sheet if that first load failed.
    var catalog by remember { mutableStateOf<Pair<List<ModelChoice>, ModelChoice?>?>(null) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var catalogLoading by remember { mutableStateOf(false) }

    fun loadCatalog() {
        if (catalog != null || catalogLoading) return
        catalogLoading = true
        catalogError = null
        scope.launch {
            runCatching { client.modelOptions() }
                .onSuccess { (providers, default) -> catalog = modelChoices(providers) to default }
                .onFailure { if (it !is CancellationException) catalogError = connectionError("Couldn't load models", it) }
            catalogLoading = false
        }
    }
    LaunchedEffect(Unit) { loadCatalog() }

    fun openModelSheet() {
        modelSheet = true
        loadCatalog()
    }

    // A failed turn: the message that failed and one readable line about why, shown under it.
    var failure by remember { mutableStateOf<Pair<String, String>?>(null) }
    // The reply so far while it streams, in whole paragraphs only (see completedParagraphs).
    var live by remember { mutableStateOf<String?>(null) }

    // Runs one turn for [text], already shown as your message.
    fun runTurn(text: String) {
        failure = null
        live = null
        // You just sent it: show it, even if you had scrolled up.
        followBottom = true
        // Static: no dots or spinner, which would redraw the E Ink screen.
        working = "Thinking…"
        sending = true
        // The model this chat should answer with, to notice a fallback.
        val expected = model?.model ?: catalog?.second?.model
        scope.launch {
            try {
                // Tools this turn used, for the "Used …" line placed before the reply.
                val usedTools = mutableListOf<String>()
                val buffer = StringBuilder()
                runCatching {
                    client.chatStream(sessionId, text).collect { e ->
                        when (e) {
                            // Show which tool is running, but don't repaint per token.
                            is ChatEvent.Tool -> {
                                working = "Working: ${e.name}…" + if (e.detail.isNotBlank()) " ${e.detail}" else ""
                                usedTools += e.name
                            }
                            // Redraw only when a paragraph completes.
                            is ChatEvent.Delta -> {
                                buffer.append(e.text)
                                val ready = completedParagraphs(buffer.toString())
                                if (ready.length > (live?.length ?: 0)) {
                                    live = ready
                                    working = null
                                }
                            }
                            is ChatEvent.Done -> {
                                toolCallEntry(usedTools, System.currentTimeMillis())?.let { messages.add(it) }
                                e.finalText?.takeIf { it.isNotBlank() }?.let {
                                    messages.add(
                                        ChatMessage(
                                            "assistant",
                                            it,
                                            System.currentTimeMillis(),
                                            footer = answeredByLabel(e.servedModel, expected),
                                        ),
                                    )
                                }
                                live = null
                                working = null
                                if (shownTitle.isBlank()) refreshTitle()
                            }
                            is ChatEvent.Failed -> {
                                live = null
                                working = null
                                failure = text to runFailureLine(e.reason)
                            }
                        }
                    }
                }.onFailure {
                    if (it is CancellationException) throw it
                    live = null
                    if ((it as? HermesApiException)?.code == 409) {
                        working = "This session belongs to another surface. Use Continue here."
                    } else {
                        working = null
                        failure = text to connectionError("Couldn't send", it)
                    }
                }
            } finally {
                sending = false
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
        runTurn(text)
    }

    // Sends the failed message again to this session; your message stays where it is.
    fun retry() {
        val text = failure?.first ?: return
        if (!sending) runTurn(text)
    }

    var showTools by remember { mutableStateOf(SettingsStore.showToolMessages(context)) }
    var chatSheet by remember { mutableStateOf(false) }

    var modelSaving by remember { mutableStateOf(false) }
    var lockError by remember { mutableStateOf<String?>(null) }

    // Shown as picked only once Hermes has accepted the lock.
    fun chooseModel(choice: ModelChoice?) {
        if (choice == model || modelSaving) {
            modelSheet = false
            return
        }
        val target = lockTarget(choice, catalog?.second)
        if (target == null) {
            lockError = "Hermes didn't say which model is the profile's default."
            return
        }
        modelSaving = true
        lockError = null
        scope.launch {
            runCatching { client.lockSessionModel(sessionId, target) }
                .onSuccess {
                    model = choice
                    SettingsStore.setSessionModel(context, modelKey, choice)
                    modelSheet = false
                }
                .onFailure { lockError = connectionError("Hermes didn't accept ${target.model}", it) }
            modelSaving = false
        }
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
    // The list's rows: the messages, then the reply streaming in, then a failure line.
    val rowCount = shown.size + (if (live != null) 1 else 0) + (if (failure != null) 1 else 0)
    LaunchedEffect(rowCount, live?.length, showTools, historyLoaded) {
        if (rowCount > 0 && followBottom && !listState.isScrollInProgress) {
            // Never move the list under the reader's finger: a reply that lands mid-scroll
            // waits. The offset is clamped, so this shows the end of the last item, not just its top.
            listState.scrollToItem(rowCount - 1, scrollOffset = Int.MAX_VALUE)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        // Like a Messages thread: who you're talking to (the profile) above the conversation.
        // The profile and the model this chat answers with (picked, or the profile's default
        // once known) in the small line above the title; tapping it opens the Model sheet.
        val shownModel = model?.model ?: catalog?.second?.model
        MessagesTopBar(
            label = listOfNotNull(profileName, shownModel).joinToString(" · "),
            title = sessionTitleOrPlaceholder(shownTitle),
            onBack = onBack,
            onLabelClick = if (readOnly) null else ({ openModelSheet() }),
            labelClickLabel = "Change model",
        ) {
            BarIcon(Icons.Filled.MoreVert, "More") { chatSheet = true }
        }
        val today = rememberToday()
        val use24h = android.text.format.DateFormat.is24HourFormat(context)
        val zone = java.time.ZoneId.systemDefault()
        val locale = java.util.Locale.getDefault()
        val dayNames = remember(today) { systemDayNames() }
        // Scrolls like a Messages thread: follows the finger (scrollStep = 0), no scrollbar. The
        // default (jump 4 items, aligned to an item's top) never shows the rest of a long reply.
        LazyColumnMMD(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            scrollStep = 0,
            isScrollbarVisible = false,
        ) {
            itemsIndexed(shown) { i, m ->
                // Markers compare calendar days, not their labels.
                val day = m.time?.let { localDay(it, zone) }
                val previousDay = shown.getOrNull(i - 1)?.time?.let { localDay(it, zone) }
                if (day != null && day != previousDay) DaySeparator(dayMarkerLabel(day, today, locale, dayNames))
                when {
                    m.kind == MessageKind.CONVERSATION ->
                        MessageBubble(
                            m.text,
                            mine = m.role == "user",
                            time = listOfNotNull(m.time?.let { timeOfDayLabel(it, zone, locale, use24h) }, m.footer)
                                .joinToString(" · "),
                            // Same author next, on the same day: group the two 2 dp apart.
                            sameAuthorAsNext = shown.getOrNull(i + 1)?.let { n ->
                                n.kind == MessageKind.CONVERSATION && n.role == m.role &&
                                    n.time?.let { localDay(it, zone) } == day
                            } == true,
                        )
                    m.kind == MessageKind.TOOL_CALL && !showTools -> ToolSummaryLine(m.text)
                    else -> EventLine(m.label, m.text)
                }
            }
            live?.let { text -> item(key = "live") { MessageBubble(text, mine = false, time = null) } }
            failure?.let { (_, line) ->
                item(key = "failure") { FailureLine(line, retryEnabled = !sending) { retry() } }
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
                    "Profile default" + (c?.second?.let { " · ${it.model}" } ?: ""),
                    selected = isCurrentChoice(null, model),
                ) { chooseModel(null) }
                lockError?.let { TextMMD(it, style = MaterialTheme.typography.bodyLarge) }
                if (modelSaving) TextMMD("Saving…", style = MaterialTheme.typography.bodyLarge)
                when {
                    c == null && catalogError != null -> TextMMD(catalogError.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    c == null -> TextMMD("Loading…", style = MaterialTheme.typography.bodyLarge)
                    // The stock paged list: ▲/▼ and the pill scrollbar appear when it overflows.
                    // Sized to its rows up to a cap, since the list fills whatever height it gets.
                    else -> LazyColumnMMD(
                        Modifier.fillMaxWidth().height((c.first.size * 56).coerceIn(56, 320).dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(c.first) { m ->
                            Box(Modifier.padding(end = 4.dp)) {
                                ChoiceButton(m.label, selected = isCurrentChoice(m, model)) { chooseModel(m) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Under a message whose run failed: one plain line saying why, and an outlined Retry that sends
 * the same message again. Aligned with your messages, at the right.
 */
@Composable
private fun FailureLine(line: String, retryEnabled: Boolean, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextMMD(
            line,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedSquareButton(Icons.Outlined.Refresh, "Retry", enabled = retryEnabled, onClick = onRetry)
    }
}

/** "Used web_search, read_file": one small line between messages where the agent used tools. */
@Composable
private fun ToolSummaryLine(text: String) {
    TextMMD(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
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
            TextMMD(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        TextMMD(text, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
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
            style = if (selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
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
    // The whole labelled row is one accessible switch; the switch itself only draws.
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        SwitchMMD(checked = checked, onCheckedChange = null)
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

    TextMMD("Other profiles", style = MaterialTheme.typography.titleSmall)
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
    TextMMD("Display names", style = MaterialTheme.typography.titleSmall)
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

/** Hermes reports times as epoch seconds (e.g. `1.7954E9`); show those day-relative, else as given. */
private fun formatUpdated(raw: String, today: java.time.LocalDate, use24h: Boolean): String =
    raw.toDoubleOrNull()?.let {
        listTimeLabel((it * 1000).toLong(), today, java.time.ZoneId.systemDefault(), java.util.Locale.getDefault(), use24h, systemDayNames())
    } ?: raw

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
                    style = MaterialTheme.typography.titleLarge,
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
            style = MaterialTheme.typography.labelLarge,
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
                        fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
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
