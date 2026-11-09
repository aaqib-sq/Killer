package dev.killer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import dev.killer.ui.screens.ClosingScreen
import dev.killer.ui.screens.HomeScreen
import dev.killer.ui.screens.PermissionsScreen
import dev.killer.ui.screens.QuietModeScreen
import dev.killer.ui.theme.KillerColors
import dev.killer.ui.theme.KillerMotion
import kotlinx.coroutines.delay

/**
 * Navigation across exactly four screens. No nav library: four destinations and
 * one back rule is less machinery than a graph.
 */
@Composable
fun KillerRoot(viewModel: KillerViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Returning from a system settings page must re-read the permissions.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
        if (state.screen == Screen.Home || state.screen == Screen.QuietMode) {
            viewModel.loadApps()
        }
    }

    // Toasts are short and calm, never a system dialog.
    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(2400)
            viewModel.dismissToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(KillerColors.Background)) {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            AnimatedContent(
                targetState = state.screen,
                transitionSpec = {
                    fadeIn(tween(KillerMotion.STANDARD_MS)) togetherWith
                        fadeOut(tween(KillerMotion.STANDARD_MS))
                },
                label = "screen",
            ) { screen ->
                when (screen) {
                    Screen.Permissions -> PermissionsScreen(
                        usageAccess = state.usageAccess,
                        accessibility = state.accessibility,
                        onEnableUsage = viewModel::openUsageAccessSettings,
                        onEnableAccessibility = viewModel::openAccessibilitySettings,
                        onContinue = viewModel::continueFromPermissions,
                    )

                    Screen.Home -> HomeScreen(
                        apps = state.apps,
                        onCloseOne = viewModel::closeOne,
                        onCloseAll = viewModel::closeAll,
                        onQuietMode = viewModel::openQuietMode,
                    )

                    Screen.Closing -> ClosingScreen(
                        phase = state.phase,
                        currentLabel = state.closingLabel,
                        index = state.closingIndex,
                        total = state.closingTotal,
                        progress = state.ringProgress,
                        closedCount = state.closedCount,
                        keptSafeCount = state.keptSafeCount,
                        undoAvailable = state.undoAvailable,
                        undoSecondsLeft = state.undoSecondsLeft,
                        onCancel = viewModel::cancelClosing,
                        onBackHome = viewModel::goHome,
                        onUndo = viewModel::undo,
                    )

                    Screen.QuietMode -> QuietModeScreen(
                        apps = state.apps,
                        closableCount = state.closable.size,
                        keptCount = state.keptRows.size,
                        keptRows = state.keptRows,
                        intention = state.intention,
                        keptSafeExpanded = state.keptSafeExpanded,
                        onSelectIntention = viewModel::setIntention,
                        onToggleKeptSafe = viewModel::toggleKeptSafe,
                        onUnkeep = viewModel::unkeep,
                        onMakeItQuiet = viewModel::makeItQuiet,
                        onBack = viewModel::goHome,
                    )
                }
            }
        }

        state.toast?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp)
                    .background(KillerColors.Surface2, shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "✓ $message",
                    color = KillerColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        }
    }
}
