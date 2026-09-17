import re

file_path = "/workspace/Yomotsu-Oficial/app/src/main/java/eu/kanade/presentation/library/components/LibraryBadges.kt"
with open(file_path, "r") as f:
    content = f.read()

# Remove the wrong icon import
content = content.replace("import eu.kanade.domain.source.model.icon\n", "")

# Add imports for icon processing
imports = """import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.ExtensionManager
"""
content = content.replace("import uy.kohesive.injekt.Injekt", imports + "import uy.kohesive.injekt.Injekt")

# Replace sourceManager logic with direct ExtensionManager logic
old_logic = """        val sourceManager = uy.kohesive.injekt.Injekt.get<tachiyomi.domain.source.service.SourceManager>()
        val source = sourceId?.let { sourceManager.getOrStub(it) }
        val icon = source?.icon"""

new_logic = """        val extensionManager = Injekt.get<ExtensionManager>()
        val icon = sourceId?.let { extensionManager.getAppIconForSource(it)?.toBitmap()?.asImageBitmap() }"""

content = content.replace(old_logic, new_logic)

with open(file_path, "w") as f:
    f.write(content)

print("Fixed LibraryBadges.kt receiver type mismatch!")
