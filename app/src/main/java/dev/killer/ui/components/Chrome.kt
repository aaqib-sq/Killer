package dev.killer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.killer.R
import dev.killer.ui.theme.KillerColors
import dev.killer.ui.theme.KillerDimens
import dev.killer.ui.theme.KillerMotion
import dev.killer.ui.theme.KillerType

/**
 * The app mark. Drawn from the supplied PNG exactly as delivered —
 * no redraw, no recolour, no vector re-creation.
 */
@Composable
fun KillerMark(size: Int, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.killer_mark),
        contentDescription = "Killer",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(percent = 28)),
    )
}

/** "Killer." — the wordmark always carries the coral full stop. Never all caps. */
@Composable
fun Wordmark(fontSize: Int = 20) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = KillerColors.TextPrimary)) { append("Killer") }
            withStyle(SpanStyle(color = KillerColors.Coral)) { append(".") }
        },
        fontSize = fontSize.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = KillerType.TightTracking.sp,
    )
}

/** Small pill used for "Setup", "{n} running", "Quiet Mode". */
@Composable
fun Pill(
    text: String,
    background: Color = KillerColors.Surface2,
    foreground: Color = KillerColors.TextSecondary,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = KillerType.Pill,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Header: logo + wordmark + trailing pill. */
@Composable
fun KillerHeader(trailingPill: String, markSize: Int = 34, wordmarkSize: Int = 20) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KillerMark(size = markSize)
            Box(Modifier.padding(start = 8.dp)) { Wordmark(fontSize = wordmarkSize) }
        }
        Pill(text = trailingPill)
    }
}

/**
 * Every tappable thing is at least 56dp tall and settles at 0.98 on press —
 * the concept's `.tap:active { transform: scale(.98) }` plus the brief's
 * "every tappable thing ≥ 56dp" rule.
 */
@Composable
fun tapScale(): Pair<Modifier, MutableInteractionSource> {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) KillerMotion.TapScale else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "tapScale",
    )
    return Modifier.scale(scale) to interaction
}

/** Big coral primary action — 64dp tall, 28dp pill, 17sp bold. */
@Composable
fun PrimaryButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 64,
) {
    val (scaleModifier, interaction) = tapScale()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .then(scaleModifier)
            .clip(RoundedCornerShape(KillerDimens.ButtonRadius))
            .background(if (enabled) KillerColors.Coral else KillerColors.Surface2)
            .clickableNoRipple(enabled, interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) KillerColors.TextPrimary else KillerColors.TextMuted,
            fontSize = KillerType.Button,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Outline action — "Cancel" on Closing, 28dp pill, 2dp border. */
@Composable
fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 60,
) {
    val (scaleModifier, interaction) = tapScale()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .then(scaleModifier)
            .clip(RoundedCornerShape(KillerDimens.ButtonRadius))
            .border(2.dp, KillerColors.Border, RoundedCornerShape(KillerDimens.ButtonRadius))
            .clickableNoRipple(true, interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = KillerColors.TextPrimary,
            fontSize = KillerType.Button,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Surface-2 secondary action ("Back to Home", "Undo (5s)"). */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Int = 60,
    content: @Composable (() -> Unit)? = null,
) {
    val (scaleModifier, interaction) = tapScale()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .then(scaleModifier)
            .clip(RoundedCornerShape(KillerDimens.ButtonRadius))
            .background(KillerColors.Surface2)
            .clickableNoRipple(enabled, interaction, onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content?.invoke()
        Text(
            text = label,
            color = if (enabled) KillerColors.TextPrimary else KillerColors.TextMuted,
            fontSize = KillerType.Button,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A card: 24dp radius, Surface fill, 1px border, 12dp+ inside. */
@Composable
fun KillerCard(
    modifier: Modifier = Modifier,
    background: Color = KillerColors.Surface,
    borderColor: Color = KillerColors.Border,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KillerDimens.CardRadius))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(KillerDimens.CardRadius))
            .padding(KillerDimens.InsideCard)
            .defaultMinSize(minHeight = KillerDimens.MinTapTarget),
        content = content,
    )
}
