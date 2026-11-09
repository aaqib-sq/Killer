package dev.killer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The concept is the authority for copy, and the brief forbids invented numbers.
 * These tests pin the strings that must never drift and the vocabulary that must
 * never appear in the product.
 */
class KillerCopyTest {

    @Test
    fun keepReasonsMatchTheBriefWording() {
        assertEquals("Music playing", KeepReason.MusicPlaying.label)
        assertEquals("Navigating", KeepReason.Navigating.label)
        assertEquals("On a call", KeepReason.PhoneCall.label)
        assertEquals("Timer running", KeepReason.Timer.label)
        assertEquals("Alarm set", KeepReason.Alarm.label)
        assertEquals("Downloading", KeepReason.Downloading.label)
    }

    @Test
    fun forbiddenClaimsNeverAppearInCopy() {
        val allCopy = buildString {
            KeepReason.entries.forEach { append(it.label).append(' ') }
            Intention.entries.forEach {
                append(it.title).append(' ')
                append(it.description).append(' ')
                append(it.keepLabel).append(' ')
            }
        }.lowercase()

        // No boosting, no fake memory numbers, no force-stop claims.
        listOf("gb", "boost", "ram", "force", "force-stop", "%").forEach { word ->
            assertTrue(
                "Forbidden claim '$word' must not appear in user-facing copy",
                !allCopy.contains(word),
            )
        }
    }

    @Test
    fun usageWindowIsSevenDaysAsInTheExtractedEngine() {
        assertEquals(7L * 24 * 60 * 60 * 1000, AppKillerEngine.USAGE_STATS_WINDOW_MS)
    }
}
