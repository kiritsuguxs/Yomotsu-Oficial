import re

with open("app/src/main/java/eu/kanade/tachiyomi/data/telegram/TelegramCloudManager.kt", "r") as f:
    content = f.read()

target = 'val tempFile = File(cacheDir, "${System.currentTimeMillis()}_${DiskUtil.buildValidFilename(chapter.name)}.cbz")'
replacement = '''val type = getCloudIndex().find { it.title == mangaTitle }?.type ?: "MANGA"
            val ext = if (type == "ANIME") ".mkv" else if (type == "NOVEL") ".txt" else ".cbz"
            val tempFile = File(cacheDir, "${System.currentTimeMillis()}_${DiskUtil.buildValidFilename(chapter.name)}$ext")'''

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/eu/kanade/tachiyomi/data/telegram/TelegramCloudManager.kt", "w") as f:
        f.write(content)
    print("Success")
else:
    print("Target not found")
