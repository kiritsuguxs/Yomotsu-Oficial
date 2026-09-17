import re

file_path = "/workspace/Yomotsu-Oficial/app/src/main/java/eu/kanade/tachiyomi/ui/base/delegate/ThemingDelegate.kt"
with open(file_path, "r") as f:
    content = f.read()

entries = """    AppTheme.YOTSUBA to R.style.Theme_Tachiyomi_Yotsuba,
    AppTheme.ONYX to R.style.Theme_Tachiyomi,
    AppTheme.NEON to R.style.Theme_Tachiyomi,
    AppTheme.TWILIGHT to R.style.Theme_Tachiyomi,
    AppTheme.ORCHID to R.style.Theme_Tachiyomi,
    AppTheme.AUTUMN to R.style.Theme_Tachiyomi,"""

content = content.replace("    AppTheme.YOTSUBA to R.style.Theme_Tachiyomi_Yotsuba,", entries)

with open(file_path, "w") as f:
    f.write(content)

print("Patched ThemingDelegate.kt!")
