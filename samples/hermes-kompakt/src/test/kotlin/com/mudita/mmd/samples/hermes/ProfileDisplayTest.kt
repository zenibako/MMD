package com.mudita.mmd.samples.hermes

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileDisplayTest {
    @Test fun hyphenatedSlugIsTitleCased() = assertEquals("System Admin", displayNameFor("system-admin"))

    @Test fun shortSlugIsTitleCased() = assertEquals("Lead Dev", displayNameFor("lead-dev"))

    @Test fun underscoresSplitToo() = assertEquals("Home Manager", displayNameFor("home_manager"))

    @Test fun singleWordIsCapitalized() = assertEquals("Researcher", displayNameFor("researcher"))

    @Test fun unknownSlugIsDerivedNotDropped() = assertEquals("Zz Test 2", displayNameFor("zz-test-2"))

    @Test fun defaultIdsShowHermes() {
        assertEquals("Hermes", displayNameFor("hermes-agent"))
        assertEquals("Hermes", displayNameFor("default"))
        assertEquals("Hermes", displayNameFor("hermes"))
        assertEquals("Hermes", displayNameFor(""))
    }

    @Test fun friendlyIdWithSpaceIsKept() = assertEquals("home assistant bot", displayNameFor("home assistant bot"))

    @Test fun friendlyIdWithUppercaseIsKept() = assertEquals("SysAdmin", displayNameFor("SysAdmin"))

    @Test fun repeatedSeparatorsCollapse() = assertEquals("Lead Dev", displayNameFor("lead--dev_"))
}
