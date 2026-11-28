package com.mudita.mmd.samples.hermes

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalParsingTest {
    @Test fun readsTheSessionStreamEvent() {
        val event = JSONObject(
            """{"event":"approval.request","run_id":"run_1","timestamp":1.0,"message_id":"msg_1",
               "command":"echo hello","description":"Run a shell command","pattern_key":"shell",
               "request_id":"req_9","tool_name":"terminal","choices":["once","session","always","deny"]}""",
        )
        val a = parseApproval(event)!!
        assertEquals("run_1", a.runId)
        assertEquals("req_9", a.requestId)
        assertNull(a.generation)
        assertEquals("terminal", a.tool)
        assertEquals("Run a shell command", a.summary)
        assertEquals(listOf("once", "session", "always"), a.approveChoices)
    }

    @Test fun readsTheGenerationAndFallsBackForMissingFields() {
        val a = parseApproval(JSONObject("""{"run_id":"run_2","execution_generation":7,"command":"ls -la\nsecond line"}"""))!!
        assertEquals(7L, a.generation)
        assertEquals("Command", a.tool)
        assertEquals("ls -la", a.summary)
        assertEquals(listOf("once", "deny"), a.choices)
        assertEquals(listOf("once"), a.approveChoices)
    }

    @Test fun needsARunId() {
        assertNull(parseApproval(JSONObject("""{"command":"echo"}""")))
    }
}

class ApprovalBodyTest {
    private val base = ApprovalRequest("run_1", "req_9", null, "terminal", "echo", listOf("once", "deny"))

    @Test fun fencedRunsSendExactlyRequestGenerationAndChoice() {
        val body = approvalBody(base.copy(generation = 3), "once")
        assertEquals(mapOf("request_id" to "req_9", "execution_generation" to 3L, "choice" to "once"), body)
    }

    @Test fun unfencedRunsSendChoiceAndRequestId() {
        assertEquals(mapOf("choice" to "deny", "request_id" to "req_9"), approvalBody(base, "deny"))
    }

    @Test fun noRequestIdSendsOnlyTheChoice() {
        assertEquals(mapOf("choice" to "once"), approvalBody(base.copy(requestId = null, generation = 3), "once"))
    }
}

class ApprovalOutcomeTest {
    @Test fun closedPromptsAreAlreadyResolved() {
        assertEquals(ApprovalOutcome.AlreadyResolved, approvalFailure(409, """{"error":{"message":"x","code":"approval_not_pending"}}"""))
        assertEquals(ApprovalOutcome.AlreadyResolved, approvalFailure(400, """{"error":{"code":"stale_generation"}}"""))
    }

    @Test fun otherFailuresKeepTheirCode() {
        assertEquals(ApprovalOutcome.Failed("invalid_params"), approvalFailure(400, """{"error":{"code":"invalid_params"}}"""))
        assertEquals(ApprovalOutcome.Failed("HTTP 500"), approvalFailure(500, "oops"))
    }

    @Test fun quietLines() {
        assertEquals("Approved terminal once.", approvalOutcomeLine(ApprovalOutcome.Answered("once"), "terminal"))
        assertEquals("Denied terminal.", approvalOutcomeLine(ApprovalOutcome.Answered("deny"), "terminal"))
        assertTrue(approvalOutcomeLine(ApprovalOutcome.AlreadyResolved, "terminal").contains("already answered"))
    }
}

class ProfileUnknownTest {
    @Test fun matchesTheCodeNotTheText() {
        assertEquals("profile_unknown", apiErrorCode("""{"error": "Unknown or unconfigured profile", "code": "profile_unknown"}"""))
        assertEquals("profile_not_served", apiErrorCode("""{"error":{"message":"…","code":"profile_not_served"}}"""))
        assertNull(apiErrorCode("Unknown or unconfigured profile"))
    }

    @Test fun lineNamesTheProfile() {
        assertEquals(
            "Couldn't load sessions. Profile 'lead-dev' isn't on the server; check its name in Settings.",
            profileUnknownLine("Couldn't load sessions", "lead-dev"),
        )
        assertEquals("Profile 'x' isn't on the server", ProfileUnknownException("x").message)
    }
}

class StreamAssemblerTest {
    private fun j(s: String) = JSONObject(s)

    @Test fun secondRunCompletedIsIgnored() {
        val a = StreamAssembler()
        a.accept("assistant.delta", j("""{"delta":"Hi"}"""))
        assertEquals(listOf(ChatEvent.Done("Hi", "m1")), a.accept("run.completed", j("""{"runtime":{"model":"m1"}}""")))
        assertTrue(a.finished)
        assertEquals(emptyList<ChatEvent>(), a.accept("run.completed", j("""{"output":"Hi again"}""")))
        assertEquals(emptyList<ChatEvent>(), a.accept("assistant.completed", j("""{"content":"other"}""")))
    }

    @Test fun repeatedAssistantCompletedKeepsTheFirstText() {
        val a = StreamAssembler()
        a.accept("assistant.completed", j("""{"content":"Final answer"}"""))
        a.accept("assistant.completed", j("""{"content":"Duplicate"}"""))
        assertEquals(listOf(ChatEvent.Done("Final answer", null)), a.accept("run.completed", j("{}")))
    }

    @Test fun errorEventEndsTheTurn() {
        val a = StreamAssembler()
        assertEquals(listOf(ChatEvent.Failed("boom")), a.accept("error", j("""{"message":"boom"}""")))
        assertEquals(emptyList<ChatEvent>(), a.accept("run.completed", j("{}")))
    }

    @Test fun approvalRequestBecomesAnEvent() {
        val events = StreamAssembler().accept("approval.request", j("""{"run_id":"run_1","command":"echo hello"}"""))
        assertEquals(1, events.size)
        assertEquals("echo hello", (events[0] as ChatEvent.Approval).request.summary)
    }
}

class StreamToolNameTest {
    @Test fun sessionStreamToolEventsUseToolName() {
        val events = StreamAssembler().accept("tool.started", JSONObject("""{"message_id":"m","tool_name":"terminal","preview":"echo hello"}"""))
        assertEquals(listOf(ChatEvent.Tool("terminal", "started")), events)
    }

    @Test fun namelessToolEventsAreDropped() {
        assertEquals(emptyList<ChatEvent>(), StreamAssembler().accept("tool.completed", JSONObject("{}")))
    }
}
