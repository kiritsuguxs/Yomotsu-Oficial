package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object OrchidColorScheme : BaseColorScheme() {
    override val darkScheme = darkColorScheme(
        primary = Color(0xFFCBA6F7),
        onPrimary = Color(0xFF1E1E2E),
        primaryContainer = Color(0xFFCBA6F7),
        onPrimaryContainer = Color(0xFF1E1E2E),
        inversePrimary = Color(0xFF8839EF),
        secondary = Color(0xFFCBA6F7),
        onSecondary = Color(0xFF1E1E2E),
        secondaryContainer = Color(0xFFCBA6F7),
        onSecondaryContainer = Color(0xFF1E1E2E),
        tertiary = Color(0xFFCBA6F7),
        onTertiary = Color(0xFF1E1E2E),
        tertiaryContainer = Color(0xFFCBA6F7),
        onTertiaryContainer = Color(0xFF1E1E2E),
        background = Color(0xFF1E1E2E),
        onBackground = Color(0xFFE0E0E0),
        surface = Color(0xFF1E1E2E),
        onSurface = Color(0xFFE0E0E0),
        surfaceVariant = Color(0xFF1E1E1E),
        onSurfaceVariant = Color(0xFFE0E0E0),
        surfaceTint = Color(0xFFCBA6F7),
        inverseSurface = Color(0xFFE0E0E0),
        inverseOnSurface = Color(0xFF1E1E2E),
        outline = Color(0xFF757575),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF121212),
        surfaceContainer = Color(0xFF1E1E1E),
        surfaceContainerHigh = Color(0xFF2C2C2C),
        surfaceContainerHighest = Color(0xFF383838),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF8839EF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF8839EF),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFFCBA6F7),
        secondary = Color(0xFF8839EF),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF8839EF),
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = Color(0xFF8839EF),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF8839EF),
        onTertiaryContainer = Color(0xFFFFFFFF),
        background = Color(0xFFEFF1F5),
        onBackground = Color(0xFF212121),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF212121),
        surfaceVariant = Color(0xFFEEEEEE),
        onSurfaceVariant = Color(0xFF424242),
        surfaceTint = Color(0xFF8839EF),
        inverseSurface = Color(0xFF212121),
        inverseOnSurface = Color(0xFFFFFFFF),
        outline = Color(0xFFBDBDBD),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFAFAFA),
        surfaceContainer = Color(0xFFF5F5F5),
        surfaceContainerHigh = Color(0xFFEEEEEE),
        surfaceContainerHighest = Color(0xFFE0E0E0),
    )
}
