package dev.killer.engine

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

sealed class KillResult {
    data class Killed(val packageName: String) : KillResult()
    data class Skipped(val packageName: String, val reason: String) : KillResult()
}

data class KillProgress(
    val current: Int,
    val total: Int,
    val currentPackage: String,
    val currentLabel: String,
)

data class KillReport(
    val totalTargeted: Int,
    val killed: List<String>,
    val skipped: List<Pair<String, String>>, // (packageName, reason)
)

/**
 * Core engine for Killer.
 *
 * ADAPTED FROM TRMX-GUI: the detection half is byte-for-byte the PR #70 engine;
 * the destructive half is replaced. TRMX drove Settings -> Force stop. Killer
 * dismisses the app's card from Recents instead, because the concept promises
 * "closes apps from Recents, in one tap" and never claims force-stop.
 *
 * Privacy & Security Guarantee:
 * STRICTLY ON-DEVICE ONLY. All UsageStats, Accessibility observations, and package metadata
 * are processed solely in local device memory and local private storage. ZERO network calls,
 * zero HTTP requests, zero uploads, zero analytics, zero ad SDKs, and zero telemetry.
 * No data leaves the device.
 */
object AppKillerEngine {

    /**
     * Recent window for UsageStats detection: 7 days.
     * Packages active within this window are detected as running.
     */
    const val USAGE_STATS_WINDOW_MS: Long = 7 * 24 * 60 * 60 * 1000L // 7 days

    private val killedTimestamps = ConcurrentHashMap<String, Long>()

    @Volatile
    var isPackageStoppedOverride: ((String) -> Boolean)? = null

    /**
     * Shared helper to check if an app has ApplicationInfo.FLAG_STOPPED set.
     * Reused across loadInstalledApps, loadDiagnosticData, and executeKillList pre-check.
     */
    fun isStopped(flags: Int): Boolean {
        return (flags and ApplicationInfo.FLAG_STOPPED) != 0
    }

    fun isStopped(appInfo: ApplicationInfo?): Boolean {
        if (appInfo == null) return false
        return isStopped(appInfo.flags)
    }

