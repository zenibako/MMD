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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
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
    // From a link: the message to open at instead of the newest.
    anchorMessageId: String? = null,
) {
    // A chat opened from a link starts without its source; the session row fills it in.
    var sessionSource by remember { mutableStateOf(source) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Hermes names a new session from its first message; shown once it has.
    var shownTitle by remember { mutableStateOf(title) }
    val readOnly = isReadOnlySession(sessionSource, shownTitle.ifBlank { title })
    val client = remember {
        val settings = SettingsStore.load(context)
        HermesClient(settings, settings.profile(profileSlug))
    }
    val profileName = client.profileLabel()
    // The model picked for this session; null is the profile's default. Shown from what the
    // gateway reports (its pin in GET /api/sessions/{id}), with the app's own record only as a
    // first guess until that answer arrives. Keyed per server, profile and session, as are the
    // draft and the reading position.
    val modelKey = remember { sessionModelKey(SettingsStore.load(context).baseUrl, profileSlug, sessionId) }
    var model by remember { mutableStateOf(SettingsStore.sessionModel(context, modelKey)) }
    var gatewayPinned by remember { mutableStateOf<ModelChoice?>(null) }
    var gatewayChecked by remember { mutableStateOf(false) }
    // How this gateway clears a pin (from /v1/capabilities); null: it can't (Hermes 0.23.4).
    var clearEndpoint by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Re-reads the session row: its pin, and the source and title a linked chat starts without.
    fun refreshSessionRow() {
        scope.launch {
            val row = client.session(sessionId) ?: return@launch
            if (sessionSource.isBlank()) sessionSource = row.str("source")
            if (shownTitle.isBlank()) row.str("title").takeIf { it.isNotBlank() }?.let { shownTitle = it }
            gatewayPinned = gatewayPin(row)
            gatewayChecked = true
        }
    }
    LaunchedEffect(sessionId) {
        clearEndpoint = client.capabilities()?.let(::clearModelEndpoint)
        refreshSessionRow()
    }
    LaunchedEffect(Unit) { client.profileName() }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    // What you were typing, kept across leaving the chat, sleep and alerts.
    var draft by remember {
        val saved = SettingsStore.draft(context, modelKey)
        mutableStateOf(TextFieldValue(saved, TextRange(saved.length)))
    }
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
    // The gateway's pin wins over the app's record once both it and the profile default are known.
    LaunchedEffect(gatewayChecked, gatewayPinned, catalog) {
        if (!gatewayChecked) return@LaunchedEffect
        val chosen = selectedModel(gatewayPinned, model, catalog?.second)
        if (chosen != model) {
            model = chosen
            SettingsStore.setSessionModel(context, modelKey, chosen)
        }
    }

    fun openModelSheet() {
        modelSheet = true
        loadCatalog()
    }

    // A failed turn: the message that failed and one readable line about why, shown under it.
    var failure by remember { mutableStateOf<Pair<String, String>?>(null) }
    // The reply so far while it streams, in whole paragraphs only (see completedParagraphs).
    var live by remember { mutableStateOf<String?>(null) }
    // A tool call the run is waiting on you to allow, and the quiet line once it's answered.
    var approval by remember { mutableStateOf<ApprovalRequest?>(null) }
    var approvalNote by remember { mutableStateOf<String?>(null) }
    var approveSheet by remember { mutableStateOf(false) }
    var answering by remember { mutableStateOf(false) }

    // A waiting approval that the run moved past was answered somewhere else.
    fun approvalMovedOn() {
        if (approval != null) {
            approvalNote = approvalOutcomeLine(ApprovalOutcome.AlreadyResolved, approval!!.tool)
            approval = null
            approveSheet = false
        }
    }

    fun answerApproval(choice: String) {
        val request = approval ?: return
        if (answering) return
        answering = true
        scope.launch {
            val outcome = try {
                client.answerApproval(request, choice)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApprovalOutcome.Failed(connectionError("no answer from Hermes", e))
            }
            answering = false
            approvalNote = approvalOutcomeLine(outcome, request.tool)
            // Keep the card on a failure, so you can try again or answer elsewhere.
            if (outcome !is ApprovalOutcome.Failed && approval == request) {
                approval = null
                if (sending) working = "Working…"
            }
        }
    }

    // After the connection dropped: if Hermes finished the reply anyway, show the history with
    // it instead of a failure, so Retry never sends a message twice. Else the failure line.
    suspend fun reconcileAfterDrop(text: String, reason: String) {
        working = "Checking whether Hermes finished…"
        val history = runCatching { client.messages(sessionId) }.getOrNull()
        working = null
        if (history != null && completedReplyAfter(history, text) != null) {
            messages.clear()
            messages.addAll(history)
            failure = null
        } else {
            failure = text to reason
        }
    }

    // Runs one turn for [text], already shown as your message.
    fun runTurn(text: String) {
        failure = null
        live = null
        approval = null
        approvalNote = null
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
                                // A tool finishing means the run got past the approval.
                                if (e.phase == "completed" || e.phase == "failed") approvalMovedOn()
                                working = "Working: ${e.name}…" + if (e.detail.isNotBlank()) " ${e.detail}" else ""
                                usedTools += e.name
                            }
                            // Redraw only when a paragraph completes.
                            is ChatEvent.Delta -> {
                                approvalMovedOn()
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
                                approval = null
                                approveSheet = false
                                if (shownTitle.isBlank()) refreshTitle()
                                refreshSessionRow()
                            }
                            is ChatEvent.Approval -> {
                                approval = e.request
                                approvalNote = null
                                working = "Waiting for your approval"
                            }
                            is ChatEvent.Failed -> {
                                live = null
                                working = null
                                approval = null
                                approveSheet = false
                                if (e.connectionLost) {
                                    reconcileAfterDrop(text, runFailureLine(e.reason))
                                } else {
                                    failure = text to runFailureLine(e.reason)
                                }
                            }
                        }
                    }
                }.onFailure {
                    if (it is CancellationException) throw it
                    live = null
                    working = null
                    // A refused turn (e.g. 409 session_active_turn while another window is
                    // answering) is a failure line with Retry like any other. A dropped
                    // connection may have left a finished reply behind: check first.
                    if (it is HermesApiException || it is ProfileUnknownException) {
                        failure = text to sendFailureLine(it)
                    } else {
                        reconcileAfterDrop(text, sendFailureLine(it))
                    }
                }
            } finally {
                sending = false
            }
        }
    }

    // @mentions route only in bot rooms and Bot Chat. Known handles: the phone's profiles and
    // agents that wrote into this chat; a bot room also takes @all and @everyone.
    val mentionChat = mentionsRoute(sessionSource, shownTitle.ifBlank { title })
    val mentionCandidates = remember(mentionChat, messages.size) {
        if (!mentionChat) {
            emptyList()
        } else {
            val settings = SettingsStore.load(context)
            val fromProfiles = profileCandidates(settings.profiles.map { it.slug to ProfileNames.label(settings.baseUrl, it) })
            val seen = messages.mapNotNull { m -> m.senderHandle?.let { MentionCandidate(it, m.sender ?: it) } }
            (fromProfiles + seen).distinctBy { it.handle.lowercase() }
        }
    }
    val knownHandles = remember(mentionCandidates, sessionSource) {
        mentionCandidates.map { it.handle }.toSet() + if (sessionSource == "bot_room") ROOM_WIDE_HANDLES else emptySet()
    }
    // A send held back because a mention names nobody here; Send anyway goes ahead.
    var mentionWarning by remember { mutableStateOf<String?>(null) }

    fun send(force: Boolean = false) {
        val text = draft.text.trim()
        if (text.isEmpty() || !historyLoaded || sending) return
        if (mentionChat && !force) {
            val issues = unknownMentions(text, knownHandles)
            if (issues.isNotEmpty()) {
                mentionWarning = mentionNotice(issues)
                return
            }
        }
        mentionWarning = null
        // Close the keyboard so the reply has the whole screen.
        keyboard?.hide()
        focusManager.clearFocus()
        draft = TextFieldValue("")
        SettingsStore.setDraft(context, modelKey, "")
        messages.add(ChatMessage("user", text, System.currentTimeMillis()))
        runTurn(text)
    }

    // Sends the failed message again to this session; your message stays where it is. Checks
    // the history first, in case the reply finished after all.
    fun retry() {
        val text = failure?.first ?: return
        if (sending) return
        sending = true
        scope.launch {
            val history = runCatching { client.messages(sessionId) }.getOrNull()
            sending = false
            if (history != null && completedReplyAfter(history, text) != null) {
                messages.clear()
                messages.addAll(history)
                failure = null
            } else {
                runTurn(text)
            }
        }
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
        // A gateway that can clear a pin goes back to inheriting the default.
        val clear = clearEndpoint
        if (choice == null && clear != null) {
            modelSaving = true
            lockError = null
            scope.launch {
                runCatching { client.clearSessionModel(sessionId, clear) }
                    .onSuccess {
                        model = null
                        gatewayPinned = null
                        SettingsStore.setSessionModel(context, modelKey, null)
                        modelSheet = false
                    }
                    .onFailure { lockError = connectionError("Hermes didn't clear the model", it) }
                modelSaving = false
            }
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
                    gatewayPinned = target
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
    val rowCount = shown.size + listOf(live, approval, approvalNote, failure).count { it != null }
    // Something new landed below while you were reading further up.
    var unseenBelow by remember { mutableStateOf(false) }
    LaunchedEffect(followBottom) { if (followBottom) unseenBelow = false }
    LaunchedEffect(rowCount, live?.length) { if (!followBottom && rowCount > 0) unseenBelow = true }

    fun goToLatest() {
        followBottom = true
        unseenBelow = false
        if (rowCount > 0) scope.launch { listState.scrollToItem(rowCount - 1, scrollOffset = Int.MAX_VALUE) }
    }

    fun goToStart() {
        followBottom = false
        if (rowCount > 0) scope.launch { listState.scrollToItem(0) }
    }

    // Remember where you were reading when the chat goes out of view (leaving it, sleep, an
    // alert or another app on top); at the newest message there is nothing to keep.
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentRowCount by rememberUpdatedState(rowCount)
    val currentShowTools by rememberUpdatedState(showTools)
    fun savePosition() {
        if (!historyLoaded) return
        val atBottom = followBottom || !listState.canScrollForward
        SettingsStore.setReadingPosition(
            context,
            modelKey,
            if (atBottom) {
                null
            } else {
                ReadingPosition(
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset,
                    currentRowCount,
                    messages.lastOrNull()?.time,
                    currentShowTools,
                )
            },
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) savePosition()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            savePosition()
        }
    }

    // Once the history is in: open at a linked message, else where you left off if the chat
    // hasn't changed since, else (below) at the newest message.
    var openPending by remember { mutableStateOf(true) }
    LaunchedEffect(rowCount, live?.length, showTools, historyLoaded) {
        if (historyLoaded && openPending && rowCount > 0) {
            openPending = false
            val anchor = anchorMessageId?.let { id -> shown.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
            val restore = positionToRestore(SettingsStore.readingPosition(context, modelKey), rowCount, messages.lastOrNull()?.time, showTools)
            if (anchor != null || restore != null) {
                followBottom = false
                if (anchor != null) listState.scrollToItem(anchor) else listState.scrollToItem(restore!!.index, restore.offset)
                return@LaunchedEffect
            }
        }
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
        // Scrolls like a Messages thread: follows the finger (scrollStep = 0); the default (jump 4
        // items, aligned to an item's top) never shows the rest of a long reply. Once the chat is
        // longer than the screen, the stock ▲/▼ column pages one screen per tap (long-press
        // ▲ for the start), with the pill scrollbar between them.
        LazyColumnMMD(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            scrollStep = 0,
            isScrollbarVisible = true,
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
                            // Agent replies and messages from other agents are Markdown.
                            markdown = m.role != "user",
                            time = listOfNotNull(m.time?.let { timeOfDayLabel(it, zone, locale, use24h) }, m.footer)
                                .joinToString(" · "),
                            // Same author next, on the same day: group the two 2 dp apart.
                            sameAuthorAsNext = shown.getOrNull(i + 1)?.let { n ->
                                n.kind == MessageKind.CONVERSATION && n.role == m.role && n.sender == m.sender &&
                                    n.time?.let { localDay(it, zone) } == day
                            } == true,
                            // Another agent's name, once at the start of its run of messages.
                            sender = m.sender?.takeUnless {
                                shown.getOrNull(i - 1)?.let { p -> p.kind == MessageKind.CONVERSATION && p.sender == m.sender } == true
                            },
                        )
                    m.kind == MessageKind.TOOL_CALL && !showTools -> ToolSummaryLine(m.text)
                    else -> EventLine(m.label, m.text)
                }
            }
            live?.let { text -> item(key = "live") { MessageBubble(text, mine = false, time = null, markdown = true) } }
            approval?.let { request ->
                item(key = "approval") {
                    ApprovalCard(
                        request,
                        enabled = !answering,
                        onDeny = { answerApproval("deny") },
                        onApprove = {
                            val ways = request.approveChoices
                            if (ways.size > 1) approveSheet = true else answerApproval(ways.first())
                        },
                    )
                }
            }
            approvalNote?.let { note -> item(key = "approvalNote") { ToolSummaryLine(note) } }
            failure?.let { (_, line) ->
                item(key = "failure") { FailureLine(line, retryEnabled = !sending) { retry() } }
            }
        }
        latestButtonLabel(atBottom = followBottom || !listState.canScrollForward, unseenBelow = unseenBelow)?.let { label ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp), horizontalArrangement = Arrangement.End) {
                SmallOutlinedButton(label) { goToLatest() }
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
            // Typing @… in a chat where mentions route: matching bots, tap to insert.
            val query = if (mentionChat) activeMentionQuery(draft.text, draft.selection.end) else null
            val suggestions = query?.let { mentionSuggestions(it.prefix, mentionCandidates) }.orEmpty()
            if (query != null && suggestions.isNotEmpty()) {
                MentionSuggestions(suggestions) { c ->
                    val (text, cursor) = applyMention(draft.text, query, draft.selection.end, c.handle)
                    draft = TextFieldValue(text, TextRange(cursor))
                    SettingsStore.setDraft(context, modelKey, text)
                    mentionWarning = null
                }
            }
            mentionWarning?.let { warning ->
                MentionWarningLine(warning, onSendAnyway = { send(force = true) }, onEdit = { mentionWarning = null })
            }
            ComposeBar {
                MessageField(
                    value = draft,
                    onValueChange = {
                        if (it.text != draft.text) mentionWarning = null
                        draft = it
                        SettingsStore.setDraft(context, modelKey, it.text)
                    },
                    placeholder = "Message",
                    onSend = { send() },
                    modifier = Modifier.weight(1f),
                    bold = { text ->
                        if (!mentionChat) {
                            emptyList()
                        } else {
                            mentionsIn(text).filter { (_, h) -> knownHandles.any { it.equals(h, ignoreCase = true) } }.map { it.first }
                        }
                    },
                )
                SendButton(onClick = { send() }, enabled = historyLoaded && !sending)
            }
        }
    }
        if (chatSheet) {
            KompaktSheet("Chat", onDismiss = { chatSheet = false }) {
                SheetButton("Go to first message") {
                    chatSheet = false
                    goToStart()
                }
                SheetButton("Go to latest") {
                    chatSheet = false
                    goToLatest()
                }
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
        approval?.takeIf { approveSheet }?.let { request ->
            KompaktSheet("Approve ${request.tool}?", onDismiss = { approveSheet = false }) {
                request.approveChoices.forEach { choice ->
                    SheetButton(approveChoiceLabel(choice)) {
                        approveSheet = false
                        answerApproval(choice)
                    }
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
                // Said before the tap: this gateway can only pin the default, not follow it.
                if (clearEndpoint == null) {
                    c?.second?.let {
                        TextMMD(
                            pinsDefaultNote(it.model),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                lockError?.let { TextMMD(it, style = MaterialTheme.typography.bodyLarge) }
                if (modelSaving) TextMMD("Saving…", style = MaterialTheme.typography.bodyLarge)
                when {
                    c == null && catalogError != null -> TextMMD(catalogError.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    c == null -> TextMMD("Loading…", style = MaterialTheme.typography.bodyLarge)
                    // The stock paged list: ▲/▼ and the pill scrollbar appear when it overflows.
                    // Sized to its rows up to a cap, since the list fills whatever height it gets.
                    // The cap (about three and a half two-line rows) keeps the whole sheet below
                    // the chat header, so the model line you tapped stays in view.
                    else -> LazyColumnMMD(
                        Modifier.fillMaxWidth().height((c.first.size * 56).coerceIn(56, 232).dp),
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
            style = MaterialTheme.typography.bodyMedium.scaledForMessages(),
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedSquareButton(Icons.Outlined.Refresh, "Retry", enabled = retryEnabled, onClick = onRetry)
    }
}

/**
 * A tool call waiting on you, inline in the chat like an info card (2 dp outline, 8 dp corners):
 * what wants to run and one line about it, then an outlined Deny and the one black Approve.
 */
@Composable
private fun ApprovalCard(request: ApprovalRequest, enabled: Boolean, onDeny: () -> Unit, onApprove: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextMMD("Allow ${request.tool}?", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (request.summary.isNotBlank()) {
            TextMMD(request.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { SheetButton("Deny") { if (enabled) onDeny() } }
            Box(Modifier.weight(1f)) { SheetButton("Approve", filled = true) { if (enabled) onApprove() } }
        }
    }
}

/** A small (36 dp) outlined button with 8 dp corners and a 21 sp Black label, as in the stock apps. */
@Composable
private fun SmallOutlinedButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .heightIn(min = 36.dp)
                .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            TextMMD(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Bots matching the @word being typed, above the compose bar: outlined 48 dp rows with the
 * handle in Black and the name after it, at most four (from [mentionSuggestions]).
 */
@Composable
private fun MentionSuggestions(suggestions: List<MentionCandidate>, onPick: (MentionCandidate) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        suggestions.forEach { c ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .border(2.dp, Color.Black, RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "Mention ${c.name}") { onPick(c) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextMMD("@${c.handle}", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                if (!c.name.equals(c.handle, ignoreCase = true)) {
                    TextMMD(
                        "  ${c.name}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A held send: one line on what's wrong with a mention, then Edit and Send anyway. */
@Composable
private fun MentionWarningLine(text: String, onSendAnyway: () -> Unit, onEdit: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TextMMD(text, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { SheetButton("Edit") { onEdit() } }
            Box(Modifier.weight(1f)) { SheetButton("Send anyway") { onSendAnyway() } }
        }
    }
}

/** "Used web_search, read_file": one small line between messages where the agent used tools. */
@Composable
private fun ToolSummaryLine(text: String) {
    TextMMD(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium.scaledForMessages(),
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
internal fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
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
    is ProfileUnknownException -> profileUnknownLine(what, e.slug)
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
internal fun KompaktSheet(
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
internal fun SheetButton(label: String, filled: Boolean = false, onClick: () -> Unit) {
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
