package dev.killer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.killer.engine.Intention
import dev.killer.ui.HomeApp
import dev.killer.ui.components.KillerMark
import dev.killer.ui.components.Pill
import dev.killer.ui.components.Wordmark
import dev.killer.ui.components.clickableNoRipple
import dev.killer.ui.components.tapScale
import dev.killer.ui.icons.KillerIcons
import dev.killer.ui.theme.KillerColors
import dev.killer.ui.theme.KillerDimens
import dev.killer.ui.theme.KillerMotion
import dev.killer.ui.theme.KillerType

/**
 * Screen 4 — Quiet Mode, the alternative Home.
 * Copy from the concept: "What do you want quiet for?", "{n}" + "apps making noise",
 * "Make it quiet", the three intentions, and the Kept Safe disclosure.
 */
@Composable
fun QuietModeScreen(
    apps: List<HomeApp>,
    closableCount: Int,
    keptCount: Int,
    keptRows: List<HomeApp>,
    intention: Intention,
    keptSafeExpanded: Boolean,
    onSelectIntention: (Intention) -> Unit,
    onToggleKeptSafe: () -> Unit,
    onUnkeep: (String) -> Unit,
    onMakeItQuiet: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KillerColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KillerDimens.PagePadding),
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KillerMark(size = 32)
            Spacer(Modifier.size(8.dp))
            Wordmark(fontSize = 19)
            Spacer(Modifier.weight(1f))
            val (backScale, backInteraction) = tapScale()
            Row(
                modifier = Modifier
                    .then(backScale)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(KillerColors.Surface2)
                    .clickableNoRipple(true, backInteraction, onBack)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KillerIcons.Draw(KillerIcons.Moon, 13, KillerColors.TextSecondary)
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "Quiet Mode",
                    color = KillerColors.TextSecondary,
                    fontSize = KillerType.Pill,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "What do you want quiet for?",
            color = KillerColors.TextPrimary,
            fontSize = KillerType.HeadlineLarge,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = KillerType.TightTracking.sp,
            lineHeight = 33.sp,
        )

        Spacer(Modifier.height(20.dp))

        // Hero card: the real count, then the one button.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(KillerDimens.CardRadius))
                .background(KillerColors.Surface)
                .padding(20.dp),
        ) {
            Text(
                text = "${apps.size}",
                color = KillerColors.TextPrimary,
                fontSize = KillerType.DisplayNumber,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 64.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "apps making noise",
                color = KillerColors.TextSecondary,
                fontSize = KillerType.Body,
            )

            Spacer(Modifier.height(20.dp))

            val (scale, interaction) = tapScale()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .then(scale)
                    .clip(RoundedCornerShape(KillerDimens.ButtonRadius))
                    .background(if (closableCount > 0) KillerColors.Coral else KillerColors.Surface2)
                    .clickableNoRipple(closableCount > 0, interaction, onMakeItQuiet),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Make it quiet",
                    color = if (closableCount > 0) KillerColors.TextPrimary else KillerColors.TextMuted,
                    fontSize = KillerType.Button,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Closes $closableCount • Keeps $keptCount safe",
                color = KillerColors.TextMuted,
                fontSize = KillerType.Pill,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        Spacer(Modifier.height(16.dp))

        Intention.entries.forEach { option ->
            IntentionCard(
                intention = option,
                selected = option == intention,
                onClick = { onSelectIntention(option) },
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(4.dp))

        KeptSafeCard(
            keptRows = keptRows,
            keptCount = keptCount,
            totalCount = apps.size,
            expanded = keptSafeExpanded,
            onToggle = onToggleKeptSafe,
            onUnkeep = onUnkeep,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun IntentionCard(intention: Intention, selected: Boolean, onClick: () -> Unit) {
    val (scale, interaction) = tapScale()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(scale)
            .clip(RoundedCornerShape(KillerDimens.CardRadius))
            .background(if (selected) KillerColors.Coral.copy(alpha = 0.10f) else KillerColors.Surface)
            .border(
                1.dp,
                if (selected) KillerColors.Coral else KillerColors.Border,
                RoundedCornerShape(KillerDimens.CardRadius),
            )
            .clickableNoRipple(true, interaction, onClick)
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = intention.emoji, fontSize = 22.sp)
            Spacer(Modifier.weight(1f))
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(KillerColors.Coral),
                    contentAlignment = Alignment.Center,
                ) {
                    KillerIcons.Draw(KillerIcons.Check, 14, KillerColors.TextPrimary)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = intention.title,
            color = KillerColors.TextPrimary,
            fontSize = KillerType.AppName,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = intention.description,
            color = KillerColors.TextSecondary,
            fontSize = KillerType.Caption,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = intention.keepLabel,
            color = KillerColors.Amber,
            fontSize = KillerType.Pill,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun KeptSafeCard(
    keptRows: List<HomeApp>,
    keptCount: Int,
    totalCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUnkeep: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KillerDimens.CardRadius))
            .background(KillerColors.Surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(KillerDimens.MinTapTarget)
                .clickableNoRipple(true, androidx.compose.foundation.interaction.MutableInteractionSource(), onToggle)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KillerIcons.Draw(KillerIcons.ShieldCheck, 19, KillerColors.Mint)
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Kept safe",
                color = KillerColors.TextPrimary,
                fontSize = KillerType.Body,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.size(8.dp))
            Pill(text = "$keptCount", background = KillerColors.Surface2, foreground = KillerColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            KillerIcons.Draw(
                KillerIcons.ChevronDown,
                18,
                KillerColors.TextSecondary,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(KillerMotion.STANDARD_MS)) + expandVertically(tween(KillerMotion.STANDARD_MS)),
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Text(
                    text = "These stay open because they matter right now.",
                    color = KillerColors.TextSecondary,
                    fontSize = KillerType.Pill,
                )

                Spacer(Modifier.height(12.dp))

                if (keptRows.isEmpty()) {
                    Text(
                        text = "Nothing needs protecting right now.",
                        color = KillerColors.TextSecondary,
                        fontSize = KillerType.Caption,
                    )
                } else {
                    keptRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = row.label,
                                color = KillerColors.TextPrimary,
                                fontSize = KillerType.BodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.weight(1f))
                            KillerIcons.Draw(KillerIcons.Lock, 14, KillerColors.Mint)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Concept wording, verbatim: "See all {n} apps →".
                Text(
                    text = "See all $totalCount apps →",
                    color = KillerColors.TextSecondary,
                    fontSize = KillerType.Pill,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
