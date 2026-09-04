package eu.kanade.translation.data

import eu.kanade.tachiyomi.R
import tachiyomi.core.common.preference.Preference

enum class TranslationFont(
    val label: String,
    val res: Int,
    val preferenceValue: Int,
) {
    ANIME_ACE("Anime Ace", R.font.animeace, 0),
    MANGA_MASTER_BB("Manga Master BB", R.font.manga_master_bb, 1),
    BUBBLE_SANS("Bubble Sans", R.font.bubble_sans, 3),
    COMIC_SPICE("ComicSpice", R.font.comic_spice, 4),
    BALSAMIQ_SANS("Balsamiq Sans", R.font.balsamiq_sans, 5),
    ;

    companion object {
        val selectableEntries: List<TranslationFont> = entries

        fun fromPreferenceValue(value: Int): TranslationFont {
            return entries.firstOrNull { it.preferenceValue == value } ?: ANIME_ACE
        }

        fun fromPref(pref: Preference<Int>): TranslationFont {
            val storedValue = pref.get()
            val font = fromPreferenceValue(storedValue)
            if (font.preferenceValue != storedValue) {
                pref.set(font.preferenceValue)
            }
            return font
        }
    }
}
