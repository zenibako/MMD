package com.mudita.mmd.samples.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelChoiceTest {
    @Test fun flattensProvidersInOrderSkippingBlanks() {
        val p = listOf(
            ProviderModels("anthropic", "Anthropic", listOf("claude-opus-5-5", " ", "claude-sonnet-5-5")),
            ProviderModels("openrouter", "OpenRouter", listOf("glm-5")),
        )
        assertEquals(
            listOf(ModelChoice("anthropic", "claude-opus-5-5"), ModelChoice("anthropic", "claude-sonnet-5-5"), ModelChoice("openrouter", "glm-5")),
            modelChoices(p),
        )
    }

    @Test fun limitedProvidersGoLast() {
        val p = listOf(
            ProviderModels("anthropic", "Anthropic", listOf("a"), limited = true),
            ProviderModels("local", "Local", listOf("b")),
        )
        assertEquals(listOf("b", "a"), modelChoices(p).map { it.model })
    }

    @Test fun duplicatesAppearOnce() =
        assertEquals(1, modelChoices(listOf(ProviderModels("x", "X", listOf("m", "m")))).size)

    @Test fun emptyProviderIsSkipped() = assertEquals(emptyList<ModelChoice>(), modelChoices(listOf(ProviderModels("x", "X", emptyList()))))

    @Test fun encodeDecodeRoundTrips() {
        val c = ModelChoice("anthropic", "claude-opus-5-5")
        assertEquals(c, ModelChoice.decode(c.encode()))
        assertEquals(ModelChoice("", "m"), ModelChoice.decode(ModelChoice("", "m").encode()))
    }

    @Test fun badStoredValuesDecodeToNull() {
        assertNull(ModelChoice.decode(null))
        assertNull(ModelChoice.decode("garbage"))
        assertNull(ModelChoice.decode("p\t"))
    }

    @Test fun labelShowsProviderWhenKnown() {
        assertEquals("m (p)", ModelChoice("p", "m").label)
        assertEquals("m", ModelChoice("", "m").label)
    }

    @Test fun keyIsScopedToServerProfileAndSession() {
        assertEquals("model|https://h.example|lead-dev|s1", sessionModelKey("h.example/", "lead-dev", "s1"))
        assertEquals(sessionModelKey("https://h.example", "", "s1"), sessionModelKey("h.example", "", "s1"))
    }
}

class LockTargetTest {
    private val default = ModelChoice("anthropic", "claude-opus-5-5")
    private val pick = ModelChoice("openrouter", "glm-5")

    @Test fun pickLocksToThePick() = assertEquals(pick, lockTarget(pick, default))

    @Test fun profileDefaultLocksToTheDefaultModel() = assertEquals(default, lockTarget(null, default))

    @Test fun profileDefaultWithoutAKnownDefaultHasNoTarget() = assertNull(lockTarget(null, null))
}
