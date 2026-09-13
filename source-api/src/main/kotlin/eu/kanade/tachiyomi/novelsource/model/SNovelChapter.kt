@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.novelsource.model

import kotlinx.serialization.json.JsonObject
import java.io.Serializable

interface SNovelChapter : Serializable {

    var url: String

    var name: String

    var date_upload: Long

    var date_upload_raw: String?

    var chapter_number: Float

    var scanlator: String?

    var memo: JsonObject
        get() = JsonObject(emptyMap())
        set(value) {}

    fun copyFrom(other: SNovelChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        date_upload_raw = other.date_upload_raw
        chapter_number = other.chapter_number
        scanlator = other.scanlator
        memo = other.memo
    }

    companion object {
        fun create(): SNovelChapter {
            return SNovelChapterImpl()
        }
    }
}
