import re

with open("app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloadManager.kt", "r") as f:
    content = f.read()

bad_code = """    private fun getEpisodesToDownload(episodes: List<Episode>): List<Episode> {
        return episodes
    }
        } else {
            episodes
        }
    }"""

good_code = """    private fun getEpisodesToDownload(episodes: List<Episode>): List<Episode> {
        return episodes
    }"""

content = content.replace(bad_code, good_code)

with open("app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloadManager.kt", "w") as f:
    f.write(content)
print("Patched AnimeDownloadManager.kt")
