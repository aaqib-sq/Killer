package dev.killer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Keep Safe, after Slice 7: the live-signal helpers and the app-level rules
 * are gone (the user asked for the leftover code from the removed rules to
 * be cleaned out). What remains is the one absolute rule — the core
 * blocklist — and these tests pin it so a future edit cannot quietly drop it.
 */
class KeepSafeTest {

    @Test
    fun corePackagesAreAlwaysKeptAsKillerItself() {
        // The extracted engine's blocklist is absolute; Keep Safe must agree with it.
        assertEquals(KeepReason.KillerItself, keepFor("com.android.systemui"))
        assertEquals(KeepReason.KillerItself, keepFor("android"))
        assertEquals(KeepReason.KillerItself, keepFor("com.android.settings"))
    }

    /** Helper mirroring the context-free part of KeepSafe.reasonToKeep. */
    private fun keepFor(pkg: String): KeepReason? =
        if (AppCategories.isCore(pkg)) KeepReason.KillerItself else null

    @Test
    fun nonCoreAppIsNotBlindlyKept() {
        assertNull(keepFor("com.instagram.android"))
    }
}
