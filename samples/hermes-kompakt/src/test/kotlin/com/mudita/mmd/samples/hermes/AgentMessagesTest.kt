package com.mudita.mmd.samples.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StampedMessageTest {
    @Test fun stripsTheStampAndNamesTheSender() {
        assertEquals(
            AgentMessage("Lead Dev", "Can you check the build?\nThanks", "lead-dev"),
            parseStampedMessage("Message from 🤖 Lead Dev (@lead-dev): Can you check the build?\nThanks"),
        )
    }

    @Test fun ordinaryMessagesAreNotStamped() {
        assertNull(parseStampedMessage("Message from Lead Dev: hi"))
        assertNull(parseStampedMessage("hello @lead-dev"))
    }
}

class AgentReplyTest {
    private val delivery =
        "py /opt/hermes/tools/bot_mode_dm.py --run-delivery --author '{\"id\":\"bot:hermes\"}' query-file /tmp/x " +
            "--profile-home /home/hermes/.hermes/profiles/system-admin /opt/hermes/libexec/hermes -p system-admin chat --in '~' -c 'Bot Chat'"

    @Test fun deliveryReplyIsUnderTheAnsweringProfile() {
        val content = "[IMPORTANT: Background process proc_1 completed (exit code 0).\nCommand: $delivery\nOutput:\nAll green.\nNothing to do.]"
        assertEquals(AgentMessage("System Admin", "All green.\nNothing to do.", "system-admin"), parseAgentReply(content))
    }

    @Test fun relayReplyDropsTheReplyFromLine() {
        val content = "[IMPORTANT: Background process proc_2 completed (exit code 0).\n" +
            "Command: py /opt/hermes/tools/bot_mode_dm.py --wait-reply /tmp/r '@hermes on mac-mini' 3120\n" +
            "Output:\nReply from @hermes on mac-mini:\nDone.]"
        assertEquals(AgentMessage("@hermes on mac-mini", "Done."), parseAgentReply(content))
    }

    @Test fun defaultProfileIsHermes() {
        val content = "[IMPORTANT: Background process p completed (exit code 0).\n" +
            "Command: py bot_mode_dm.py --run-delivery query-file /tmp/x --profile-home /home/hermes/.hermes hermes -p default chat\nOutput:\nhi]"
        assertEquals("Hermes", parseAgentReply(content)?.sender)
    }

    @Test fun failuresAndOtherProcessesStayNotices() {
        assertNull(parseAgentReply("[IMPORTANT: Background process p exited (exit code 1).\nCommand: $delivery\nOutput:\nerror]"))
        assertNull(parseAgentReply("[IMPORTANT: Background process p completed (exit code 0).\nCommand: make test\nOutput:\nok]"))
        assertNull(parseAgentReply("[IMPORTANT: 2 background processes completed.\nCommand: $delivery\nOutput:\nx]"))
    }
}

class DelegationResultTest {
    @Test fun takesTheResultSection() {
        val content = "[ASYNC DELEGATION COMPLETE — d1]\nA background subagent…\nGoal: x\nStatus: completed   Duration: 3s\n--- RESULT ---\nFound 3 files."
        assertEquals(AgentMessage("Subagent", "Found 3 files."), parseDelegationResult(content))
    }

    @Test fun noResultNoMessage() {
        assertNull(parseDelegationResult("[ASYNC DELEGATION COMPLETE — d1]\nThe subagent did not complete successfully"))
        assertNull(parseDelegationResult("Subagent Task Completed: x"))
    }
}

class HistoryAgentEntriesTest {
    @Test fun stampedUserTurnBecomesAReceivedAgentMessage() {
        val m = historyEntries("user", "Message from 🤖 Researcher (@researcher): Here's the paper.", 1L).single()
        assertEquals(AGENT_ROLE, m.role)
        assertEquals("Researcher", m.sender)
        assertEquals("Here's the paper.", m.text)
        assertEquals(MessageKind.CONVERSATION, m.kind)
    }

    @Test fun internalNotificationsStayHiddenEvenWhenStamped() {
        val m = historyEntries("user", "Message from 🤖 Lead Dev (@lead-dev): x", 1L, displayKind = "internal_notification").single()
        assertEquals(MessageKind.SYSTEM, m.kind)
    }

    @Test fun unrecognisedProcessNoticesStaySystem() {
        assertEquals(MessageKind.SYSTEM, historyEntries("user", "[IMPORTANT: something]", 1L, displayKind = "process_complete").single().kind)
    }
}

class MentionRangesTest {
    @Test fun findsHandlesButNotEmails() {
        val text = "Ask @lead-dev and @researcher, not me@example.com or @@x"
        assertEquals(listOf("@lead-dev", "@researcher"), mentionRanges(text).map { text.substring(it) })
    }
}

class BotChatWritableTest {
    @Test fun botChatIsWritableOtherTerminalSessionsAreNot() {
        assertEquals(false, isReadOnlySession("tui", "Bot Chat"))
        assertEquals(true, isReadOnlySession("tui", "Debug the router"))
        assertEquals(false, isReadOnlySession("api_server", "anything"))
        assertEquals(false, isReadOnlySession("bot_room", "Room"))
        assertEquals(true, isReadOnlySession("cron", "Bot Chat"))
        assertEquals(false, isReadOnlySession("", ""))
    }

    @Test fun refusedTurnsReadClearly() {
        val busy = HermesApiException(409, "HTTP 409 on /x: {\"error\":{\"message\":\"turn running\",\"code\":\"session_active_turn\"}}")
        assertEquals("Hermes is still answering this chat elsewhere. Try again in a moment.", sendFailureLine(busy))
        val other = HermesApiException(409, "HTTP 409 on /x: {\"error\":{\"message\":\"Session is locked\",\"code\":\"x\"}}")
        assertEquals("Hermes refused the message: Session is locked", sendFailureLine(other))
    }
}
