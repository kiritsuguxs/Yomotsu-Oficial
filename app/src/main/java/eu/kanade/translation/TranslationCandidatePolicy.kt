package eu.kanade.translation

import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.translation.model.Translation

object TranslationCandidatePolicy {
    fun canQueue(
        downloadState: Download.State,
        translationState: Translation.State,
    ): Boolean = downloadState == Download.State.DOWNLOADED &&
        translationState in setOf(
            Translation.State.NOT_TRANSLATED,
            Translation.State.ERROR,
        )
}