    fun isPackageStopped(context: Context, packageName: String): Boolean {
        isPackageStoppedOverride?.let { return it(packageName) }
        return try {
            val pm = context.packageManager
            // minSdk is 33, so ApplicationInfoFlags always exists; no deprecated
            // integer overload is needed (it would have to hit a hidden framework id).
            val appInfo = pm.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0L),
            )
            isStopped(appInfo)
        } catch (_: Exception) {
            false
        }
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        if (AppKillerAccessibilityService.isRunning) return true

        val expectedComponentName = ComponentName(context, AppKillerAccessibilityService::class.java)
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (am != null) {
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledServices != null) {
                for (service in enabledServices) {
                    val serviceInfo = service.resolveInfo?.serviceInfo
                    if (serviceInfo != null &&
                        serviceInfo.packageName == expectedComponentName.packageName &&
                        serviceInfo.name == expectedComponentName.className
                    ) {
                        return true
                    }
                }
            }
        }

        try {
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

            val expectedShort = expectedComponentName.flattenToString()
            val expectedLong = "${expectedComponentName.packageName}/${expectedComponentName.className}"

            val parts = enabledServicesSetting.split(":")
            for (part in parts) {
                if (part.equals(expectedShort, ignoreCase = true) ||
                    part.equals(expectedLong, ignoreCase = true)
                ) {
                    return true
                }
            }
        } catch (_: Exception) {
        }

        return false
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun hasUsageAccessPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    /**
     * Queries recent app activity via UsageStatsManager on-device.
     *
     * Privacy guarantee: All UsageStats data is strictly queried and processed on-device
     * solely to determine recently active packages for the Kill screen. No usage data leaves the device.
     */
    fun getActivePackagesFromUsageStats(context: Context, windowMs: Long = USAGE_STATS_WINDOW_MS): Set<String> {
        if (!hasUsageAccessPermission(context)) return emptySet()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptySet()
        val now = System.currentTimeMillis()
        val startTime = now - windowMs

        return try {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, now) ?: return emptySet()
            val active = mutableSetOf<String>()
            for (usage in stats) {
                val pkg = usage.packageName ?: continue
                val trimmed = pkg.trim()
                if (trimmed.isEmpty() || AppCategories.isCore(trimmed)) continue

                val lastUsed = usage.lastTimeUsed
                val lastVisible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) usage.lastTimeVisible else 0L
                val mostRecent = maxOf(lastUsed, lastVisible)

                if (mostRecent >= startTime) {
                    val killedAt = getPackageLastKilledTimestamp(trimmed)
                    if (mostRecent > killedAt) {
                        active.add(trimmed)
                    }
                }
            }
            active
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun markPackageKilled(context: Context?, packageName: String) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        killedTimestamps[pkg] = System.currentTimeMillis()
        AppKillerAccessibilityService.removeObservedPackage(context, pkg)
    }

    fun getPackageLastKilledTimestamp(packageName: String): Long {
        return killedTimestamps[packageName.trim()] ?: 0L
    }

    /**
     * Determines active running packages using a union of two on-device signals:
     * 1. Window events observed by AppKillerAccessibilityService (persisted locally across screen-off).
     * 2. Recent usage stats within the 24-hour window (UsageStatsManager).
     *
     * Core packages are strictly excluded.
     */
    fun getActivePackages(context: Context, installedPackages: Set<String>? = null): Set<String> {
        val active = mutableSetOf<String>()

        // Signal 1: Accessibility service observed foreground packages
        active.addAll(AppKillerAccessibilityService.getObservedPackages(context))

        // Signal 2: UsageStats recent activity (24-hour window)
        active.addAll(getActivePackagesFromUsageStats(context, USAGE_STATS_WINDOW_MS))

        // Fallback: ActivityManager running processes (for older Android / emulators)
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val runningProcesses = am?.runningAppProcesses
            if (runningProcesses != null) {
                for (proc in runningProcesses) {
                    val pkgList = proc.pkgList ?: continue
                    for (pkg in pkgList) {
                        val trimmed = pkg?.trim() ?: continue
                        if (trimmed.isNotEmpty()) {
                            active.add(trimmed)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Filter: exclude core packages and uninstalled packages
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val pkg = iterator.next()
            if (AppCategories.isCore(pkg) || (installedPackages != null && !installedPackages.contains(pkg))) {
                iterator.remove()
            }
        }

        return active
    }

    fun getRunningPackages(context: Context, installedPackages: Set<String>? = null): Set<String> {
        return getActivePackages(context, installedPackages)
    }

    fun loadInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }

        val installedSet = packages.mapNotNull { it.packageName }.toSet()
        // Prune any uninstalled packages from accessibility cache
        AppKillerAccessibilityService.pruneObservedPackages(context, installedSet)

        val activePackages = getActivePackages(context, installedSet)

        val result = ArrayList<AppInfo>(packages.size)
        for (pkgInfo in packages) {
            val pkg = pkgInfo.packageName ?: continue
            val appInfo = pkgInfo.applicationInfo

            val category = AppCategories.categoryFor(pkg)

            val label = try {
                appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            } ?: pkg

            val isSystem = appInfo != null && (
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            )

            val isStopped = isStopped(appInfo)
            val isActive = activePackages.contains(pkg)
            val isRunning = AppCategories.isRunning(
                packageName = pkg,
                isStopped = isStopped,
                isActive = isActive,
            )

            result.add(
                AppInfo(
                    packageName = pkg,
                    label = label,
                    category = category,
                    isSystem = isSystem,
                    isRunning = isRunning,
                    isStopped = isStopped,
                    isActive = isActive,
                ),
            )
        }

        result.sortBy { it.label.lowercase() }
        return result
    }

    fun getActivityManagerRunningPackages(context: Context): Set<String> {
        val result = mutableSetOf<String>()
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val runningProcesses = am?.runningAppProcesses
            if (runningProcesses != null) {
                for (proc in runningProcesses) {
                    val pkgList = proc.pkgList ?: continue
                    for (pkg in pkgList) {
                        val trimmed = pkg?.trim() ?: continue
                        if (trimmed.isNotEmpty() && !AppCategories.isCore(trimmed)) {
                            result.add(trimmed)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun queryAllUsageStats(context: Context): Map<String, Long> {
        if (!hasUsageAccessPermission(context)) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyMap()
        val now = System.currentTimeMillis()
        val map = mutableMapOf<String, Long>()

        fun aggregate(stats: List<UsageStats>?) {
            if (stats == null) return
            for (usage in stats) {
                val pkg = usage.packageName?.trim() ?: continue
                if (pkg.isEmpty()) continue
                val lastUsed = usage.lastTimeUsed
                val lastVisible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) usage.lastTimeVisible else 0L
                val mostRecent = maxOf(lastUsed, lastVisible)
                if (mostRecent > 0) {
                    val current = map[pkg] ?: 0L
                    if (mostRecent > current) {
                        map[pkg] = mostRecent
                    }
                }
            }
        }

        try {
            val stats24h = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - USAGE_STATS_WINDOW_MS, now)
            aggregate(stats24h)
            val stats30d = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - (30L * 24 * 60 * 60 * 1000L), now)
            aggregate(stats30d)
        } catch (_: Exception) {
        }
        return map
    }

    fun formatHoursAgo(lastTimeUsedMs: Long?, now: Long = System.currentTimeMillis()): String {
        if (lastTimeUsedMs == null || lastTimeUsedMs <= 0L) return "no stats"
        val diffMs = now - lastTimeUsedMs
        if (diffMs < 0L) return "<1h ago"
        val hours = diffMs / 3600000.0
        return if (hours < 1.0) {
            val mins = (diffMs / 60000L).coerceAtLeast(0)
            "<1h ago (${mins}m)"
        } else {
            String.format(Locale.US, "%.1fh ago", hours)
        }
    }

    fun computeNotRunningReason(
        isCore: Boolean,
        isStopped: Boolean,
        killedAt: Long,
        lastTimeUsedMs: Long?,
        startTime7d: Long,
        isAccessibilityObserved: Boolean,
        hasUsageAccess: Boolean,
    ): String {
        if (isCore) {
            return "core-blocked"
        }
        val mostRecent = lastTimeUsedMs ?: 0L
        val wasKilledThisSession = killedAt > 0L && mostRecent <= killedAt
        if (isStopped || wasKilledThisSession) {
            return "stopped"
        }
        if (!hasUsageAccess && !isAccessibilityObserved) {
            return "usage access not granted and not observed"
        }
        if (mostRecent > 0L && mostRecent < startTime7d) {
            return "not active in 7-day window"
        }
        if (mostRecent <= 0L && !isAccessibilityObserved) {
            return "no usage stats and not observed"
        }
        return "not active in 7-day window and not observed"
    }

    fun loadDiagnosticData(context: Context): KillerDiagnosticData {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }

        val installedSet = packages.mapNotNull { it.packageName }.toSet()
        val isA11yEnabled = isAccessibilityEnabled(context)
        val hasUsage = hasUsageAccessPermission(context)

        // 1. Accessibility observed
        val observedPackages = AppKillerAccessibilityService.getObservedPackages(context)
        val a11yObservedCount = observedPackages.count { installedSet.contains(it) && !AppCategories.isCore(it) }

        // 2. UsageStats 7-day window
        val usageStats7dActive = getActivePackagesFromUsageStats(context, USAGE_STATS_WINDOW_MS)
        val usageStatsCount = usageStats7dActive.size

        // 3. ActivityManager fallback
        val amFallbackPackages = getActivityManagerRunningPackages(context)
        val amFallbackCount = amFallbackPackages.count { installedSet.contains(it) && !AppCategories.isCore(it) }

        // Active packages union (signals)
        val activePackages = getActivePackages(context, installedSet)

        // UsageStats for lastTimeUsed
        val usageStatsMap = queryAllUsageStats(context)

        val now = System.currentTimeMillis()
        val startTime7d = now - USAGE_STATS_WINDOW_MS

        val diagnosticApps = ArrayList<AppDiagnosticInfo>(packages.size)

        for (pkgInfo in packages) {
            val pkg = pkgInfo.packageName ?: continue
            val appInfo = pkgInfo.applicationInfo

            val label = try {
                appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            } ?: pkg

            val isSystem = appInfo != null && (
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            )

            val isStopped = isStopped(appInfo)

            val lastTimeUsedMs = usageStatsMap[pkg]
            val lastTimeUsedFormatted = formatHoursAgo(lastTimeUsedMs, now)

            val isObserved = observedPackages.contains(pkg)
            val isActive = activePackages.contains(pkg)

            // Single source of truth predicate (Item 2)
            val inRunning = AppCategories.isRunning(
                packageName = pkg,
                isStopped = isStopped,
                isActive = isActive,
            )

            val isCore = AppCategories.isCore(pkg)
            val killedAt = getPackageLastKilledTimestamp(pkg)

            val notRunningReason = if (!inRunning) {
                computeNotRunningReason(
                    isCore = isCore,
                    isStopped = isStopped,
                    killedAt = killedAt,
                    lastTimeUsedMs = lastTimeUsedMs,
                    startTime7d = startTime7d,
                    isAccessibilityObserved = isObserved,
                    hasUsageAccess = hasUsage,
                )
            } else {
                null
            }

            diagnosticApps.add(
                AppDiagnosticInfo(
                    packageName = pkg,
                    label = label,
                    isSystem = isSystem,
                    lastTimeUsedMs = lastTimeUsedMs,
                    lastTimeUsedFormatted = lastTimeUsedFormatted,
                    isAccessibilityObserved = isObserved,
                    isStopped = isStopped,
                    inRunning = inRunning,
                    notRunningReason = notRunningReason,
                    isActive = isActive,
                ),
            )
        }

        diagnosticApps.sortBy { it.label.lowercase() }

        val runningCount = diagnosticApps.count { it.inRunning }

        return KillerDiagnosticData(
            isAccessibilityEnabled = isA11yEnabled,
            isUsageAccessGranted = hasUsage,
            totalInstalledApps = packages.size,
            usageStatsCount = usageStatsCount,
            accessibilityObservedCount = a11yObservedCount,
            activityManagerFallbackCount = amFallbackCount,
            runningCount = runningCount,
            apps = diagnosticApps,
        )
    }

    suspend fun executeKillList(
        context: Context,
        packagesToKill: List<String>,
        appLabels: Map<String, String>,
        onProgress: (KillProgress) -> Unit = {},
        serviceProvider: () -> AppKillerAccessibilityService? = { AppKillerAccessibilityService.instance },
        startActivityAction: (Intent) -> Unit = { context.startActivity(it) },
    ): KillReport {
        val eligible = mutableListOf<String>()
        val skipped = mutableListOf<Pair<String, String>>()
        val killed = mutableListOf<String>()

        // 1. Enforce CORE blocklist in engine
        for (pkg in packagesToKill) {
            if (AppCategories.isCore(pkg)) {
                skipped.add(pkg to "Core system package (protected)")
            } else {
                eligible.add(pkg)
            }
        }

        // 2. Verify accessibility service instance
        val service = serviceProvider()
        if (service == null) {
            for (pkg in eligible) {
                skipped.add(pkg to "Accessibility service not running")
            }
            return KillReport(
                totalTargeted = packagesToKill.size,
                killed = killed,
                skipped = skipped,
            )
        }

        val total = eligible.size
        for ((index, pkg) in eligible.withIndex()) {
            if (!coroutineContext.isActive) {
                for (remIndex in index until eligible.size) {
                    skipped.add(eligible[remIndex] to "Cancelled by user")
                }
                break
            }

            val label = appLabels[pkg] ?: pkg
            onProgress(KillProgress(current = index + 1, total = total, currentPackage = pkg, currentLabel = label))

            // Pre-check: if already stopped, skip immediately WITHOUT opening Settings
            if (isPackageStopped(context, pkg)) {
                markPackageKilled(context, pkg)
                skipped.add(pkg to "Already stopped")
                continue
            }

            // RECENTS DISMISSAL (this is the whole adaptation).
            // TRMX opened App info and clicked "Force stop" here. Killer opens
            // Recents and dismisses the app's card with the same swipe a person
            // makes. Nothing is force-stopped, and nothing here claims to be.
            val dismissed = try {
                service.dismissFromRecents(label, pkg)
            } catch (e: Exception) {
                skipped.add(pkg to "Couldn't reach Recents: ${e.message ?: "unknown"}")
                false
            }

            if (dismissed) {
                markPackageKilled(context, pkg)
                killed.add(pkg)
            } else {
                skipped.add(pkg to "Not in Recents")
            }
        }

        // No activity hop: the Closing screen is Killer's own, never a system screen.

        return KillReport(
            totalTargeted = packagesToKill.size,
            killed = killed,
            skipped = skipped,
        )
    }
}
