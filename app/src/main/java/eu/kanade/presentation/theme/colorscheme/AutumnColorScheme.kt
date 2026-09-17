package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object AutumnColorScheme : BaseColorScheme() {
    override val darkScheme = darkColorScheme(
        primary = Color(0xFFD79921),
        onPrimary = Color(0xFF282828),
        primaryContainer = Color(0xFFD79921),
        onPrimaryContainer = Color(0xFF282828),
        inversePrimary = Color(0xFFB57614),
        secondary = Color(0xFFD79921),
        onSecondary = Color(0xFF282828),
        secondaryContainer = Color(0xFFD79921),
        onSecondaryContainer = Color(0xFF282828),
        tertiary = Color(0xFFD79921),
        onTertiary = Color(0xFF282828),
        tertiaryContainer = Color(0xFFD79921),
        onTertiaryContainer = Color(0xFF282828),
        background = Color(0xFF282828),
        onBackground = Color(0xFFE0E0E0),
        surface = Color(0xFF282828),
        onSurface = Color(0xFFE0E0E0),
        surfaceVariant = Color(0xFF1E1E1E),
        onSurfaceVariant = Color(0xFFE0E0E0),
        surfaceTint = Color(0xFFD79921),
        inverseSurface = Color(0xFFE0E0E0),
        inverseOnSurface = Color(0xFF282828),
        outline = Color(0xFF757575),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF121212),
        surfaceContainer = Color(0xFF1E1E1E),
        surfaceContainerHigh = Color(0xFF2C2C2C),
        surfaceContainerHighest = Color(0xFF383838),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFB57614),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB57614),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFFD79921),
        secondary = Color(0xFFB57614),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFB57614),
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = Color(0xFFB57614),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFB57614),
        onTertiaryContainer = Color(0xFFFFFFFF),
        background = Color(0xFFFBF1C7),
        onBackground = Color(0xFF212121),
        surface = Color(0xFFF9F5D7),
        onSurface = Color(0xFF212121),
        surfaceVariant = Color(0xFFEEEEEE),
        onSurfaceVariant = Color(0xFF424242),
        surfaceTint = Color(0xFFB57614),
        inverseSurface = Color(0xFF212121),
        inverseOnSurface = Color(0xFFF9F5D7),
        outline = Color(0xFFBDBDBD),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFAFAFA),
        surfaceContainer = Color(0xFFF5F5F5),
        surfaceContainerHigh = Color(0xFFEEEEEE),
        surfaceContainerHighest = Color(0xFFE0E0E0),
    )
}
