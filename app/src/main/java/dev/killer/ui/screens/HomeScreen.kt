package dev.killer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.killer.ui.HomeApp
import dev.killer.ui.AppTab
import dev.killer.ui.components.KillerCard
import dev.killer.ui.components.KillerHeader
import dev.killer.ui.components.Pill
import dev.killer.ui.components.PrimaryButton
import dev.killer.ui.components.clickableNoRipple
import dev.killer.ui.components.tapScale
import dev.killer.ui.icons.KillerIcons
import dev.killer.ui.theme.KillerColors
import dev.killer.ui.theme.KillerDimens
import dev.killer.ui.theme.KillerMotion
import dev.killer.ui.theme.KillerType

/**
 * Screen 2 — Home. The list.
 * Copy from the concept: "{n} apps running", "Tap one to close it. Or close them all at once.",
 * sticky "Close All • {n}", empty state "All quiet."
 */
@Composable
fun HomeScreen(
    apps: List<HomeApp>,
    query: String,
    onQuery: (String) -> Unit,
    selectedOther: Set<String>,
    onToggleOther: (String) -> Unit,
    onSelectOther: () -> Unit,
    onClearOther: () -> Unit,
    onCloseSelected: () -> Unit,
    onRefresh: () -> Unit,
    onDiagnostics: () -> Unit,
    runningCount: Int,
    closableCount: Int,
    loading: Boolean,
    tab: AppTab,
    onTab: (AppTab) -> Unit,
    onCloseOne: (HomeApp) -> Unit,
    onCloseAll: () -> Unit,
    onQuietMode: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(KillerColors.Background)) {

        Column(modifier = Modifier.padding(horizontal = KillerDimens.PagePadding)) {
            Spacer(Modifier.height(3.dp))
            // Header keeps the concept's exact chrome: logo + wordmark + "{n} running".
            KillerHeader(trailingPill = "$runningCount running", markSize = 32, wordmarkSize = 19)

            Spacer(Modifier.height(24.dp))

            Text(
                text = if (tab == AppTab.Running) "$runningCount apps running" else if (tab == AppTab.Other) "Other apps" else "Core apps",
                color = KillerColors.TextPrimary,
                fontSize = KillerType.HeadlineLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = KillerType.TightTracking.sp,
                lineHeight = 32.sp,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Tap one to close it. Or close them all at once.",
                color = KillerColors.TextSecondary,
                fontSize = KillerType.Body,
            )

            Spacer(Modifier.height(12.dp))

            // Quiet Mode is the concept's alternative Home; this is its front door.
            val (quietScale, quietInteraction) = tapScale()
            Row(
                modifier = Modifier
                    .height(KillerDimens.MinTapTarget)
                    .then(quietScale)
                    .clip(RoundedCornerShape(KillerDimens.ButtonRadius))
                    .background(KillerColors.Surface2)
                    .clickableNoRipple(true, quietInteraction, onQuietMode)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KillerIcons.Draw(KillerIcons.Moon, 15, KillerColors.TextSecondary)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Quiet Mode",
                    color = KillerColors.TextPrimary,
                    fontSize = KillerType.Pill,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTab.entries.forEach { option ->
                    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    Text(
                        text = option.name,
                        color = if (tab == option) KillerColors.TextPrimary else KillerColors.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (tab == option) KillerColors.Coral else KillerColors.Surface2)
                            .clickableNoRipple(true, interaction) { onTab(option) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = query, onValueChange = onQuery, singleLine = true,
                label = { Text("Search apps or packages") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tab == AppTab.Other) {
                    TextButton(onClick = onSelectOther) { Text("Select all") }
                    TextButton(onClick = onClearOther) { Text("Clear") }
                }
                TextButton(onClick = onRefresh) { Text("Refresh") }
                TextButton(onClick = onDiagnostics) { Text("Diagnostics") }
            }
        }

        // The list settles gently: cards fade + slide out, never just vanish.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (loading) {
                Text("Loading apps…", color = KillerColors.TextSecondary, modifier = Modifier.padding(24.dp))
            } else if (apps.isEmpty() && tab == AppTab.Running && query.isBlank()) {
                EmptyState(onQuietMode = onQuietMode)
            } else if (apps.isEmpty()) {
                Text("No apps found", color = KillerColors.TextSecondary, modifier = Modifier.padding(24.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = KillerDimens.PagePadding),
                    verticalArrangement = Arrangement.spacedBy(KillerDimens.BetweenCards),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        // Explicit receiver: LazyItemScope is not a ColumnScope.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(KillerMotion.STANDARD_MS)) +
                                slideInVertically(tween(KillerMotion.STANDARD_MS)) { it / 6 },
                            exit = fadeOut(tween(KillerMotion.STANDARD_MS)) +
                                shrinkVertically(tween(KillerMotion.STANDARD_MS)),
                        ) {
                            AppCard(app = app, onClose = { onCloseOne(app) },
                                selected = tab == AppTab.Other && app.packageName in selectedOther,
                                onSelect = if (tab == AppTab.Other && !app.isKept) ({ onToggleOther(app.packageName) }) else null)
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }

        // Sticky, never scrolls away.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(KillerColors.Background)
                .padding(horizontal = KillerDimens.PagePadding, vertical = 12.dp),
        ) {
            PrimaryButton(
                label = if (tab == AppTab.Other) "Close selected • ${selectedOther.size}" else "Close All • $closableCount",
                enabled = !loading && ((tab == AppTab.Running && closableCount > 0) || (tab == AppTab.Other && selectedOther.isNotEmpty())),
                onClick = if (tab == AppTab.Other) onCloseSelected else onCloseAll,
                height = KillerDimens.PrimaryButtonHeight.value.toInt(),
            )
        }
    }
}

