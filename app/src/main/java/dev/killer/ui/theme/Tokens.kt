package dev.killer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens — the first concept's values, updated by Slice 10 to the
 * RedotPay Depth Version (user order 2026-11-10: look 100% like that design,
 * no compromise). Dark only. Nothing here is a guess: every value exists in
 * that design's rationale.
 */
object KillerColors {
    /** Slice 10: the depth canvas is TRUE black (#000000), not #0A0A0B. "Grey washes out contrast — black makes red glow." */
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF161618)
    val Surface2 = Color(0xFF1E1E20)
    val Border = Color(0xFF2A2A2D)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA8A8AD)
    val TextMuted = Color(0xFF6B6B70)

    /** Slice 10: the depth design's red (#FF2D3A) replaces the first concept's coral (#FF5A5A). Renamed Accent so the name stays honest. Orange (#FF6A00, a small secondary in the design) is deliberately NOT used — one accent only. */
    val Accent = Color(0xFFFF2D3A)
    /** Slice 10: the top ambient glow — a #5A0E18 halo at 80%, fading to transparent by 45% height. */
    val Halo = Color(0xFF5A0E18)
    /** Slice 10: the layered cards' inner top highlight — white at 8%. */
    val CardHighlight = Color(0x14FFFFFF)
    /** Slice 10: the glass pills — frosted white at 10% with a 15% border. */
    val Glass = Color(0x1AFFFFFF)
    val GlassBorder = Color(0x26FFFFFF)
    val Mint = Color(0xFF7BF1C8)
    val Amber = Color(0xFFFFC24B)
}

/** Type scale from the concept: headlines 28–32sp/700 with −0.5 tracking, body 16sp, buttons 17sp bold. */
object KillerType {
    val DisplayNumber = 64.sp
    val ClosingPercent = 48.sp
    val HeadlineLarge = 30.sp
    val Headline = 28.sp
    val Title = 22.sp
    val CardTitle = 19.sp
    val AppName = 17.sp
    val Button = 17.sp
    val Body = 16.sp
    val BodySmall = 15.sp
    val Caption = 14.sp
    val Pill = 13.sp
    val PillSmall = 12.sp

    const val TightTracking = -0.5f
}

/** Shape + spacing tokens. */
object KillerDimens {
    val PagePadding = 20.dp
    val BetweenCards = 16.dp
    val InsideCard = 12.dp

    val CardRadius = 24.dp
    val CardInnerRadius = 20.dp
    val IconRadius = 16.dp
    val ButtonRadius = 28.dp

    val MinTapTarget = 56.dp
    val AppCardHeight = 76.dp
    val PrimaryButtonHeight = 64.dp
    val RingSize = 220.dp
}

/** Motion: 300ms ease-out everywhere, 0.98 tap scale, nothing snaps. */
object KillerMotion {
    const val STANDARD_MS = 300
    const val TapScale = 0.98f

    /** The ring covers one app in ≈600ms; the queue animation is driven per app. */
    const val MS_PER_APP = 600L
}
