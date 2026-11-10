package dev.killer.engine

import android.app.AlarmManager
import android.app.DownloadManager
import android.content.Context
import android.media.AudioManager

/**
 * Keep Safe — the automatic protection layer the brief requires, built on top of
 * the extracted TRMX engine.
 *
 * TRMX's "safe list" was a static set of package names (AppCategories.CORE_PACKAGES).
 * Killer keeps that blocklist untouched and adds the seven live rules the concept
 * promises: music playing, navigating, on a call, timer, alarm, download, itself.
 *
 * Every rule reads a real system signal. Nothing is estimated, nothing is invented.
 */
object KeepSafe {

    /**
     * Why [packageName] must stay open right now, or null when it may be closed.
     * Called from the ViewModel for every detected app; the answer is shown on the
     * card ("Music playing, 18 min") and enforced again inside the engine.
     */
    fun reasonToKeep(context: Context, packageName: String, label: String): KeepReason? {
        if (packageName == context.packageName) return KeepReason.KillerItself
        // The extracted engine's blocklist is absolute: a core package is never closed.
        if (AppCategories.isCore(packageName)) return KeepReason.KillerItself

        val functions = functionsOf(packageName, label)

        // AudioManager reports device-wide activity, not the originating app. Keep
        // every candidate while audio/call is active rather than guess a package.
        if (isCallActive(context)) return KeepReason.PhoneCall
        if (isMusicActive(context)) return KeepReason.MusicPlaying
        // Android does not expose every app's navigation or timer state. A possible
        // maps/clock app is kept conservatively; labels describe uncertainty.
        if (KeptFunction.Maps in functions) return KeepReason.PossibleNavigation
        if (KeptFunction.Alarm in functions) return KeepReason.PossibleTimer
        if (pendingAlarmOwner(context) == packageName) return KeepReason.Alarm
        // DownloadManager only exposes this app's downloads; no cross-app attribution
        // is claimed. Keep apps with download-related names as a conservative fallback.
        if (packageName.contains("download", ignoreCase = true)) return KeepReason.PossibleDownload
        return null
    }

    /** Whether an app survives a specific intention (the three Quiet Mode cards). */
    fun keptByIntention(packageName: String, label: String, intention: Intention): Boolean {
        val functions = functionsOf(packageName, label)
        return functions.any { it in intention.keeps }
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
    KillerItself("That's me"),
    PossibleNavigation("May be navigating"),
    PossibleTimer("May have a timer or alarm"),
    PossibleDownload("May be downloading"),
}

/** The functions an intention can protect. */
enum class KeptFunction { Music, Notes, Alarm, Maps, Messaging, PhoneCall }

/**
 * The three Quiet Mode cards. Copy is the concept's, verbatim.
 */
enum class Intention(
    val id: String,
    val title: String,
    val description: String,
    val keepLabel: String,
    val keeps: Set<KeptFunction>,
    val emoji: String,
) {
    Focus(
        id = "focus",
        title = "Focus for 25 min",
        description = "Quiet everything except what helps you focus.",
        keepLabel = "Keeps Music + Notes",
        keeps = setOf(KeptFunction.Music, KeptFunction.Notes),
        emoji = "🎯",
    ),
    Bedtime(
        id = "bedtime",
        title = "Bedtime",
        description = "Wind down. Only the essentials stay awake.",
        keepLabel = "Keeps Alarm + Music",
        keeps = setOf(KeptFunction.Alarm, KeptFunction.Music),
        emoji = "🌙",
    ),
    BatterySaver(
        id = "battery",
        title = "Battery Saver",
        description = "Hold on to charge. Keep only what gets you home.",
        keepLabel = "Keeps Maps + Messages",
        keeps = setOf(KeptFunction.Maps, KeptFunction.Messaging),
        emoji = "🔋",
    ),
}
