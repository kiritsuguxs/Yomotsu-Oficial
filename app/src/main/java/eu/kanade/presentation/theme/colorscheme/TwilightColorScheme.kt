package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object TwilightColorScheme : BaseColorScheme() {
    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFF79C6),
        onPrimary = Color(0xFF282A36),
        primaryContainer = Color(0xFFFF79C6),
        onPrimaryContainer = Color(0xFF282A36),
        inversePrimary = Color(0xFFBD93F9),
        secondary = Color(0xFFFF79C6),
        onSecondary = Color(0xFF282A36),
        secondaryContainer = Color(0xFFFF79C6),
        onSecondaryContainer = Color(0xFF282A36),
        tertiary = Color(0xFFFF79C6),
        onTertiary = Color(0xFF282A36),
        tertiaryContainer = Color(0xFFFF79C6),
        onTertiaryContainer = Color(0xFF282A36),
        background = Color(0xFF282A36),
        onBackground = Color(0xFFE0E0E0),
        surface = Color(0xFF282A36),
        onSurface = Color(0xFFE0E0E0),
        surfaceVariant = Color(0xFF1E1E1E),
        onSurfaceVariant = Color(0xFFE0E0E0),
        surfaceTint = Color(0xFFFF79C6),
        inverseSurface = Color(0xFFE0E0E0),
        inverseOnSurface = Color(0xFF282A36),
        outline = Color(0xFF757575),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF121212),
        surfaceContainer = Color(0xFF1E1E1E),
        surfaceContainerHigh = Color(0xFF2C2C2C),
        surfaceContainerHighest = Color(0xFF383838),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFBD93F9),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFBD93F9),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFFFF79C6),
        secondary = Color(0xFFBD93F9),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFBD93F9),
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = Color(0xFFBD93F9),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFBD93F9),
        onTertiaryContainer = Color(0xFFFFFFFF),
        background = Color(0xFFF8F8F2),
        onBackground = Color(0xFF212121),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF212121),
        surfaceVariant = Color(0xFFEEEEEE),
        onSurfaceVariant = Color(0xFF424242),
        surfaceTint = Color(0xFFBD93F9),
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
