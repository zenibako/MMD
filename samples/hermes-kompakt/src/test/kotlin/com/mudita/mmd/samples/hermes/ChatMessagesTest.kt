package com.mudita.mmd.samples.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessagesTest {
    @Test fun userAndAssistantTextIsConversation() {
        assertEquals(MessageKind.CONVERSATION, historyEntries("user", "hi", 1L).single().kind)
        assertEquals(MessageKind.CONVERSATION, historyEntries("assistant", "hello", 2L).single().kind)
    }

    @Test fun blankConversationEntriesAreDropped() {
        assertTrue(historyEntries("user", "  ", null).isEmpty())
        assertTrue(historyEntries("assistant", "", null).isEmpty())
    }

    @Test fun assistantToolCallsBecomeAToolLineAfterTheText() {
        val e = historyEntries("assistant", "Checking.", 3L, toolCalls = listOf("web_search", "read_file"))
        assertEquals(listOf(MessageKind.CONVERSATION, MessageKind.TOOL), e.map { it.kind })
        assertEquals("Called web_search, read_file", e[1].text)
    }

    @Test fun toolCallWithoutTextIsOnlyTheToolLine() {
        val e = historyEntries("assistant", "", 3L, toolCalls = listOf("terminal"))
        assertEquals(listOf(MessageKind.TOOL), e.map { it.kind })
    }

    @Test fun toolResultIsToolLabelledWithItsName() {
        val e = historyEntries("tool", "42 files", 4L, toolName = "terminal").single()
        assertEquals(MessageKind.TOOL, e.kind)
        assertEquals("terminal", e.label)
    }

    @Test fun emptyToolResultStillShows() = assertEquals("(no output)", historyEntries("tool", "", null).single().text)

    @Test fun systemAndUnknownRolesAreSystem() {
        assertEquals(MessageKind.SYSTEM, historyEntries("system", "You are Hermes", null).single().kind)
        assertEquals(MessageKind.SYSTEM, historyEntries("status", "compacted", null).single().kind)
    }

    @Test fun nonPlainDisplayKindIsSystem() =
        assertEquals(MessageKind.SYSTEM, historyEntries("assistant", "Model switched", null, displayKind = "notice").single().kind)

    @Test fun plainDisplayKindStaysConversation() =
        assertEquals(MessageKind.CONVERSATION, historyEntries("assistant", "Hi", null, displayKind = "message").single().kind)

    @Test fun filterHidesToolAndSystemByDefault() {
        val all = historyEntries("user", "q", null) +
            historyEntries("assistant", "", null, toolCalls = listOf("t")) +
            historyEntries("tool", "r", null) +
            historyEntries("system", "s", null) +
            historyEntries("assistant", "a", null)
        assertEquals(listOf("q", "a"), visibleMessages(all, showTools = false).map { it.text })
        assertEquals(5, visibleMessages(all, showTools = true).size)
    }
}
