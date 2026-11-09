package dev.killer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Click without the Material ripple: the concept's tap feedback is the 0.98
 * scale, not an ink splash. Keeps "nothing flashes".
 */
fun Modifier.clickableNoRipple(
    enabled: Boolean,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = composed {
    val focusManager = LocalFocusManager.current
    clickable(
        enabled = enabled,
        interactionSource = interaction,
        indication = null,
        onClick = {
            focusManager.clearFocus()
            onClick()
        },
    )
}
