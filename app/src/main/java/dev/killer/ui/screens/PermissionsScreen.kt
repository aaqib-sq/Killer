package dev.killer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.killer.ui.components.KillerHeader
import dev.killer.ui.components.DepthHalo
import dev.killer.ui.components.clickableNoRipple
import dev.killer.ui.components.PrimaryButton
import dev.killer.ui.components.tapScale
import dev.killer.ui.icons.KillerIcons
import dev.killer.ui.theme.KillerColors
import dev.killer.ui.theme.KillerDimens
import dev.killer.ui.theme.KillerMotion
import dev.killer.ui.theme.KillerType

/**
 * Screen 1 — Permissions. First run only.
 * The first two cards are the concept's, word for word
 * (killer-ui-concept.html, "Permissions screen"). Slice 3 adds the optional
 * third card ("Keep progress visible") so the closing progress can float
 * above Settings; it never gates Continue.
 */
@Composable
fun PermissionsScreen(
    usageAccess: Boolean,
    accessibility: Boolean,
    overlayPermission: Boolean,
    onEnableUsage: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableOverlay: () -> Unit,
    onContinue: () -> Unit,
) {
    // The overlay permission is optional: Continue only needs the first two.
    val both = usageAccess && accessibility

    // Slice 10: the depth canvas — true black + red halo behind the content.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KillerColors.Background),
    ) {
        DepthHalo()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KillerDimens.PagePadding),
        ) {
        Spacer(Modifier.height(12.dp))
        KillerHeader(trailingPill = "Setup • 1 of 1")

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Three quick permissions. Then you're done.",
            color = KillerColors.TextPrimary,
            fontSize = KillerType.HeadlineLarge,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = KillerType.TightTracking.sp,
            lineHeight = 33.sp,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "We explain everything in plain words. Nothing runs in the background without you saying so.",
            color = KillerColors.TextSecondary,
            fontSize = KillerType.Body,
            lineHeight = 24.sp,
        )

        Spacer(Modifier.height(24.dp))

        PermissionCard(
            icon = { KillerIcons.Draw(KillerIcons.Eye, 24, KillerColors.TextPrimary) },
            title = "See what's running",
            body = "So Killer can list the apps that are open right now. We never read what's inside them.",
            enabled = usageAccess,
            onEnable = onEnableUsage,
        )

        Spacer(Modifier.height(KillerDimens.BetweenCards))

        PermissionCard(
            icon = { KillerIcons.Draw(KillerIcons.Hand, 24, KillerColors.TextPrimary) },
            title = "Close apps for you",
            body = "So one tap can open Settings and force-stop the selected apps. You can turn this off anytime.",
            enabled = accessibility,
            onEnable = onEnableAccessibility,
        )

        Spacer(Modifier.height(KillerDimens.BetweenCards))

        PermissionCard(
            icon = { KillerIcons.Draw(KillerIcons.Layers, 24, KillerColors.TextPrimary) },
            title = "Keep progress visible",
            body = "So the closing progress stays on screen while Settings opens each app. Optional — closing still works without it.",
            enabled = overlayPermission,
            onEnable = onEnableOverlay,
        )

        Spacer(Modifier.height(20.dp))

        PrimaryButton(
            label = "Continue",
            enabled = both,
            onClick = onContinue,
            height = 60,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "No account. No ads. No data leaves your phone.",
            color = KillerColors.TextMuted,
            fontSize = KillerType.Pill,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    enabled: Boolean,
    onEnable: () -> Unit,
) {
    val buttonBackground by animateColorAsState(
        targetValue = if (enabled) KillerColors.Mint else KillerColors.Accent,
        animationSpec = tween(KillerMotion.STANDARD_MS),
        label = "permissionButton",
    )
    val textColor by animateColorAsState(
        targetValue = if (enabled) KillerColors.Background else KillerColors.TextPrimary,
        animationSpec = tween(KillerMotion.STANDARD_MS),
        label = "permissionText",
    )
    val (scaleModifier, interaction) = tapScale()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KillerDimens.CardRadius))
            .background(KillerColors.Surface)
            .padding(KillerDimens.InsideCard),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(KillerDimens.IconRadius))
                .background(KillerColors.Surface2),
            contentAlignment = Alignment.Center,
        ) { icon() }

        Spacer(Modifier.height(12.dp))

        Text(
            text = title,
            color = KillerColors.TextPrimary,
            fontSize = KillerType.CardTitle,
            fontWeight = FontWeight.ExtraBold,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = body,
            color = KillerColors.TextSecondary,
            fontSize = KillerType.BodySmall,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(KillerDimens.MinTapTarget)
                .then(scaleModifier)
                .clip(RoundedCornerShape(KillerDimens.CardInnerRadius))
                .background(buttonBackground)
                .clickableNoRipple(enabled = !enabled, interaction = interaction, onClick = onEnable),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (enabled) "Enabled ✓" else "Enable",
                color = textColor,
                fontSize = KillerType.Button,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
