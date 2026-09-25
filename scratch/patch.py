import re

with open("app/src/main/java/eu/kanade/tachiyomi/data/telegram/TelegramCloudManager.kt", "r") as f:
    content = f.read()

with open("scratch/patch_sync.kt", "r") as f:
    patch = f.read()

# We want to replace the `for (msg in messages)` loop inside `syncFromTelegram`
# Let's find the start of the loop
start_idx = content.find("for (msg in messages) {")
if start_idx != -1:
    # Find the end of this block by counting braces
    brace_count = 0
    end_idx = -1
    for i in range(start_idx, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                end_idx = i + 1
                break
    
    if end_idx != -1:
        new_content = content[:start_idx] + patch + content[end_idx:]
        with open("app/src/main/java/eu/kanade/tachiyomi/data/telegram/TelegramCloudManager.kt", "w") as f:
            f.write(new_content)
        print("Success")
    else:
        print("End brace not found")
else:
    print("Loop not found")

