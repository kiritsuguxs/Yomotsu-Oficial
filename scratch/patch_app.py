import re

with open("app/src/main/java/eu/kanade/tachiyomi/App.kt", "r") as f:
    content = f.read()

content = content.replace("import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher", "import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher\nimport eu.kanade.tachiyomi.data.coil.AnimeImageFetcher")
content = content.replace("import eu.kanade.tachiyomi.data.coil.MangaCoverKeyer", "import eu.kanade.tachiyomi.data.coil.MangaCoverKeyer\nimport eu.kanade.tachiyomi.data.coil.AnimeCoverKeyer")

components_old = """                add(MangaCoverFetcher.MangaCoverFactory(callFactoryLazy))
                add(MangaCoverFetcher.MangaFactory(callFactoryLazy))
                add(MangaCoverKeyer())"""

components_new = """                add(MangaCoverFetcher.MangaCoverFactory(callFactoryLazy))
                add(MangaCoverFetcher.MangaFactory(callFactoryLazy))
                add(AnimeImageFetcher.AnimeCoverFactory(callFactoryLazy))
                add(AnimeImageFetcher.AnimeFactory(callFactoryLazy))
                add(MangaCoverKeyer())
                add(AnimeCoverKeyer())"""

content = content.replace(components_old, components_new)

with open("app/src/main/java/eu/kanade/tachiyomi/App.kt", "w") as f:
    f.write(content)
print("Patched App.kt")
