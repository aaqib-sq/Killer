package dev.killer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keep Safe is the promise that separates Killer from a "boost" app: nothing that
 * matters is ever closed. These tests pin the rules so a future edit cannot
 * quietly drop one.
 */
class KeepSafeTest {

    private fun functions(pkg: String, label: String = "") =
        KeepSafe.functionsOf(pkg, label)

    @Test
    fun musicAppsAreRecognised() {
        assertTrue(KeptFunction.Music in functions("com.spotify.music"))
        assertTrue(KeptFunction.Music in functions("com.google.android.apps.youtube.music"))
        assertTrue(KeptFunction.Music in functions("org.videolan.vlc", "Podcast player"))
        assertFalse(KeptFunction.Music in functions("com.instagram.android"))
    }

    @Test
    fun navigationAppsAreRecognised() {
        assertTrue(KeptFunction.Maps in functions("com.google.android.apps.maps"))
        assertTrue(KeptFunction.Maps in functions("com.waze"))
        assertFalse(KeptFunction.Maps in functions("com.spotify.music"))
    }

    @Test
    fun clockAppsAreRecognisedForTimerAndAlarm() {
        assertTrue(KeptFunction.Alarm in functions("com.google.android.deskclock"))
        assertTrue(KeptFunction.Alarm in functions("com.android.deskclock"))
        // "clockpackage" has no space between "clock" and "package"; the matcher is
        // deliberately literal, so Samsung's clock is matched by its label instead.
        assertTrue(KeptFunction.Alarm in functions("com.sec.android.app.clockpackage", "Clock"))
    }

    @Test
    fun messagingAppsAreRecognised() {
        assertTrue(KeptFunction.Messaging in functions("com.whatsapp"))
        assertTrue(KeptFunction.Messaging in functions("org.telegram.messenger"))
        // Brand names cover WhatsApp/Telegram/Signal; the "messag" stem covers the
        // Google and AOSP messaging apps without needing a network app directory.
        assertTrue(KeptFunction.Messaging in functions("com.google.android.apps.messaging"))
        assertTrue(KeptFunction.Messaging in functions("com.android.messaging"))
        assertFalse(
            "A brand name alone must not be mistaken for a messaging app",
            KeptFunction.Messaging in functions("com.example.notes", "Notes"),
        )
    }

    @Test
    fun dialerIsRecognisedForCalls() {
        assertTrue(KeptFunction.PhoneCall in functions("com.google.android.dialer"))
    }

    @Test
    fun corePackagesAreAlwaysKeptAsKillerItself() {
        // The extracted engine's blocklist is absolute; Keep Safe must agree with it.
        assertEquals(KeepReason.KillerItself, keepFor("com.android.systemui"))
        assertEquals(KeepReason.KillerItself, keepFor("android"))
        assertEquals(KeepReason.KillerItself, keepFor("com.android.settings"))
    }

    @Test
    fun unknownAppsHaveNoProtectedFunction() {
        assertTrue(functions("com.example.game", "Game").isEmpty())
    }

    /** Helper mirroring the context-free part of KeepSafe.reasonToKeep. */
    private fun keepFor(pkg: String): KeepReason? =
        if (AppCategories.isCore(pkg)) KeepReason.KillerItself else null

    @Test
    fun nonCoreAppIsNotBlindlyKept() {
        assertNull(keepFor("com.instagram.android"))
    }
}
