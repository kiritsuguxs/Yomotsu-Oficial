import re

file_path = "/workspace/Yomotsu-Oficial/app/src/main/java/eu/kanade/presentation/library/components/LibraryBadges.kt"
with open(file_path, "r") as f:
    content = f.read()

old_logic = """        val extensionManager = Injekt.get<ExtensionManager>()
        val icon = sourceId?.let { extensionManager.getAppIconForSource(it)?.toBitmap()?.asImageBitmap() }"""

new_logic = """        val icon = try {
            val extensionManager = Injekt.get<ExtensionManager>()
            sourceId?.let { extensionManager.getAppIconForSource(it)?.toBitmap()?.asImageBitmap() }
        } catch (e: Throwable) {
            null
        }"""

content = content.replace(old_logic, new_logic)

with open(file_path, "w") as f:
    f.write(content)

print("Fixed LibraryBadges.kt preview crash potential!")
