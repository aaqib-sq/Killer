package dev.killer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import dev.killer.ui.theme.KillerColors
import dev.killer.ui.theme.KillerDimens
import dev.killer.ui.theme.KillerMotion
import dev.killer.ui.theme.KillerType

/**
 * Screen 3 — Closing. A full-screen moment, never a toast.
 * 220dp ring, 48sp percentage, the app being closed right now, dots for the queue,
 * Cancel while running; Done state with mint ring + check + Undo for 5 seconds.
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
    undoAvailable: Boolean,
    undoSecondsLeft: Int,
    onCancel: () -> Unit,
    onBackHome: () -> Unit,
    onUndo: () -> Unit,
) {
    val done = phase == ClosingPhase.Done

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
            text = if (done) "Done. All quiet." else "Closing $currentLabel…",
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
                if (keptSafeCount > 0) "$closedCount apps closed • $keptSafeCount kept safe"
                else "$closedCount apps closed"
            } else {
                "Staying on this screen until it's finished."
            },
            color = KillerColors.TextSecondary,
            fontSize = KillerType.Body,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        ProgressDots(index = index, total = total, done = done)

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

/** Small dots through the queue: amber-free, coral while closing, mint when finished. */
@Composable
private fun ProgressDots(index: Int, total: Int, done: Boolean) {
    if (total <= 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total.coerceAtMost(12)) { i ->
            val finished = done || i < index - 1
            val current = !done && i == index - 1
            Box(
                modifier = Modifier
                    .size(width = if (finished || current) 24.dp else 10.dp, height = 10.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        when {
                            finished -> KillerColors.Mint
                            current -> KillerColors.Coral
                            else -> KillerColors.Border
                        },
                    ),
            )
        }
    }
}
