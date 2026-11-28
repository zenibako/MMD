package com.mudita.mmd.samples.hermes

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingPositionTest {
    private val saved = ReadingPosition(index = 4, offset = 120, count = 20, lastMessageTime = 1_000L, showTools = false)

    @Test fun roundTrips() {
        assertEquals(saved, ReadingPosition.decode(saved.encode()))
        assertEquals(saved.copy(lastMessageTime = null), ReadingPosition.decode(saved.copy(lastMessageTime = null).encode()))
        assertNull(ReadingPosition.decode("garbage"))
    }

    @Test fun restoresOnlyAnUnchangedChat() {
        assertEquals(saved, positionToRestore(saved, 20, 1_000L, false))
        assertNull(positionToRestore(saved, 21, 2_000L, false)) // a new reply arrived
        assertNull(positionToRestore(saved, 20, 1_000L, true)) // the switch changed the rows
        assertNull(positionToRestore(null, 20, 1_000L, false))
    }
}

class ReconcileTest {
    private fun user(t: String) = ChatMessage("user", t, 1L)
    private fun bot(t: String) = ChatMessage("assistant", t, 2L)

    @Test fun findsAReplyThatFinishedAfterTheDrop() {
        val history = listOf(user("hi"), bot("hello"), user("Run it"), ChatMessage("assistant", "Used terminal", 3L, MessageKind.TOOL_CALL), bot("Done."))
        assertEquals("Done.", completedReplyAfter(history, "Run it ")?.text)
    }

    @Test fun noReplyMeansRetry() {
        assertNull(completedReplyAfter(listOf(user("hi"), bot("hello"), user("Run it")), "Run it"))
        assertNull(completedReplyAfter(listOf(user("hi"), bot("hello")), "Run it")) // never reached Hermes
    }
}

class GatewayModelTest {
    private val granite = ModelChoice("ibm", "granite-4")

    @Test fun readsTheBrowserModelLock() {
        val row = JSONObject("""{"model":"granite-4","model_config":{"browser_model_lock":{"provider":"ibm","model":"granite-4","confirmed":true}}}""")
        assertEquals(granite, gatewayPin(row))
        assertNull(gatewayPin(JSONObject("""{"model":"hermes-agent","model_config":{}}""")))
        assertNull(gatewayPin(JSONObject("""{"model_config":"{\"browser_model_lock\":{\"model\":\"\"}}"}""")))
    }

    @Test fun profileDefaultPinStaysProfileDefault() {
        assertNull(selectedModel(pin = null, local = granite, profileDefault = granite))
        assertNull(selectedModel(pin = granite, local = null, profileDefault = granite))
        assertEquals(granite, selectedModel(pin = granite, local = granite, profileDefault = granite))
        assertEquals(granite, selectedModel(pin = granite, local = null, profileDefault = ModelChoice("x", "other")))
    }

    @Test fun clearEndpointComesFromCapabilities() {
        val old = JSONObject("""{"endpoints":{"session_model_lock":{"method":"POST","path":"/api/sessions/{session_id}/model"}}}""")
        assertNull(clearModelEndpoint(old))
        val new = JSONObject(
            """{"endpoints":{"session_model_lock":{"method":"POST","path":"/api/sessions/{session_id}/model"},
               "session_model_clear":{"method":"DELETE","path":"/api/sessions/{session_id}/model"}}}""",
        )
        assertEquals("DELETE" to "/api/sessions/{session_id}/model", clearModelEndpoint(new))
    }
}

class ChatLinkTest {
    @Test fun pathForm() {
        assertEquals(ChatLink("lead-dev", "api_123", "4567"), parseChatLink("hermes://chat/lead-dev/api_123#4567"))
        assertEquals(ChatLink("", "s1"), parseChatLink("hermes://chat/default/s1"))
        assertEquals(ChatLink("", "s1"), parseChatLink("hermes://chat/-/s1"))
    }

    @Test fun queryForm() {
        assertEquals(ChatLink("researcher", "s 2", null), parseChatLink("hermes-kompakt://chat?profile=researcher&session=s%202"))
    }

    @Test fun rejectsOtherLinks() {
        assertNull(parseChatLink("hermes://chat/only-one"))
        assertNull(parseChatLink("https://chat/a/b"))
        assertNull(parseChatLink("hermes://sessions/a/b"))
        assertNull(parseChatLink("hermes-kompakt://chat?profile=x"))
    }
}
