import re

with open("app/src/main/java/eu/kanade/tachiyomi/App.kt", "r") as f:
    content = f.read()

content = content.replace("import eu.kanade.tachiyomi.data.coil.MangaKeyer", "import eu.kanade.tachiyomi.data.coil.MangaKeyer\nimport eu.kanade.tachiyomi.data.coil.AnimeKeyer")

content = content.replace("add(MangaKeyer())", "add(MangaKeyer())\n                add(AnimeKeyer())")

with open("app/src/main/java/eu/kanade/tachiyomi/App.kt", "w") as f:
    f.write(content)
print("Patched App.kt again")
