import os

themes = [
    {
        "id": "onyx",
        "name": "OnyxColorScheme",
        "enum": "ONYX",
        "primary_dark": "0xFFAAAAAA",
        "on_primary_dark": "0xFF000000",
        "bg_dark": "0xFF000000",
        "surface_dark": "0xFF000000",
        "primary_light": "0xFF444444",
        "bg_light": "0xFFF5F5F5",
        "surface_light": "0xFFFFFFFF",
    },
    {
        "id": "neon",
        "name": "NeonColorScheme",
        "enum": "NEON",
        "primary_dark": "0xFF00FFFF",
        "on_primary_dark": "0xFF000000",
        "bg_dark": "0xFF0A0A0A",
        "surface_dark": "0xFF121212",
        "primary_light": "0xFF00BFFF",
        "bg_light": "0xFFFAFAFA",
        "surface_light": "0xFFFFFFFF",
    },
    {
        "id": "twilight",
        "name": "TwilightColorScheme",
        "enum": "TWILIGHT",
        "primary_dark": "0xFFFF79C6",
        "on_primary_dark": "0xFF282A36",
        "bg_dark": "0xFF282A36",
        "surface_dark": "0xFF282A36",
        "primary_light": "0xFFBD93F9",
        "bg_light": "0xFFF8F8F2",
        "surface_light": "0xFFFFFFFF",
    },
    {
        "id": "orchid",
        "name": "OrchidColorScheme",
        "enum": "ORCHID",
        "primary_dark": "0xFFCBA6F7",
        "on_primary_dark": "0xFF1E1E2E",
        "bg_dark": "0xFF1E1E2E",
        "surface_dark": "0xFF1E1E2E",
        "primary_light": "0xFF8839EF",
        "bg_light": "0xFFEFF1F5",
        "surface_light": "0xFFFFFFFF",
    },
    {
        "id": "autumn",
        "name": "AutumnColorScheme",
        "enum": "AUTUMN",
        "primary_dark": "0xFFD79921",
        "on_primary_dark": "0xFF282828",
        "bg_dark": "0xFF282828",
        "surface_dark": "0xFF282828",
        "primary_light": "0xFFB57614",
        "bg_light": "0xFFFBF1C7",
        "surface_light": "0xFFF9F5D7",
    }
]

kt_template = """package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object {name} : BaseColorScheme() {{
    override val darkScheme = darkColorScheme(
        primary = Color({primary_dark}),
        onPrimary = Color({on_primary_dark}),
        primaryContainer = Color({primary_dark}),
        onPrimaryContainer = Color({on_primary_dark}),
        inversePrimary = Color({primary_light}),
        secondary = Color({primary_dark}),
        onSecondary = Color({on_primary_dark}),
        secondaryContainer = Color({primary_dark}),
        onSecondaryContainer = Color({on_primary_dark}),
        tertiary = Color({primary_dark}),
        onTertiary = Color({on_primary_dark}),
        tertiaryContainer = Color({primary_dark}),
        onTertiaryContainer = Color({on_primary_dark}),
        background = Color({bg_dark}),
        onBackground = Color(0xFFE0E0E0),
        surface = Color({surface_dark}),
        onSurface = Color(0xFFE0E0E0),
        surfaceVariant = Color(0xFF1E1E1E),
        onSurfaceVariant = Color(0xFFE0E0E0),
        surfaceTint = Color({primary_dark}),
        inverseSurface = Color(0xFFE0E0E0),
        inverseOnSurface = Color({surface_dark}),
        outline = Color(0xFF757575),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF121212),
        surfaceContainer = Color(0xFF1E1E1E),
        surfaceContainerHigh = Color(0xFF2C2C2C),
        surfaceContainerHighest = Color(0xFF383838),
    )

    override val lightScheme = lightColorScheme(
        primary = Color({primary_light}),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color({primary_light}),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color({primary_dark}),
        secondary = Color({primary_light}),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color({primary_light}),
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = Color({primary_light}),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color({primary_light}),
        onTertiaryContainer = Color(0xFFFFFFFF),
        background = Color({bg_light}),
        onBackground = Color(0xFF212121),
        surface = Color({surface_light}),
        onSurface = Color(0xFF212121),
        surfaceVariant = Color(0xFFEEEEEE),
        onSurfaceVariant = Color(0xFF424242),
        surfaceTint = Color({primary_light}),
        inverseSurface = Color(0xFF212121),
        inverseOnSurface = Color({surface_light}),
        outline = Color(0xFFBDBDBD),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFAFAFA),
        surfaceContainer = Color(0xFFF5F5F5),
        surfaceContainerHigh = Color(0xFFEEEEEE),
        surfaceContainerHighest = Color(0xFFE0E0E0),
    )
}}
"""

for t in themes:
    kt_code = kt_template.format(**t)
    with open(f"/workspace/Yomotsu-Oficial/app/src/main/java/eu/kanade/presentation/theme/colorscheme/{t['name']}.kt", "w") as f:
        f.write(kt_code)

print("Generated Compose Color Schemes!")
