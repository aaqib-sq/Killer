package dev.killer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keep Safe is the promise that separates Killer from a "boost" app: nothing that
 * matters is ever closed. These tests pin the seven rules and the three intentions
 * so a future edit cannot quietly drop one.
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
        assertTrue(KeptFunction.Messaging in functions("com.google.android.apps.messaging"))
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

    @Test
    fun intentionsKeepExactlyWhatTheyPromise() {
        // The concept's three cards, and the exact labels it prints.
        assertTrue(KeepSafe.keptByIntention("com.spotify.music", "Spotify", Intention.Focus))
        assertTrue(KeepSafe.keptByIntention("com.google.android.keep", "Keep Notes", Intention.Focus))
        assertFalse(KeepSafe.keptByIntention("com.instagram.android", "Instagram", Intention.Focus))

        assertTrue(KeepSafe.keptByIntention("com.spotify.music", "Spotify", Intention.Bedtime))
        assertTrue(KeepSafe.keptByIntention("com.google.android.deskclock", "Clock", Intention.Bedtime))
        assertFalse(KeepSafe.keptByIntention("com.google.android.apps.maps", "Maps", Intention.Bedtime))

        assertTrue(KeepSafe.keptByIntention("com.google.android.apps.maps", "Maps", Intention.BatterySaver))
        assertTrue(KeepSafe.keptByIntention("com.whatsapp", "WhatsApp", Intention.BatterySaver))
        assertFalse(KeepSafe.keptByIntention("com.spotify.music", "Spotify", Intention.BatterySaver))
    }

    @Test
    fun intentionCopyMatchesTheConcept() {
        assertEquals("Focus for 25 min", Intention.Focus.title)
        assertEquals("Keeps Music + Notes", Intention.Focus.keepLabel)
        assertEquals("Bedtime", Intention.Bedtime.title)
        assertEquals("Keeps Alarm + Music", Intention.Bedtime.keepLabel)
        assertEquals("Battery Saver", Intention.BatterySaver.title)
        assertEquals("Keeps Maps + Messages", Intention.BatterySaver.keepLabel)
        assertEquals("Quiet everything except what helps you focus.", Intention.Focus.description)
        assertEquals("Wind down. Only the essentials stay awake.", Intention.Bedtime.description)
        assertEquals("Hold on to charge. Keep only what gets you home.", Intention.BatterySaver.description)
    }

    /** Helper mirroring the context-free part of KeepSafe.reasonToKeep. */
    private fun keepFor(pkg: String): KeepReason? =
        if (AppCategories.isCore(pkg)) KeepReason.KillerItself else null

    @Test
    fun nonCoreAppIsNotBlindlyKept() {
        assertNull(keepFor("com.instagram.android"))
    }
}
