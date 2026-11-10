package dev.killer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.killer.ui.ClosingPhase
import dev.killer.ui.components.OutlineButton
import dev.killer.ui.components.PrimaryButton
import dev.killer.ui.components.SecondaryButton
import dev.killer.ui.icons.KillerIcons
import dev.killer.ui.overlay.overlayPositionText
import dev.killer.ui.theme.KillerColors
import dev.killer.ui.theme.KillerDimens
import dev.killer.ui.theme.KillerMotion
import dev.killer.ui.theme.KillerType

/**
 * Screen 3 — Closing. A full-screen moment, never a toast.
 * 220dp ring, 48sp percentage, the app being closed right now, a seek line
 * for the queue (Slice 7 — the dots are gone), Cancel while running;
 * Done state with mint ring + check + Undo for 5 seconds.
 */
@Composable
fun ClosingScreen(
    phase: ClosingPhase,
    currentLabel: String,
    index: Int,
    total: Int,
    progress: Float,
    closedCount: Int,
    keptSafeCount: Int,
    skippedCount: Int,
    skippedDetails: List<String>,
    undoAvailable: Boolean,
    undoSecondsLeft: Int,
    /** False when the progress overlay could not be shown (usually the "Display over other apps" permission is off on this install) — the Done screen says so plainly. */
    overlayAvailable: Boolean,
    onCancel: () -> Unit,
    onBackHome: () -> Unit,
    onUndo: () -> Unit,
) {
    val done = phase == ClosingPhase.Done
    var showDetails by remember { mutableStateOf(false) }
    if (showDetails) {
        AlertDialog(onDismissRequest = { showDetails = false },
            title = { Text("Not closed") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { skippedDetails.forEach { Text(it) } } },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Done") } })
    }

    // The ring eases; it never jumps.
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = KillerMotion.STANDARD_MS),
        label = "ringProgress",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KillerColors.Background)
            .padding(horizontal = KillerDimens.PagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            text = if (done) "Finished" else "Closing apps",
            color = KillerColors.TextSecondary,
            fontSize = KillerType.Pill,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )

        Spacer(Modifier.height(32.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(KillerDimens.RingSize)) {
            ProgressRing(progress = if (done) 1f else animated, done = done)

            if (done) {
                // Explicit receiver: this Box is not a ColumnScope.
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(KillerMotion.STANDARD_MS)) +
                        scaleIn(tween(KillerMotion.STANDARD_MS), initialScale = 0.96f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(KillerColors.Mint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        KillerIcons.Draw(KillerIcons.Check, 36, KillerColors.Mint)
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(animated * 100).toInt()}%",
                        color = KillerColors.TextPrimary,
                        fontSize = KillerType.ClosingPercent,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = KillerType.TightTracking.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Closing…",
                        color = KillerColors.TextSecondary,
                        fontSize = KillerType.Pill,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = if (done) "Done." else "Closing $currentLabel…",
            color = KillerColors.TextPrimary,
            fontSize = KillerType.Headline,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = KillerType.TightTracking.sp,
            textAlign = TextAlign.Center,
            lineHeight = 31.sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (done) {
                "$closedCount apps closed • $keptSafeCount kept safe" +
                    if (skippedCount > 0) " • $skippedCount not closed" else ""
            } else {
                "Settings screens appear while apps are closed."
            },
            color = KillerColors.TextSecondary,
            fontSize = KillerType.Body,
            textAlign = TextAlign.Center,
        )

        if (done && skippedDetails.isNotEmpty()) {
            TextButton(onClick = { showDetails = true }) { Text("See details") }
        }
        if (done && !overlayAvailable) {
            Text(
                text = "Progress overlay off tha — 'Display over other apps' on karo, warna progress sirf beech mein dikhega.",
                color = KillerColors.TextSecondary,
                fontSize = KillerType.Body,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))

        if (!done) {
            Text(
                text = overlayPositionText(index, total),
                color = KillerColors.TextSecondary,
                fontSize = KillerType.Pill,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }

        SeekLine(progress = if (done) 1f else animated, done = done)

        Spacer(Modifier.weight(1f))

        if (!done) {
            OutlineButton(label = "Cancel", onClick = onCancel)
        } else {
            PrimaryButton(label = "Back to Home", onClick = onBackHome)
            if (undoAvailable) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    label = "Undo (${undoSecondsLeft}s)",
                    onClick = onUndo,
                    enabled = undoSecondsLeft > 0,
                ) {
                    KillerIcons.Draw(KillerIcons.Undo, 18, KillerColors.TextPrimary)
                    Spacer(Modifier.size(8.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ProgressRing(progress: Float, done: Boolean) {
    val ringColor = if (done) KillerColors.Mint else KillerColors.Coral
    Canvas(modifier = Modifier.size(KillerDimens.RingSize)) {
        val stroke = 14.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)

        // Track
        drawArc(
            color = KillerColors.Surface2,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        // Progress — starts at 12 o'clock because of the −90° rotation.
        drawArc(
            color = ringColor,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * Seek line (Slice 7 — replaces the dots): a full-width track with a fill
 * proportional to the ring progress, like a video seek bar. Coral while
 * closing, mint when finished. Same geometry as the overlay's seek line.
 */
@Composable
private fun SeekLine(progress: Float, done: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(KillerColors.Surface2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (done) KillerColors.Mint else KillerColors.Coral),
        )
    }
}
