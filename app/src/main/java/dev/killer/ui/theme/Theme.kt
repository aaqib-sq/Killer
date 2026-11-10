package dev.killer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Dark-only theme. The concept asks for `ui-rounded, "SF Pro Rounded", system-ui`:
 * FontFamily.SansSerif resolves to Roboto on stock Android and to the OEM's own
 * rounded system face where one exists (Android 15+ / Pixel), so the design is
 * honoured without shipping a font the concept does not name.
 */
@Composable
fun KillerTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = KillerColors.Accent,
        onPrimary = KillerColors.TextPrimary,
        secondary = KillerColors.Mint,
        onSecondary = KillerColors.Background,
        tertiary = KillerColors.Amber,
        background = KillerColors.Background,
        onBackground = KillerColors.TextPrimary,
        surface = KillerColors.Surface,
        onSurface = KillerColors.TextPrimary,
        surfaceVariant = KillerColors.Surface2,
        onSurfaceVariant = KillerColors.TextSecondary,
        outline = KillerColors.Border,
        error = KillerColors.Accent,
    )

    val typography = Typography(
        displayLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = KillerType.DisplayNumber,
            letterSpacing = KillerType.TightTracking.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = KillerType.HeadlineLarge,
            lineHeight = 33.sp,
            letterSpacing = KillerType.TightTracking.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = KillerType.Headline,
            lineHeight = 31.sp,
            letterSpacing = KillerType.TightTracking.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = KillerType.Title,
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = KillerType.CardTitle,
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = KillerType.Body,
            lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = KillerType.BodySmall,
            lineHeight = 22.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = KillerType.Button,
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = KillerType.Pill,
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = KillerType.PillSmall,
        ),
    )

    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
