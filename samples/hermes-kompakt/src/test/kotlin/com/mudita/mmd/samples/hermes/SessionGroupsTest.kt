package com.mudita.mmd.samples.hermes

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionGroupsTest {
    private fun s(id: String, source: String, title: String = id, pinned: Boolean = false) =
        SessionSummary(id = id, title = title, updated = "", source = source, pinned = pinned)

    @Test fun groupsBySource() {
        assertEquals(SessionGroup.CHATS, sessionGroup(s("1", "api_server")))
        assertEquals(SessionGroup.CHATS, sessionGroup(s("1", "")))
        assertEquals(SessionGroup.BOT, sessionGroup(s("1", "bot_room")))
        assertEquals(SessionGroup.SCHEDULED, sessionGroup(s("1", "cron")))
        assertEquals(SessionGroup.KANBAN, sessionGroup(s("1", "kanban")))
        assertEquals(SessionGroup.OTHER, sessionGroup(s("1", "tui")))
        assertEquals(SessionGroup.MESSAGING, sessionGroup(s("1", "telegram")))
        assertEquals(SessionGroup.MESSAGING, sessionGroup(s("1", "homeassistant")))
    }

    @Test fun botChatIsBotWhateverItsSource() = assertEquals(SessionGroup.BOT, sessionGroup(s("1", "tui", title = "Bot Chat")))

    @Test fun nothingHiddenKeepsAll() {
        val all = listOf(s("a", "cron"), s("b", "api_server"))
        assertEquals(all, filterSessions(all, emptySet()))
    }

    @Test fun hiddenGroupsAreDroppedIncludingPinned() {
        val all = listOf(s("a", "cron", pinned = true), s("b", "api_server"), s("c", "kanban"))
        assertEquals(listOf("b"), filterSessions(all, setOf(SessionGroup.SCHEDULED, SessionGroup.KANBAN)).map { it.id })
    }
}
