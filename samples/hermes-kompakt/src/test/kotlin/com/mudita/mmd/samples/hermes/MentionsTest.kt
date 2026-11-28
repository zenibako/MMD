package com.mudita.mmd.samples.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionQueryTest {
    @Test fun findsTheWordBeingTyped() {
        assertEquals(MentionQuery(4, "le"), activeMentionQuery("Ask @le", 7))
        assertEquals(MentionQuery(0, ""), activeMentionQuery("@", 1))
        assertEquals(MentionQuery(1, "sys"), activeMentionQuery("(@sys", 5))
    }

    @Test fun ignoresEmailsCodeAndFinishedWords() {
        assertNull(activeMentionQuery("me@exa", 6))
        assertNull(activeMentionQuery("run `@x", 7))
        assertNull(activeMentionQuery("Ask @lead-dev now", 17))
    }
}

class MentionSuggestionTest {
    private val c = listOf(
        MentionCandidate("hermes", "Hermes"),
        MentionCandidate("lead-dev", "Lead Dev"),
        MentionCandidate("system-admin", "System Admin"),
        MentionCandidate("researcher", "Researcher"),
        MentionCandidate("home-manager", "Home Manager"),
    )

    @Test fun handlePrefixFirstThenNameWords() {
        assertEquals(listOf("hermes", "home-manager"), mentionSuggestions("h", c).map { it.handle })
        assertEquals(listOf("system-admin"), mentionSuggestions("ADM", c).map { it.handle })
        assertEquals(4, mentionSuggestions("", c).size)
    }

    @Test fun insertsTheHandleWithASpace() {
        assertEquals("Ask @lead-dev about it" to 14, applyMention("Ask @le about it", MentionQuery(4, "le"), 7, "lead-dev"))
        assertEquals("@hermes " to 8, applyMention("@he", MentionQuery(0, "he"), 3, "hermes"))
    }

    @Test fun profilesMapDefaultToHermes() {
        assertEquals(listOf(MentionCandidate("hermes", "Hermes"), MentionCandidate("lead-dev", "Lead Dev")), profileCandidates(listOf("" to "Hermes", "lead-dev" to "Lead Dev")))
    }
}

class MentionValidationTest {
    private val known = setOf("hermes", "lead-dev", "all")

    @Test fun knownHandlesPass() {
        assertEquals(emptyList<MentionIssue>(), unknownMentions("@Lead-Dev and @all, mail me@x.io, `@code`", known))
    }

    @Test fun unknownAndTrailingPunctuation() {
        assertEquals(listOf(MentionIssue("foo", null)), unknownMentions("hey @foo", known))
        assertEquals(listOf(MentionIssue("lead-dev.", "lead-dev")), unknownMentions("Ask @lead-dev.", known))
        assertEquals("@foo isn't a bot in this chat. (and 1 more)", mentionNotice(unknownMentions("@foo @bar", known)))
    }

    @Test fun onlyBotRoomsAndBotChatRoute() {
        assertTrue(mentionsRoute("bot_room", "Room"))
        assertTrue(mentionsRoute("tui", "Bot Chat"))
        assertFalse(mentionsRoute("api_server", "Bot Chat"))
        assertFalse(mentionsRoute("tui", "Debug"))
    }
}
