import re

with open("app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloadManager.kt", "r") as f:
    content = f.read()

# Fix getEpisodesToDelete
content = re.sub(
    r"downloadPreferences\.removeExcludeAnimeCategories\(\)\.get\(\)\.map\(String::toLong\)",
    "downloadPreferences.removeExcludeCategories().get().map(String::toLong)",
    content
)

content = content.replace("listOf(0)", "listOf(0L)")

# Fix getEpisodesToDownload
get_episodes_to_download = """    private fun getEpisodesToDownload(episodes: List<Episode>): List<Episode> {
        return episodes
    }"""

content = re.sub(
    r"private fun getEpisodesToDownload\(episodes: List<Episode>\): List<Episode> \{[\s\S]*?\}",
    get_episodes_to_download,
    content
)

with open("app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloadManager.kt", "w") as f:
    f.write(content)
print("Patched AnimeDownloadManager.kt")
