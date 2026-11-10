package dev.killer.ui.overlay

/**
 * Pure formatting for the closing-progress overlay card.
 *
 * Kept free of Android types so the queue/percent wording is unit-testable
 * on the JVM. The overlay mirrors the ViewModel's progress state
 * (`ringProgress`, `closingIndex`/`closingTotal`, `closingLabel`) — these
 * helpers only format that single source of truth, they never compute it.
 */

/** "42%" for a 0..1 ring fraction, clamped so the card can never show 101%. */
fun overlayPercentText(progress: Float): String {
    val percent = (progress.coerceIn(0f, 1f) * 100f).toInt()
    return "$percent%"
}

/**
 * "App 2 of 5" for the queue position. The ViewModel reports a 1-based
 * index that is 0 before the first app starts, so the display index is
 * clamped into 1..total (and the total is never shown as 0).
 */
fun overlayPositionText(index: Int, total: Int): String {
    val safeTotal = total.coerceAtLeast(1)
    val safeIndex = index.coerceIn(1, safeTotal)
    return "App $safeIndex of $safeTotal"
}

/** "Closing WhatsApp…" — the current app line, matching the Closing screen. */
fun overlayCurrentAppText(label: String): String = "Closing $label…"
