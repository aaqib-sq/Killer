package dev.killer.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import dev.killer.ui.screens.DiagnosticsScreen
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

    BackHandler(enabled = state.screen == Screen.Closing || state.screen == Screen.QuietMode || state.screen == Screen.Diagnostics) {
        when (state.screen) {
            Screen.Closing -> if (state.phase == ClosingPhase.Running) viewModel.cancelClosing() else viewModel.goHome()
            Screen.QuietMode, Screen.Diagnostics -> viewModel.goHome()
            Screen.Permissions -> if (state.usageAccess && state.accessibility) viewModel.continueFromPermissions()
            Screen.Home -> Unit
        }
    }

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

    if (state.pendingTargets.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmation,
            title = { Text("Force stop ${state.pendingTargets.size} apps?") },
            text = { Text("Killer opens Settings for each selected app and presses Force stop. You can cancel the queue.") },
            confirmButton = { TextButton(onClick = viewModel::confirmClosing) { Text("Continue") } },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirmation) { Text("Cancel") } },
        )
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
                        overlayPermission = state.overlayPermission,
                        onEnableUsage = viewModel::openUsageAccessSettings,
                        onEnableAccessibility = viewModel::openAccessibilitySettings,
                        onEnableOverlay = viewModel::openOverlayPermissionSettings,
                        onContinue = viewModel::continueFromPermissions,
                    )

                    Screen.Home -> HomeScreen(
                        apps = state.visibleApps.filter { it.label.contains(state.query, true) || it.packageName.contains(state.query, true) },
                        query = state.query,
                        onQuery = viewModel::setQuery,
                        selectedOther = state.selectedOther,
                        onToggleOther = viewModel::toggleOther,
                        onSelectOther = viewModel::selectOther,
                        onClearOther = viewModel::clearOther,
                        onCloseSelected = viewModel::closeSelectedOther,
                        onRefresh = viewModel::loadApps,
                        onDiagnostics = viewModel::openDiagnostics,
                        runningCount = state.apps.size,
                        closableCount = state.closable.size,
                        loading = state.loading,
                        tab = state.tab,
                        onTab = viewModel::setTab,
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
                        skippedCount = state.skippedCount,
                        skippedDetails = state.skippedDetails,
                        undoAvailable = state.undoAvailable,
                        undoSecondsLeft = state.undoSecondsLeft,
                        onCancel = viewModel::cancelClosing,
                        onBackHome = viewModel::goHome,
                        onUndo = viewModel::undo,
                    )

                    Screen.Diagnostics -> DiagnosticsScreen(state.diagnostics, viewModel::openDiagnostics, viewModel::goHome)

                    Screen.QuietMode -> QuietModeScreen(
                        apps = state.apps,
                        closableCount = state.quietTargets.size,
                        keptCount = state.quietKeptRows.size,
                        keptRows = state.quietKeptRows,
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
