package com.mudita.mmd.samples.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun assistantToolCallsFollowItsText() {
        val e = historyEntries("assistant", "Checking.", 3L, toolCalls = listOf("web_search", "read_file"))
        assertEquals(listOf(MessageKind.CONVERSATION, MessageKind.TOOL_CALL), e.map { it.kind })
        assertEquals(listOf("web_search", "read_file"), e[1].tools)
    }

    @Test fun toolResultIsLabelledWithItsName() {
        val e = historyEntries("tool", "42 files", 4L, toolName = "terminal").single()
        assertEquals(MessageKind.TOOL_RESULT, e.kind)
        assertEquals("terminal", e.label)
    }

    @Test fun emptyToolResultStillShows() = assertEquals("(no output)", historyEntries("tool", "", null).single().text)

    @Test fun systemAndUnknownRolesAreSystem() {
        assertEquals(MessageKind.SYSTEM, historyEntries("system", "You are Hermes", null).single().kind)
        assertEquals(MessageKind.SYSTEM, historyEntries("status", "compacted", null).single().kind)
        assertEquals(MessageKind.SYSTEM, historyEntries("assistant", "Model switched", null, displayKind = "notice").single().kind)
    }

    @Test fun plainDisplayKindStaysConversation() =
        assertEquals(MessageKind.CONVERSATION, historyEntries("assistant", "Hi", null, displayKind = "message").single().kind)

    @Test fun toolCallEntryDedupesAndSkipsBlanks() {
        assertEquals(listOf("a", "b"), toolCallEntry(listOf("a", " ", "b", "a"), null)!!.tools)
        assertNull(toolCallEntry(listOf("", " "), null))
    }

    private val history = historyEntries("user", "q", null) +
        historyEntries("assistant", "", null, toolCalls = listOf("web_search")) +
        historyEntries("tool", "r1", null) +
        historyEntries("assistant", "", null, toolCalls = listOf("read_file", "web_search")) +
        historyEntries("tool", "r2", null) +
        historyEntries("system", "s", null) +
        historyEntries("assistant", "a", null)

    @Test fun offShowsConversationWithOneMergedSummaryLine() {
        val shown = visibleMessages(history, showTools = false)
        assertEquals(listOf("q", "Used web_search, read_file", "a"), shown.map { it.text })
        assertEquals(MessageKind.TOOL_CALL, shown[1].kind)
    }

    @Test fun onShowsEverythingInOrder() = assertEquals(history.size, visibleMessages(history, showTools = true).size)

    @Test fun separateToolRunsStaySeparate() {
        val all = historyEntries("assistant", "", null, toolCalls = listOf("a")) +
            historyEntries("assistant", "mid", null) +
            historyEntries("assistant", "", null, toolCalls = listOf("b"))
        assertEquals(listOf("Used a", "mid", "Used b"), visibleMessages(all, showTools = false).map { it.text })
    }
}
