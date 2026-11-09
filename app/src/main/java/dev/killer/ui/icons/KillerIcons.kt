package dev.killer.ui.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The concept draws its icons with Lucide (24×24 grid, 2dp stroke, round caps and
 * joins). The `d` strings below are copied verbatim from `killer-ui-concept.html`,
 * so the shapes on screen are the approved ones rather than an approximation.
 */
private fun lucide(
    name: String,
    vararg paths: String,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    paths.forEach { d ->
        path(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black), // tinted at draw time
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

object KillerIcons {
    /** `Eye` — "See what's running". */
    val Eye: ImageVector = lucide(
        "Eye",
        "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0",
        "M15 12a3 3 0 1 1-6 0a3 3 0 0 1 6 0Z",
    )

    /** `Hand` — "Close apps for you". */
    val Hand: ImageVector = lucide(
        "Hand",
        "M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2",
        "M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2",
        "M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8",
        "M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15",
    )

    /** `X` — the one close affordance on an app card. */
    val X: ImageVector = lucide("X", "M18 6 6 18", "m6 6 12 12")

    /** `Undo2` — the five-second Undo. */
    val Undo: ImageVector = lucide(
        "Undo2",
        "M9 14 4 9l5-5",
        "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11",
    )

    /** `ShieldCheck` — Kept safe. */
    val ShieldCheck: ImageVector = lucide(
        "ShieldCheck",
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
        "m9 12 2 2 4-4",
    )

    /** `Lock` — each kept-safe row. */
    val Lock: ImageVector = lucide(
        "Lock",
        "M7 11V7a5 5 0 0 1 10 0v4",
        "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2Z",
    )

    /** `Moon` — Quiet Mode pill. */
    val Moon: ImageVector = lucide("Moon", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z")

    /** `Check` — mint check for Done and for enabled permissions. */
    val Check: ImageVector = lucide("Check", "M20 6 9 17l-5-5")

    /** `ChevronDown` — Kept safe disclosure. */
    val ChevronDown: ImageVector = lucide("ChevronDown", "m6 9 6 6 6-6")

    /** `Zap` — Battery Saver intention. */
    val Zap: ImageVector = lucide(
        "Zap",
        "M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z",
    )

    /** Drawn at any size with the caller's tint. Purely decorative — never the only cue. */
    @Composable
    fun Draw(vector: ImageVector, sizeDp: Int, tint: Color, modifier: Modifier = Modifier) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(sizeDp.dp),
        )
    }
}
