import re

with open("app/src/main/java/eu/kanade/tachiyomi/data/telegram/TelegramCloudManager.kt", "r") as f:
    content = f.read()

# I need to fix lines that start with spaces, then LogPriority.ERROR...
# e.g. `LogPriority.ERROR) {` -> `logcat(LogPriority.ERROR) {`
# e.g. `LogPriority.ERROR, e) {` -> `logcat(LogPriority.ERROR, e) {`

content = re.sub(r'(\s+)LogPriority\.ERROR\)', r'\1logcat(LogPriority.ERROR)', content)
content = re.sub(r'(\s+)LogPriority\.ERROR,', r'\1logcat(LogPriority.ERROR,', content)

with open("app/src/main/java/eu/kanade/tachiyomi/data/telegram/TelegramCloudManager.kt", "w") as f:
    f.write(content)
print("Patched logcat")
