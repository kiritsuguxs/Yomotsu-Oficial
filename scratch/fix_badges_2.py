import re

file_path = "/workspace/Yomotsu-Oficial/presentation-core/src/main/java/tachiyomi/presentation/core/components/Badges.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("import Image\nimport size\nimport androidx.compose.ui.Alignment\nimport ImageBitmap\n", "")

imports = """import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.shape.RoundedCornerShape
"""

content = content.replace("import androidx.compose.foundation.background", imports + "import androidx.compose.foundation.background")
content = content.replace("import RoundedCornerShape\n", "")

with open(file_path, "w") as f:
    f.write(content)

print("Fixed Badges.kt imports again!")
