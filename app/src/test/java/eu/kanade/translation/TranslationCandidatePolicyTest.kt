package eu.kanade.translation

import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.translation.model.Translation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationCandidatePolicyTest {
    @Test
    fun `downloaded untranslated or failed chapters can be queued`() {
        assertTrue(
            TranslationCandidatePolicy.canQueue(
                Download.State.DOWNLOADED,
                Translation.State.NOT_TRANSLATED,
            ),
        )
        assertTrue(
            TranslationCandidatePolicy.canQueue(
                Download.State.DOWNLOADED,
                Translation.State.ERROR,
            ),
        )
    }

    @Test
    fun `missing active or finished chapters are excluded`() {
        assertFalse(
            TranslationCandidatePolicy.canQueue(
                Download.State.NOT_DOWNLOADED,
                Translation.State.NOT_TRANSLATED,
            ),
        )
        assertFalse(TranslationCandidatePolicy.canQueue(Download.State.DOWNLOADED, Translation.State.QUEUE))
        assertFalse(TranslationCandidatePolicy.canQueue(Download.State.DOWNLOADED, Translation.State.TRANSLATING))
        assertFalse(TranslationCandidatePolicy.canQueue(Download.State.DOWNLOADED, Translation.State.TRANSLATED))
    }
}
