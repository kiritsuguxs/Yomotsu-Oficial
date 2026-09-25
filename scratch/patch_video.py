import re

with open("app/src/main/java/eu/kanade/tachiyomi/data/telegram/TelegramCloudManager.kt", "r") as f:
    content = f.read()

bad_video = "val video = org.drinkless.tdlib.TdApi.InputMessageVideo(inputFile, thumbnail, IntArray(0), 0, 0, 0, false, caption)"
good_video = """val video = org.drinkless.tdlib.TdApi.InputMessageVideo().apply {
                        this.video = inputFile
                        this.thumbnail = thumbnail
                        this.caption = caption
                        this.addedStickerFileIds = IntArray(0)
                        this.supportsStreaming = false
                        this.duration = 0
                        this.width = 0
                        this.height = 0
                    }"""

content = content.replace(bad_video, good_video)

with open("app/src/main/java/eu/kanade/tachiyomi/data/telegram/TelegramCloudManager.kt", "w") as f:
    f.write(content)
print("Patched video constructor")
