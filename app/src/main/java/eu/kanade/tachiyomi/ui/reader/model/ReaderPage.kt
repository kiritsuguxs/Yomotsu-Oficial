package eu.kanade.tachiyomi.ui.reader.model

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationPageEditor
import java.io.InputStream

open class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
    var stream: (() -> InputStream)? = null,
    var translation: PageTranslation? = null,
    var translationEditor: TranslationPageEditor? = null,
) : Page(index, url, imageUrl, null) {

    open lateinit var chapter: ReaderChapter
}
