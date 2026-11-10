package dev.killer.engine

import android.app.AlarmManager
import android.app.DownloadManager
import android.content.Context
import android.media.AudioManager

/**
 * Keep Safe — the automatic protection layer, built on top of the extracted
 * TRMX engine.
 *
 * TRMX's "safe list" was a static set of package names (AppCategories.CORE_PACKAGES).
 * Killer keeps that blocklist untouched. Slice 6 (2026-11-10, the user's
 * decision): the live rules below NO LONGER lock any app. Music, navigation,
 * calls, timers, alarms and downloads never block closing — every app is
 * closable and the user chooses what to close. Only the core blocklist
 * (Killer itself, SystemUI, Settings, the launcher) stays absolute: closing
 * those would break the phone's own screen, not just an app.
 *
 * The live-signal helpers and the reason vocabulary stay as tested utilities;
 * they no longer gate closing.
 */
object KeepSafe {

    /**
     * Why [packageName] must stay open right now, or null when it may be closed.
     * Only Killer itself and the extracted engine's core blocklist are ever
     * kept; every other app returns null and is closable.
     */
    fun reasonToKeep(context: Context, packageName: String, label: String): KeepReason? {
        if (packageName == context.packageName) return KeepReason.KillerItself
        // The extracted engine's blocklist is absolute: a core package is never closed.
        if (AppCategories.isCore(packageName)) return KeepReason.KillerItself
        return null
    }

    // ----------------------------------------------------------- live signals

    fun isMusicActive(context: Context): Boolean = try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.isMusicActive == true
    } catch (_: Exception) {
        false
    }

    fun isCallActive(context: Context): Boolean = try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val mode = am?.mode
        mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
    } catch (_: Exception) {
        false
    }

    fun pendingAlarmOwner(context: Context): String? = try {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        am?.nextAlarmClock?.showIntent?.creatorPackage
    } catch (_: Exception) {
        null
    }

    fun hasPendingAlarm(context: Context): Boolean = try {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        am?.nextAlarmClock != null
    } catch (_: Exception) {
        false
    }

    fun isDownloading(context: Context): Boolean = try {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        dm?.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_RUNNING))
            ?.use { it.moveToFirst() && it.count > 0 } ?: false
    } catch (_: Exception) {
        false
    }

    /**
     * Offline package-name classification. No metadata service, no network, no SDK:
     * the app's own label and package name are the only inputs.
     */
    fun functionsOf(packageName: String, label: String): Set<KeptFunction> {
        val pkg = packageName.lowercase()
        val name = label.lowercase()
        fun has(vararg needles: String) = needles.any { pkg.contains(it) || name.contains(it) }

        val out = mutableSetOf<KeptFunction>()
        if (has("spotify", "music", "audio", "podcast", "tidal", "deezer", "soundcloud", "ytmusic")) {
            out += KeptFunction.Music
        }
        if (has("notes", "keep", "notion", "obsidian", "onenote", "bear", "joplin")) {
            out += KeptFunction.Notes
        }
        if (has("clock", "alarm", "deskclock", "timer")) out += KeptFunction.Alarm
        if (has("maps", "nav", "waze", "gps", "geo")) out += KeptFunction.Maps
        // "messag" is the shared stem of message / messages / messaging.
        if (has("messag", "sms", "chat", "whatsapp", "telegram", "signal", "discord", "slack")) {
            out += KeptFunction.Messaging
        }
        if (has("dialer", "phone", "telecom", "incallui")) out += KeptFunction.PhoneCall
        return out
    }
}

/** The seven Keep Safe rules. */
enum class KeepReason(val label: String) {
    MusicPlaying("Music playing"),
    Navigating("Navigating"),
    PhoneCall("On a call"),
    Timer("Timer running"),
    Alarm("Alarm set"),
    Downloading("Downloading"),
    KillerItself("Core app"),
    AudioActive("Music is active • kept safe"),
    CallActive("Call is active • kept safe"),
    PossibleNavigation("May be navigating"),
    PossibleTimer("May have a timer or alarm"),
    PossibleDownload("May be downloading"),
}

/** The functions Keep Safe can recognise for automatic protection. */
enum class KeptFunction { Music, Notes, Alarm, Maps, Messaging, PhoneCall }
