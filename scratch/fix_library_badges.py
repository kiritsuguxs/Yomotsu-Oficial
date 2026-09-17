import re

file_path = "/workspace/Yomotsu-Oficial/app/src/main/java/eu/kanade/presentation/library/components/LibraryBadges.kt"
with open(file_path, "r") as f:
    content = f.read()

# Add import
import_stmt = "import eu.kanade.domain.source.model.icon\n"
if "import eu.kanade.domain.source.model.icon" not in content:
    content = content.replace("import tachiyomi.presentation.core.components.Badge", "import tachiyomi.presentation.core.components.Badge\n" + import_stmt)

# Fix extension property
content = content.replace("val icon = source?.let { eu.kanade.domain.source.model.icon(it) }", "val icon = source?.icon")

with open(file_path, "w") as f:
    f.write(content)

print("Fixed LibraryBadges.kt!")
