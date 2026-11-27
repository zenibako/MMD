package com.mudita.mmd.samples.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class ParagraphChunkTest {
    @Test fun nothingUntilAParagraphEnds() {
        assertEquals("", completedParagraphs(""))
        assertEquals("", completedParagraphs("Half a senten"))
        assertEquals("", completedParagraphs("One line\nand another"))
    }

    @Test fun showsEveryFinishedParagraph() {
        assertEquals("First.", completedParagraphs("First.\n\nSecond is still"))
        assertEquals("First.\n\nSecond.", completedParagraphs("First.\n\nSecond.\n\nThi"))
    }

    @Test fun trailingBlankLineCompletesTheParagraph() {
        assertEquals("Done.", completedParagraphs("Done.\n\n"))
    }

    @Test fun blankLinesInsideAnOpenCodeFenceDoNotCount() {
        val open = "Intro.\n\n```\nval a = 1\n\nval b = 2"
        assertEquals("Intro.", completedParagraphs(open))
        val closed = "Intro.\n\n```\nval a = 1\n\nval b = 2\n```\n\nAfter"
        assertEquals("Intro.\n\n```\nval a = 1\n\nval b = 2\n```", completedParagraphs(closed))
    }

    @Test fun leadingBlankLineIsNotAParagraph() {
        assertEquals("", completedParagraphs("\n\nStill going"))
    }
}

class RunFailureLineTest {
    private val uk = Locale.UK
    private val utc: ZoneId = ZoneOffset.UTC

    @Test fun anthropicApiLimitWithRegainDate() {
        val raw = "Error code: 400 - {'type': 'error', 'error': {'type': 'invalid_request_error', 'message': " +
            "'You have reached your specified API usage limits. You will regain access on 2026-12-01 at 00:00 UTC.'}} (Anthropic)"
        assertEquals("Anthropic usage limit reached. Resets 1 Dec.", runFailureLine(raw, uk, utc))
    }

    @Test fun regainTimeInUtcCanFallOnTheLocalDayBefore() {
        val raw = "You will regain access on 2026-12-01 at 00:00 UTC. usage limit"
        assertEquals("Usage limit reached. Resets 30 Nov.", runFailureLine(raw, uk, ZoneId.of("America/Chicago")))
    }

    @Test fun hermesResetsInCopy() {
        val raw = "Anthropic failed — its usage limit resets in 3h 20m. Send /retry after that."
        assertEquals("Anthropic usage limit reached. Resets in 3h 20m.", runFailureLine(raw, uk, utc))
    }

    @Test fun limitResetsAtTime() {
        assertEquals("Usage limit reached. Resets at 14:05.", runFailureLine("Limit resets at 14:05 (in 2h 05m).", uk, utc))
    }

    @Test fun resetsOnAMonthDay() {
        assertEquals("Usage limit reached. Resets Dec 1.", runFailureLine("Weekly usage limit used up. Resets Dec 1.", uk, utc))
    }

    @Test fun usageLimitWithoutAResetTime() {
        assertEquals("OpenAI usage limit reached.", runFailureLine("openai: quota exceeded", uk, utc))
    }

    @Test fun otherErrorsKeepTheirFirstLine() {
        assertEquals("Model not found: granite-9", runFailureLine("Model not found: granite-9\n\nTraceback ...", uk, utc))
    }

    @Test fun longErrorsAreShortenedAtAWord() {
        val line = runFailureLine("word ".repeat(60), uk, utc)
        assertTrue(line.endsWith("…"))
        assertTrue(line.length <= 161)
        assertFalse(line.contains("  "))
    }

    @Test fun emptyReason() {
        assertEquals("The run failed.", runFailureLine("  ", uk, utc))
    }
}

class AnsweredByTest {
    @Test fun sameModelNeedsNoFooter() {
        assertNull(answeredByLabel("claude-opus-5-5", "claude-opus-5-5"))
        assertNull(answeredByLabel("anthropic/Claude-Opus-5-5", "claude-opus-5-5"))
    }

    @Test fun fallbackModelIsNamed() {
        assertEquals("Answered by granite-4", answeredByLabel("ibm/granite-4", "claude-opus-5-5"))
    }

    @Test fun unknownEitherSideShowsNothing() {
        assertNull(answeredByLabel(null, "claude-opus-5-5"))
        assertNull(answeredByLabel("granite-4", null))
        assertNull(answeredByLabel("", "x"))
    }
}

class CurrentChoiceTest {
    private val granite = ModelChoice("ibm", "granite-4")

    @Test fun inheritedChecksProfileDefault() {
        assertTrue(isCurrentChoice(null, null))
        assertFalse(isCurrentChoice(granite, null))
    }

    @Test fun pinnedModelKeepsItsCheckEvenWhenItIsTheDefault() {
        // Pinned to Granite while Granite is also the profile's default.
        assertFalse(isCurrentChoice(null, granite))
        assertTrue(isCurrentChoice(granite, granite))
        assertFalse(isCurrentChoice(ModelChoice("anthropic", "claude-opus-5-5"), granite))
    }
}
