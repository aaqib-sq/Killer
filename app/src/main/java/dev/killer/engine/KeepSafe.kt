package dev.killer.engine

import android.content.Context

/**
 * Keep Safe — the automatic protection layer, built on top of the extracted
 * TRMX engine.
 *
 * TRMX's "safe list" was a static set of package names (AppCategories.CORE_PACKAGES).
 * Killer keeps that blocklist untouched. Slice 6 (2026-11-10, the user's
 * decision): the live rules NO LONGER lock any app. Music, navigation,
 * calls, timers, alarms and downloads never block closing — every app is
 * closable and the user chooses what to close. Only the core blocklist
 * (Killer itself, SystemUI, Settings, the launcher) stays absolute: closing
 * those would break the phone's own screen, not just an app.
 *
 * Slice 7 (2026-11-10): the live-signal helpers (music/call/alarm/download
 * probes, the function classifier and the app-level reason vocabulary) are
 * deleted — nothing gates on them any more and the user asked for the
 * leftover code from the removed rules to be cleaned out. Only the core
 * rule remains.
 */
object KeepSafe {

    /**
     * Why [packageName] must stay open right now, or null when it may be closed.
     * Only Killer itself and the extracted engine's core blocklist are ever
     * kept; every other app returns null and is closable.
     */
    fun reasonToKeep(context: Context, packageName: String): KeepReason? {
        if (packageName == context.packageName) return KeepReason.KillerItself
        // The extracted engine's blocklist is absolute: a core package is never closed.
        if (AppCategories.isCore(packageName)) return KeepReason.KillerItself
        return null
    }
}

/** The Keep Safe reasons. Only the core rule survives Slice 7. */
enum class KeepReason(val label: String) {
    KillerItself("Core app"),
}
