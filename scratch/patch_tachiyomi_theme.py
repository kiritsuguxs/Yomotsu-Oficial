import re

file_path = "/workspace/Yomotsu-Oficial/app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt"
with open(file_path, "r") as f:
    content = f.read()

imports = """import eu.kanade.presentation.theme.colorscheme.YotsubaColorScheme
import eu.kanade.presentation.theme.colorscheme.OnyxColorScheme
import eu.kanade.presentation.theme.colorscheme.NeonColorScheme
import eu.kanade.presentation.theme.colorscheme.TwilightColorScheme
import eu.kanade.presentation.theme.colorscheme.OrchidColorScheme
import eu.kanade.presentation.theme.colorscheme.AutumnColorScheme"""

content = content.replace("import eu.kanade.presentation.theme.colorscheme.YotsubaColorScheme", imports)

map_entries = """    AppTheme.YOTSUBA to YotsubaColorScheme,
    AppTheme.ONYX to OnyxColorScheme,
    AppTheme.NEON to NeonColorScheme,
    AppTheme.TWILIGHT to TwilightColorScheme,
    AppTheme.ORCHID to OrchidColorScheme,
    AppTheme.AUTUMN to AutumnColorScheme,"""

content = content.replace("    AppTheme.YOTSUBA to YotsubaColorScheme,", map_entries)

with open(file_path, "w") as f:
    f.write(content)

print("Patched TachiyomiTheme.kt!")