@Composable
private fun AppCard(app: HomeApp, onClose: () -> Unit, selected: Boolean, onSelect: (() -> Unit)?) {
    val (cardScale, cardInteraction) = tapScale()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(KillerDimens.AppCardHeight)
            .then(cardScale)
            .clip(RoundedCornerShape(KillerDimens.CardRadius))
            .background(KillerColors.Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconTile(app)

        Spacer(Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = KillerColors.TextPrimary,
                fontSize = KillerType.AppName,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.detail(),
                color = KillerColors.TextSecondary,
                fontSize = KillerType.Pill,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onSelect != null) {
            androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onSelect() })
        }
        // One big X in a soft circle. No swipe, no long-press, no checkbox.
        val (xScale, xInteraction) = tapScale()
        Box(
            modifier = Modifier
                .size(44.dp)
                .then(xScale)
                .clip(RoundedCornerShape(percent = 50))
                .background(KillerColors.Surface2)
                .clickableNoRipple(enabled = !app.isKept, interaction = xInteraction, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            KillerIcons.Draw(if (app.isKept) KillerIcons.Lock else KillerIcons.X, 20, KillerColors.TextSecondary)
        }
    }
}

/** The real app icon when the system will give it to us, a stable monogram otherwise. */
@Composable
private fun AppIconTile(app: HomeApp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val drawable = androidx.compose.runtime.remember(app.packageName) {
        runCatching { context.packageManager.getApplicationIcon(app.packageName) }.getOrNull()
    }
    val color = androidx.compose.runtime.remember(app.packageName) {
        // Deterministic, calm tile colour when no icon is available.
        val palette = listOf(0xFF1E1E20, 0xFF232326, 0xFF1A1A1D)
        androidx.compose.ui.graphics.Color(palette[app.packageName.hashCode().mod(palette.size)])
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(KillerDimens.IconRadius))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (drawable != null) {
            androidx.compose.foundation.Image(
                bitmap = drawable.toBitmap(104, 104).asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.size(44.dp),
            )
        } else {
            Text(
                text = app.label.take(1).uppercase(),
                color = KillerColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun EmptyState(onQuietMode: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = KillerDimens.PagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KillerCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(KillerColors.Mint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    KillerIcons.Draw(KillerIcons.Check, 32, KillerColors.Mint)
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "All quiet.",
                    color = KillerColors.TextPrimary,
                    fontSize = KillerType.Title,
                    fontWeight = FontWeight.ExtraBold,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Nothing is running in the background.",
                    color = KillerColors.TextSecondary,
                    fontSize = KillerType.BodySmall,
                )

                Spacer(Modifier.height(16.dp))

                // The way back to the other half of the product.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(KillerDimens.MinTapTarget)
                        .clip(RoundedCornerShape(KillerDimens.CardInnerRadius))
                        .background(KillerColors.Surface2)
                        .clickableNoRipple(true, androidx.compose.foundation.interaction.MutableInteractionSource(), onQuietMode),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KillerIcons.Draw(KillerIcons.Moon, 16, KillerColors.TextSecondary)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Quiet Mode",
                        color = KillerColors.TextPrimary,
                        fontSize = KillerType.BodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
