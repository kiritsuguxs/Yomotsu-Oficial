import re

file_path = "/workspace/Yomotsu-Oficial/app/src/main/java/eu/kanade/presentation/library/components/LibraryBadges.kt"
with open(file_path, "r") as f:
    content = f.read()

import_stmt = "import uy.kohesive.injekt.Injekt\nimport uy.kohesive.injekt.api.get\n"
if "import uy.kohesive.injekt.api.get" not in content:
    content = content.replace("import eu.kanade.domain.source.model.icon", "import eu.kanade.domain.source.model.icon\n" + import_stmt)

with open(file_path, "w") as f:
    f.write(content)

print("Fixed LibraryBadges.kt imports!")
