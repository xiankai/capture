package com.bespectacledcyborg.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrchestratorUrlTest {
    @Test
    fun keepsAnHttpsOriginAndDropsTrailingSlashes() {
        assertEquals("https://agents.example", OrchestratorUrl.parse("  https://agents.example//  "))
    }

    @Test
    fun keepsAPortAndAPathPrefix() {
        assertEquals("http://10.0.0.2:8080/orchestrator", OrchestratorUrl.parse("http://10.0.0.2:8080/orchestrator/"))
    }

    @Test
    fun rejectsBlankInput() {
        assertNull(OrchestratorUrl.parse(""))
        assertNull(OrchestratorUrl.parse("   "))
    }

    @Test
    fun rejectsAnAddressWithoutAWebScheme() {
        assertNull(OrchestratorUrl.parse("agents.example"))
        assertNull(OrchestratorUrl.parse("ftp://agents.example"))
    }

    @Test
    fun rejectsQueriesFragmentsAndCredentials() {
        assertNull(OrchestratorUrl.parse("https://agents.example/?a=1"))
        assertNull(OrchestratorUrl.parse("https://agents.example/#top"))
        assertNull(OrchestratorUrl.parse("https://user:pw@agents.example"))
    }

    @Test
    fun rejectsSomethingThatIsNotAUri() {
        assertNull(OrchestratorUrl.parse("https://agents example"))
    }
}
