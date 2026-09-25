package eu.kanade.presentation.theme

import androidx.compose.material3.RippleConfiguration
import androidx.compose.ui.graphics.Color

/**
 * Custom ripple configuration for the anime player overlay controls.
 * Uses a semi-transparent white ripple that's visible on dark video backgrounds.
 */
val playerRippleConfiguration = RippleConfiguration(
    color = Color.White,
)
