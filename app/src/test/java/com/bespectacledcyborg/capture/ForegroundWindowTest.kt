package com.bespectacledcyborg.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundWindowTest {
    private val own = "com.bespectacledcyborg.capture"
    private val systemUi = AppWindow("com.android.systemui", focused = false, active = false, layer = 5)
    private val explorer = AppWindow("com.bespectacledcyborg.remoteexplorer", focused = false, active = false, layer = 1)

    @Test
    fun skipsSystemUiListedAheadOfTheAppOnScreen() {
        assertEquals(explorer, ForegroundWindow.pick(listOf(systemUi, explorer), own))
    }

    @Test
    fun takesTheFocusedWindowInSplitScreen() {
        val browser = AppWindow("com.android.chrome", focused = true, active = false, layer = 1)
        val notes = explorer.copy(active = true, layer = 2)

        assertEquals(browser, ForegroundWindow.pick(listOf(notes, systemUi, browser), own))
    }

    @Test
    fun takesTheActiveWindowWhenNoneIsFocused() {
        val active = explorer.copy(active = true)
        val other = AppWindow("com.android.chrome", focused = false, active = false, layer = 9)

        assertEquals(active, ForegroundWindow.pick(listOf(other, active), own))
    }

    @Test
    fun ignoresCapturesOwnFocusedPanel() {
        val panel = AppWindow(own, focused = true, active = true, layer = 9)

        assertEquals(explorer, ForegroundWindow.pick(listOf(panel, systemUi, explorer), own))
    }

    @Test
    fun takesTheTopmostWindowWhenNoneIsFocusedOrActive() {
        val top = AppWindow("com.android.chrome", focused = false, active = false, layer = 3)

        assertEquals(top, ForegroundWindow.pick(listOf(explorer, top), own))
    }

    @Test
    fun fallsBackToSystemUiWhenItIsAllThereIs() {
        assertEquals(systemUi, ForegroundWindow.pick(listOf(systemUi), own))
    }

    @Test
    fun nothingWhenOnlyCaptureIsOnScreen() {
        assertNull(ForegroundWindow.pick(listOf(AppWindow(own, focused = true, active = true, layer = 1)), own))
    }
}
