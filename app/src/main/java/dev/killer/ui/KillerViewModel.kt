package dev.killer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.killer.engine.AppInfo
import dev.killer.engine.AppKillerAccessibilityService
import dev.killer.engine.AppKillerEngine
import dev.killer.engine.AppCategories
import dev.killer.engine.Intention
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

/** Exactly four screens. Quiet Mode is opened from Home; it is not a fifth tab. */
enum class Screen { Permissions, Home, Closing, QuietMode, Diagnostics }
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
    val diagnostics: dev.killer.engine.KillerDiagnosticData? = null,
    val undoSecondsLeft: Int = 0,
    val undoAvailable: Boolean = false,
    val intention: Intention = Intention.Focus,
    val keptSafeExpanded: Boolean = false,
    val unkept: Set<String> = emptySet(),
    val toast: String? = null,
    val query: String = "",
    val selectedOther: Set<String> = emptySet(),
    val pendingTargets: List<HomeApp> = emptyList(),
    val pendingKept: List<HomeApp> = emptyList(),
) {
    val count: Int get() = apps.size
    val closable: List<HomeApp> get() = apps.filter { !it.isKept }
    val keptRows: List<HomeApp> get() = apps.filter { it.isKept }
    val quietKeptRows: List<HomeApp> get() = apps.filter {
        it.isKept || (it.packageName !in unkept && KeepSafe.keptByIntention(it.packageName, it.label, intention))
    }
    val quietTargets: List<HomeApp> get() = apps.filterNot { app -> quietKeptRows.any { it.packageName == app.packageName } }
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
                        KeepReason.KillerItself else KeepSafe.reasonToKeep(context, info.packageName, info.label),
                )
                val running = installed.filter { AppCategories.isRunning(it) && !isLauncher(it.packageName) }
                    .map(::row).sortedWith(compareByDescending<HomeApp> { it.isKept }.thenBy { it.label.lowercase() })
                val other = installed.filter { AppCategories.belongsToOtherTab(it) && !isLauncher(it.packageName) }.map(::row)
                val core = installed.filter { it.packageName != context.packageName && (AppCategories.isCore(it.packageName) || isLauncher(it.packageName)) }.map(::row)
                Triple(running, other, core)
            }
            _state.update { it.copy(apps = rows.first, otherApps = rows.second, coreApps = rows.third, loading = false,
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

    fun openDiagnostics() {
        _state.update { it.copy(screen = Screen.Diagnostics, diagnostics = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { AppKillerEngine.loadDiagnosticData(context) }
            _state.update { it.copy(diagnostics = result) }
        }
    }

    fun openQuietMode() = _state.update { it.copy(screen = Screen.QuietMode, keptSafeExpanded = false) }

    fun setIntention(intention: Intention) = _state.update { it.copy(intention = intention, unkept = emptySet()) }

    fun toggleKeptSafe() = _state.update { it.copy(keptSafeExpanded = !it.keptSafeExpanded) }

    fun unkeep(packageName: String) = _state.update { it.copy(unkept = it.unkept + packageName) }

    fun rekeep(packageName: String) = _state.update { it.copy(unkept = it.unkept - packageName) }

    fun showToast(text: String?) = _state.update { it.copy(toast = text) }

    // --------------------------------------------------------------- closing

    /** Per-app close uses the same protected engine and report as Close All. */
    fun closeOne(app: HomeApp) {
        if (!app.isKept) startClosing(listOf(app), _state.value.keptRows)
    }

    fun closeAll() = startClosing(_state.value.closable, _state.value.keptRows)

    fun makeItQuiet() {
        val s = _state.value
        startClosing(s.quietTargets, s.quietKeptRows)
    }

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
                undoAvailable = false,
                undoSecondsLeft = 0,
            )
        }

        closeJob = viewModelScope.launch {
            try {
                // Show the progress overlay and confirm it is drawn BEFORE the
                // first Settings intent fires. Without the overlay permission
                // this returns false at once and closing falls back to the
                // in-app Closing screen, exactly as before.
                overlayController.showAndAwait()
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
                overlayController.hide()

                _state.update {
                    it.copy(
                        phase = ClosingPhase.Done,
                        selectedOther = emptySet(),
                        ringProgress = 1f,
                        closedCount = report.killed.size,
                        skippedCount = report.skipped.count { it.second != "Protected" },
                        skippedDetails = report.skipped.map { (pkg, reason) -> "${labels[pkg] ?: pkg}: $reason" },
                        keptSafeCount = kept.size + report.skipped.count { it.second == "Protected" },
                        undoAvailable = report.killed.isNotEmpty(),
                        undoSecondsLeft = if (report.killed.isNotEmpty()) 5 else 0,
                    )
                }
                startUndoCountdown()
                loadApps()
            } finally {
                // Every exit path (Done, Cancel, error) removes the overlay.
                overlayController.hide()
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
        overlayController.hide()
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
        overlayController.hide()
        closeJob?.cancel()
        undoJob?.cancel()
        super.onCleared()
    }
}
