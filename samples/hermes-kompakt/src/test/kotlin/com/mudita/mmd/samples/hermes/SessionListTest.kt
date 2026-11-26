package com.mudita.mmd.samples.hermes

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionListTest {
    private fun s(id: String, pinned: Boolean = false, title: String = id) =
        SessionSummary(id = id, title = title, updated = "", source = "api_server", pinned = pinned)

    private fun ids(list: List<SessionSummary>) = list.map { it.id }

    @Test fun pinnedComeFirstInServerOrder() =
        assertEquals(listOf("b", "d", "a", "c"), ids(arrangeSessions(listOf(s("a"), s("b", true), s("c"), s("d", true)), null)))

    @Test fun botChatFollowsPins() =
        assertEquals(listOf("p", "bot", "a"), ids(arrangeSessions(listOf(s("a"), s("p", true)), s("bot"))))

    @Test fun botChatAlreadyListedIsNotDuplicated() =
        assertEquals(listOf("bot", "a", "b"), ids(arrangeSessions(listOf(s("a"), s("bot"), s("b")), s("bot"))))

    @Test fun pinnedBotChatStaysWithThePins() =
        assertEquals(listOf("bot", "a"), ids(arrangeSessions(listOf(s("a")), s("bot", pinned = true))))

    @Test fun noBotChatLeavesTheListAsIs() =
        assertEquals(listOf("a", "b"), ids(arrangeSessions(listOf(s("a"), s("b")), null)))

    @Test fun blankTitleShowsPlaceholder() {
        assertEquals("New chat", sessionTitleOrPlaceholder(""))
        assertEquals("New chat", sessionTitleOrPlaceholder("   "))
        assertEquals("Fix the boiler", sessionTitleOrPlaceholder("Fix the boiler"))
    }
}
