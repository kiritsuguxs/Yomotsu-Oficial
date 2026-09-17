import re

file_path = "/workspace/Yomotsu-Oficial/presentation-core/src/main/java/tachiyomi/presentation/core/components/Badges.kt"
with open(file_path, "r") as f:
    content = f.read()

# Add imports
imports_to_add = """import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
"""

if "import androidx.compose.foundation.Image" not in content:
    content = content.replace("import androidx.compose.foundation.background", imports_to_add + "import androidx.compose.foundation.background")

# Replace fully qualified names
content = content.replace("androidx.compose.ui.graphics.ImageBitmap", "ImageBitmap")
content = content.replace("androidx.compose.ui.Alignment.CenterVertically", "Alignment.CenterVertically")
content = content.replace("androidx.compose.foundation.Image", "Image")
content = content.replace("androidx.compose.foundation.layout.size", "size")
content = content.replace("androidx.compose.foundation.shape.RoundedCornerShape", "RoundedCornerShape")

with open(file_path, "w") as f:
    f.write(content)

print("Fixed Badges.kt imports and fully qualified names!")
