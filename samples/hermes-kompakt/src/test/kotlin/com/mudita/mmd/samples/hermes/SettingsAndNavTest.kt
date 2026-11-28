package com.mudita.mmd.samples.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileSettingsTest {
    private val profiles = listOf(HermesProfile("", "k0"), HermesProfile("lead-dev", "k1"), HermesProfile("researcher", ""))

    @Test fun summaries() {
        assertEquals("3 profiles · 1 without a key", profilesSummary(profiles))
        assertEquals("1 profile", profilesSummary(profiles.take(1)))
        assertEquals("default · key set", profileRowSummary(profiles[0]))
        assertEquals("researcher · no key", profileRowSummary(profiles[2]))
    }

    @Test fun newProfilesNeedAValidUnusedNameAndAKey() {
        assertEquals("Use the profile's name on the server: lowercase letters, digits, - or _.", profileError("Lead Dev", "k", true, profiles))
        assertEquals("That profile is already added.", profileError("lead-dev", "k", true, profiles))
        assertEquals("Enter the profile's API key.", profileError("home-manager", " ", true, profiles))
        assertNull(profileError("home-manager", "k", true, profiles))
    }

    @Test fun existingProfilesOnlyNeedAKey() {
        assertNull(profileError("", "k0", false, profiles))
        assertEquals("Enter the profile's API key.", profileError("researcher", "", false, profiles))
    }
}

class LatestButtonTest {
    @Test fun hiddenAtTheBottom() {
        assertNull(latestButtonLabel(atBottom = true, unseenBelow = true))
    }

    @Test fun saysWhetherSomethingNewLanded() {
        assertEquals("Latest ↓", latestButtonLabel(atBottom = false, unseenBelow = false))
        assertEquals("New reply ↓", latestButtonLabel(atBottom = false, unseenBelow = true))
    }
}
