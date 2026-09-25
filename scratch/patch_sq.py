import re

with open("data/src/main/sqldelightanime/dataanime/animes.sq", "r") as f:
    content = f.read()

old_insert = """insertNetworkAnime {
    -- Insert the anime if it doesn't exist already
    INSERT INTO animes(
        source, url, artist, author, description, genre, title, status, thumbnail_url, favorite,
        last_update, next_update, initialized, viewer, episode_flags, cover_last_modified, date_added,
        update_strategy, calculate_interval, last_modified_at, version, memo
    )
    SELECT
        :source, :url, :artist, :author, :description, :genre, :title, :status, :thumbnailUrl, :favorite,
        :lastUpdate, :nextUpdate, :initialized, :viewerFlags, :episodeFlags, :coverLastModified, :dateAdded,
        :updateStrategy, :calculateInterval, 0, :version, :memo
    WHERE NOT EXISTS(SELECT 0 FROM animes WHERE source = :source AND url = :url);"""

new_insert = """insertNetworkAnime {
    -- Insert the anime if it doesn't exist already
    INSERT INTO animes(
        source, url, artist, author, description, genre, title, status, thumbnail_url, favorite,
        last_update, next_update, initialized, viewer, episode_flags, cover_last_modified, date_added,
        update_strategy, calculate_interval, last_modified_at, version, fetch_type, parent_id,
        season_flags, season_number, season_source_order, background_url, background_last_modified, memo
    )
    SELECT
        :source, :url, :artist, :author, :description, :genre, :title, :status, :thumbnailUrl, :favorite,
        :lastUpdate, :nextUpdate, :initialized, :viewerFlags, :episodeFlags, :coverLastModified, :dateAdded,
        :updateStrategy, :calculateInterval, 0, :version, :fetchType, :parentId,
        :seasonFlags, :seasonNumber, :seasonSourceOrder, :backgroundUrl, :backgroundLastModified, :memo
    WHERE NOT EXISTS(SELECT 0 FROM animes WHERE source = :source AND url = :url);"""

content = content.replace(old_insert, new_insert)

with open("data/src/main/sqldelightanime/dataanime/animes.sq", "w") as f:
    f.write(content)
print("Patched animes.sq")
