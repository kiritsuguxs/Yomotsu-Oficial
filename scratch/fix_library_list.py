import re

file_path = "/workspace/Yomotsu-Oficial/app/src/main/java/eu/kanade/presentation/library/components/LibraryList.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace(
    "isLocal = libraryItem.badges.isLocal,\n                        sourceLanguage = libraryItem.badges.sourceLanguage,\n                    )",
    "isLocal = libraryItem.badges.isLocal,\n                        sourceLanguage = libraryItem.badges.sourceLanguage,\n                        sourceId = manga.source,\n                    )"
)

with open(file_path, "w") as f:
    f.write(content)

print("Fixed LibraryList.kt!")
