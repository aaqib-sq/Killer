package dev.killer.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.killer.engine.AppInfo
import dev.killer.engine.AppKillerAccessibilityService
import dev.killer.engine.AppKillerEngine
import dev.killer.engine.AppCategories
import dev.killer.engine.KeepReason
import dev.killer.engine.KeepSafe
import dev.killer.ui.overlay.ClosingOverlayController
import dev.killer.ui.overlay.hasOverlayPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Exactly three screens. No nav library: three destinations and one back rule is less machinery than a graph. */
enum class Screen { Permissions, Home, Closing }
enum class AppTab { Running, Other, Core }

/** Closing screen phases, matching the concept's idle → running → done. */
enum class ClosingPhase { Running, Done, Idle }

/** One row on Home, derived from the extracted engine's AppInfo. */
data class HomeApp(
    val packageName: String,
    val label: String,
    val runningMinutes: Long,
    val keepReason: KeepReason?,
) {
    val isKept: Boolean get() = keepReason != null

    /** "Running, 42 min" / "Music playing, 18 min" — the concept's secondary line. */
    fun detail(): String {
        val prefix = keepReason?.label ?: "Detected"
        return if (runningMinutes > 0) "$prefix • $runningMinutes min foreground in 7 days" else prefix
    }
}

data class KillerUiState(
    val screen: Screen = Screen.Home,
    val usageAccess: Boolean = false,
    val accessibility: Boolean = false,
    /** Optional: only keeps the closing progress visible above Settings. Never gates closing. */
    val overlayPermission: Boolean = false,
    val apps: List<HomeApp> = emptyList(),
    val otherApps: List<HomeApp> = emptyList(),
    val coreApps: List<HomeApp> = emptyList(),
    val tab: AppTab = AppTab.Running,
    val loading: Boolean = true,
    val phase: ClosingPhase = ClosingPhase.Idle,
    val closingLabel: String = "",
    val closingIndex: Int = 0,
    val closingTotal: Int = 0,
    val ringProgress: Float = 0f,
    val closedCount: Int = 0,
    val keptSafeCount: Int = 0,
    val skippedCount: Int = 0,
    val skippedDetails: List<String> = emptyList(),
    /** True when the progress overlay window was actually shown for this run. False means closing fell back to the in-app screen (usually the overlay permission is off on this install) — the Done screen says so. */
    val overlayAvailable: Boolean = false,
    /** How many times the overlay had to be brought back mid-loop (Slice 8). */
    val overlayReshows: Int = 0,
    /** Unusual overlay hide() calls during this run (expected "done"/"finally" excluded) — the Done screen reports them. */
    val overlayHideLog: List<String> = emptyList(),
    /** Overlay window geometry at Done (size + attached/shown) — proves which window was on screen. */
    val overlayGeometry: String = "",
    val undoSecondsLeft: Int = 0,
    val undoAvailable: Boolean = false,
    val toast: String? = null,
    val query: String = "",
    val selectedRunning: Set<String> = emptySet(),
    val selectedOther: Set<String> = emptySet(),
    val pendingTargets: List<HomeApp> = emptyList(),
    val pendingKept: List<HomeApp> = emptyList(),
) {
    val count: Int get() = apps.size
    val closable: List<HomeApp> get() = apps.filter { !it.isKept }
    val keptRows: List<HomeApp> get() = apps.filter { it.isKept }
    val visibleApps: List<HomeApp> get() = when (tab) {
        AppTab.Running -> apps
        AppTab.Other -> otherApps
        AppTab.Core -> coreApps
    }
}

class KillerViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context = app.applicationContext
    private val prefs = context.getSharedPreferences("killer_setup", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(KillerUiState())
    val state: StateFlow<KillerUiState> = _state.asStateFlow()

    private var closeJob: Job? = null
    private var undoJob: Job? = null
    private var loadJob: Job? = null
    private var confirmedInSession = false

    /**
     * Slice 3: floats the closing progress above Settings while apps close.
     * Mirrors this ViewModel's progress state; Cancel routes to [cancelClosing].
     */
    private val overlayController = ClosingOverlayController(
        context = context,
        state = _state,
        onCancel = { cancelClosing() },
    )

    init {
        _state.update { it.copy(screen = startScreen()) }
        refreshPermissions()
        loadApps()
    }

    // ------------------------------------------------------------ permissions

    fun refreshPermissions() {
        val usage = AppKillerEngine.hasUsageAccessPermission(context)
        val a11y = AppKillerEngine.isAccessibilityEnabled(context)
        // Re-checked on every return from Settings, like the other two. The
        // overlay permission never forces the Permissions screen: closing
        // works with or without it.
        val overlay = hasOverlayPermission(context)
        _state.update {
            it.copy(
                usageAccess = usage,
                accessibility = a11y,
                overlayPermission = overlay,
                screen = if ((!usage || !a11y) && it.screen != Screen.Closing) Screen.Permissions else it.screen,
            )
        }
    }

    fun openUsageAccessSettings() = AppKillerEngine.openUsageAccessSettings(context)

    fun openAccessibilitySettings() = AppKillerEngine.openAccessibilitySettings(context)

    fun openOverlayPermissionSettings() = dev.killer.ui.overlay.openOverlayPermissionSettings(context)

    fun continueFromPermissions() {
        if (!_state.value.usageAccess || !_state.value.accessibility) return
        markSetupDone()
        _state.update { it.copy(screen = Screen.Home) }
        loadApps()
    }

    private fun setupDone(): Boolean = prefs.getBoolean("completed", false)

    private fun markSetupDone() = prefs.edit().putBoolean("completed", true).apply()

    private fun startScreen(): Screen = if (setupDone()) Screen.Home else Screen.Permissions

    // ------------------------------------------------------------------ apps

    fun loadApps() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) {
                // The extracted engine does detection: core filter, stopped check,
                // accessibility observations, UsageStats window, ActivityManager union.
                val installed = AppKillerEngine.loadInstalledApps(context)
                val minutes = foregroundMinutes()
                val launchers = AppKillerEngine.launcherPackages(context)
                fun isLauncher(packageName: String) = launchers?.contains(packageName) ?: true
                fun row(info: AppInfo): HomeApp = HomeApp(
                    packageName = info.packageName,
                    label = info.label,
                    runningMinutes = minutes[info.packageName] ?: 0L,
                    keepReason = if (AppCategories.isCore(info.packageName) || isLauncher(info.packageName))
                        KeepReason.KillerItself else KeepSafe.reasonToKeep(context, info.packageName),
                )
                val running = installed.filter { AppCategories.isRunning(it) && !isLauncher(it.packageName) }
                    .map(::row).sortedWith(compareByDescending<HomeApp> { it.isKept }.thenBy { it.label.lowercase() })
                val other = installed.filter { AppCategories.belongsToOtherTab(it) && !isLauncher(it.packageName) }.map(::row)
                val core = installed.filter { it.packageName != context.packageName && (AppCategories.isCore(it.packageName) || isLauncher(it.packageName)) }.map(::row)
                Triple(running, other, core)
            }
            _state.update { it.copy(apps = rows.first, otherApps = rows.second, coreApps = rows.third, loading = false,
                selectedRunning = it.selectedRunning.intersect(rows.first.filterNot { row -> row.isKept }.map { row -> row.packageName }.toSet()),
                selectedOther = it.selectedOther.intersect(rows.second.filterNot { row -> row.isKept }.map { row -> row.packageName }.toSet())) }
        }
    }

    /** Real foreground minutes in the extracted engine's 7-day usage window. */
    private fun foregroundMinutes(): Map<String, Long> {
        if (!AppKillerEngine.hasUsageAccessPermission(context)) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? android.app.usage.UsageStatsManager ?: return emptyMap()
        val now = System.currentTimeMillis()
        return try {
            val stats = usm.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_BEST,
                now - AppKillerEngine.USAGE_STATS_WINDOW_MS,
                now,
            ) ?: return emptyMap()
            val out = HashMap<String, Long>()
            for (u in stats) {
                val pkg = u.packageName?.trim().orEmpty()
                if (pkg.isEmpty()) continue
                val mins = u.totalTimeInForeground / 60_000L
                if (mins > 0) out[pkg] = (out[pkg] ?: 0L) + mins
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun goHome() {
        _state.update { it.copy(screen = if (it.usageAccess && it.accessibility) Screen.Home else Screen.Permissions, phase = ClosingPhase.Idle, tab = AppTab.Running) }
        loadApps()
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }
    fun toggleRunning(packageName: String) = _state.update {
        it.copy(selectedRunning = if (packageName in it.selectedRunning) it.selectedRunning - packageName else it.selectedRunning + packageName)
    }
    fun selectRunning() = _state.update { it.copy(selectedRunning = it.apps.filterNot { row -> row.isKept }.map { row -> row.packageName }.toSet()) }
    fun clearRunning() = _state.update { it.copy(selectedRunning = emptySet()) }
    fun closeSelectedRunning() {
        val s = _state.value
        startClosing(s.apps.filter { it.packageName in s.selectedRunning && !it.isKept }, s.keptRows)
    }
    fun toggleOther(packageName: String) = _state.update {
        it.copy(selectedOther = if (packageName in it.selectedOther) it.selectedOther - packageName else it.selectedOther + packageName)
    }
    fun selectOther() = _state.update { it.copy(selectedOther = it.otherApps.filterNot { row -> row.isKept }.map { row -> row.packageName }.toSet()) }
    fun clearOther() = _state.update { it.copy(selectedOther = emptySet()) }
    fun closeSelectedOther() {
        val s = _state.value
        startClosing(s.otherApps.filter { it.packageName in s.selectedOther && !it.isKept }, s.keptRows)
    }

    fun setTab(tab: AppTab) = _state.update { it.copy(tab = tab) }

    fun showToast(text: String?) = _state.update { it.copy(toast = text) }

    // --------------------------------------------------------------- closing

    /** Per-app close uses the same protected engine and report as Close All. */
    fun closeOne(app: HomeApp) {
        if (!app.isKept) startClosing(listOf(app), _state.value.keptRows)
    }

    fun closeAll() = startClosing(_state.value.closable, _state.value.keptRows)

    fun dismissConfirmation() = _state.update { it.copy(pendingTargets = emptyList(), pendingKept = emptyList()) }
    fun confirmClosing() {
        val s = _state.value
        confirmedInSession = true
        dismissConfirmation()
        startClosing(s.pendingTargets, s.pendingKept)
    }

    private fun startClosing(targets: List<HomeApp>, kept: List<HomeApp>) {
        if (closeJob?.isActive == true || targets.isEmpty()) return
        if (!confirmedInSession) {
            _state.update { it.copy(pendingTargets = targets, pendingKept = kept) }
            return
        }
        if (!_state.value.usageAccess || !_state.value.accessibility) {
            _state.update { it.copy(screen = Screen.Permissions) }
            return
        }
        undoJob?.cancel()

        _state.update {
            it.copy(
                screen = Screen.Closing,
                phase = ClosingPhase.Running,
                closingTotal = targets.size,
                closingIndex = 0,
                ringProgress = 0f,
                closingLabel = targets.firstOrNull()?.label ?: "",
                closedCount = 0,
                skippedCount = 0,
                skippedDetails = emptyList(),
                keptSafeCount = kept.size,
                overlayAvailable = false,
                overlayReshows = 0,
                overlayHideLog = emptyList(),
                overlayGeometry = "",
                undoAvailable = false,
                undoSecondsLeft = 0,
            )
        }

        closeJob = viewModelScope.launch {
            try {
                // Show the progress overlay and confirm it is drawn BEFORE the
                // first Settings intent fires. Without the overlay permission
                // this returns false at once and closing falls back to the
                // in-app Closing screen, exactly as before. The result is kept
                // in state so the Done screen can say plainly when the overlay
                // was off. (Slice 7 blamed the permission; the user confirmed
                // it is ON — "Appear on top" — and the bug stayed, so the
                // overlay's fate is now tracked per run instead of guessed.)
                // Slice 9: point the window at the running AccessibilityService
                // (TYPE_ACCESSIBILITY_OVERLAY — the only type the system does
                // not hide over Settings). Without the service, fall back to
                // the app-context overlay; the engine fails the run anyway
                // when the service is not running.
                overlayController.resetRunStats()
                val service = AppKillerAccessibilityService.instance
                if (service != null) {
                    overlayController.useServiceContext(service)
                } else {
                    overlayController.useAppContext()
                }
                val overlayShown = overlayController.showAndAwait()
                Log.d("Killer", "startClosing: overlay shown=$overlayShown")
                _state.update { it.copy(overlayAvailable = overlayShown) }
                // Let the Closing UI render before launching the system Settings UI.
                delay(400)
                val labels = targets.associate { it.packageName to it.label }

                val report = withContext(Dispatchers.IO) {
                    // The extracted engine runs the loop, cancels cleanly, and counts
                    // only dismissals that actually happened.
                    AppKillerEngine.executeKillList(
                        context = context,
                        packagesToKill = targets.map { it.packageName },
                        appLabels = labels,
                        protectedPackages = kept.map { it.packageName }.toSet(),
                        onProgress = { p ->
                            val fraction = if (p.total == 0) 0f else (p.current - 1).toFloat() / p.total
                            Log.d("Killer", "progress: app ${p.current}/${p.total} ${p.currentLabel}")
                            // Slice 8: if the overlay window vanished mid-loop, bring it
                            // back now — the next app's Settings must never be visible.
                            overlayController.ensureShown()
                            _state.update {
                                it.copy(
                                    closingIndex = p.current,
                                    closingLabel = p.currentLabel,
                                    ringProgress = fraction.coerceIn(0f, 1f),
                                )
                            }
                        },
                    )
                }

                // The ring never jumps: ease the last leg to 100% over one app's pace.
                animateRingTo(1f)

                // At Done the overlay goes away and the Done screen shows.
                overlayController.hide("done")

                _state.update {
                    it.copy(
                        phase = ClosingPhase.Done,
                        selectedRunning = emptySet(),
                        selectedOther = emptySet(),
                        ringProgress = 1f,
                        closedCount = report.killed.size,
                        skippedCount = report.skipped.count { it.second != "Protected" },
                        skippedDetails = report.skipped.map { (pkg, reason) -> "${labels[pkg] ?: pkg}: $reason" },
                        keptSafeCount = kept.size + report.skipped.count { it.second == "Protected" },
                        overlayReshows = overlayController.reshowCount,
                        overlayHideLog = overlayController.hideEvents.filter {
                            !it.startsWith("done") && !it.startsWith("finally")
                        },
                        overlayGeometry = overlayController.geometryReport(),
                        undoAvailable = report.killed.isNotEmpty(),
                        undoSecondsLeft = if (report.killed.isNotEmpty()) 5 else 0,
                    )
                }
                startUndoCountdown()
                loadApps()
            } finally {
                // Every exit path (Done, Cancel, error) removes the overlay.
                overlayController.hide("finally")
            }
        }
    }

    private suspend fun animateRingTo(target: Float) {
        val from = _state.value.ringProgress
        val steps = 12
        for (i in 1..steps) {
            delay(25)
            val v = from + (target - from) * (i.toFloat() / steps)
            _state.update { it.copy(ringProgress = v) }
        }
    }

    private fun startUndoCountdown() {
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            var left = 5
            while (left > 0) {
                delay(1000)
                left--
                _state.update { it.copy(undoSecondsLeft = left) }
            }
            _state.update { it.copy(undoAvailable = false) }
        }
    }

    /** Cancel stops cleanly and returns to Home with whatever is left. */
    fun cancelClosing() {
        // The overlay's Cancel lands here too: stop immediately, drop the card.
        overlayController.hide("cancel")
        closeJob?.cancel()
        closeJob = null
        _state.update {
            it.copy(
                screen = Screen.Home,
                phase = ClosingPhase.Idle,
                ringProgress = 0f,
                toast = "Closing stopped — the rest stayed open.",
            )
        }
        loadApps()
    }

    /** Undo clears the Done state and re-reads what is actually open. It never claims to relaunch apps. */
    fun undo() {
        undoJob?.cancel()
        _state.update {
            it.copy(undoAvailable = false, undoSecondsLeft = 0, screen = Screen.Home, phase = ClosingPhase.Idle)
        }
        loadApps()
        showToast("List refreshed")
    }

    fun dismissToast() = _state.update { it.copy(toast = null) }

    override fun onCleared() {
        overlayController.hide("cleared")
        closeJob?.cancel()
        undoJob?.cancel()
        super.onCleared()
    }
}
