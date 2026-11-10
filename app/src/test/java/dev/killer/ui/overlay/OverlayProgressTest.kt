package dev.killer.ui.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Slice 3: the overlay card formats the ViewModel's single progress state.
 * These pin the wording and the clamping so the card can never show 0 of 0,
 * 101%, or a position past the end of the queue.
 */
class OverlayProgressTest {

    @Test
    fun percentTextShowsWholePercent() {
        assertEquals("0%", overlayPercentText(0f))
        assertEquals("42%", overlayPercentText(0.42f))
        assertEquals("100%", overlayPercentText(1f))
    }

    @Test
    fun percentTextNeverExceedsBounds() {
        assertEquals("0%", overlayPercentText(-0.5f))
        assertEquals("100%", overlayPercentText(1.5f))
    }

    @Test
    fun positionTextShowsOneBasedQueuePosition() {
        assertEquals("App 1 of 5", overlayPositionText(1, 5))
        assertEquals("App 2 of 5", overlayPositionText(2, 5))
        assertEquals("App 5 of 5", overlayPositionText(5, 5))
    }

    @Test
    fun positionTextClampsBeforeFirstAppAndPastLast() {
        // The ViewModel reports index 0 before the first app starts.
        assertEquals("App 1 of 5", overlayPositionText(0, 5))
        assertEquals("App 5 of 5", overlayPositionText(9, 5))
        assertEquals("App 1 of 1", overlayPositionText(0, 0))
    }

    @Test
    fun currentAppTextNamesTheAppBeingClosed() {
        assertEquals("Closing WhatsApp…", overlayCurrentAppText("WhatsApp"))
    }
}
