package dev.killer.engine

import dev.killer.ui.HomeApp
import dev.killer.ui.KillerUiState
import org.junit.Assert.*
import org.junit.Test

class QuietModeStateTest {
    private val music = HomeApp("com.spotify.music", "Spotify", 0, null)
    private val game = HomeApp("com.example.game", "Game", 0, null)
    private val protected = HomeApp("com.example.protected", "Protected", 0, KeepReason.PhoneCall)

    @Test fun intentionKeptRowsAndTargetsMatchDisplayedCounts() {
        val state = KillerUiState(apps = listOf(music, game, protected), intention = Intention.Focus)
        assertEquals(setOf(music, protected), state.quietKeptRows.toSet())
        assertEquals(listOf(game), state.quietTargets)
    }

    @Test fun unkeepOnlyOverridesIntentionNeverLiveProtection() {
        val state = KillerUiState(apps = listOf(music, game, protected), intention = Intention.Focus,
            unkept = setOf(music.packageName, protected.packageName))
        assertEquals(listOf(protected), state.quietKeptRows)
        assertEquals(setOf(music, game), state.quietTargets.toSet())
    }
}
