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
        // Slice 7: only the core rule survives; the app-level rules are gone.
        assertEquals("Core app", KeepReason.KillerItself.label)
    }

    @Test
    fun forbiddenClaimsNeverAppearInCopy() {
        val allCopy = buildString {
            KeepReason.entries.forEach { append(it.label).append(' ') }
        }.lowercase()

        // No boosting or fake memory numbers. Honest force-stop wording is allowed.
        listOf("gb", "boost", "ram", "%").forEach { word ->
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
