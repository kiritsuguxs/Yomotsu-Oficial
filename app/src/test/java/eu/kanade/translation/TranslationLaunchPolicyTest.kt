package eu.kanade.translation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationLaunchPolicyTest {

    @Test
    fun `download completion is denied while manga opt in is disabled`() {
        assertFalse(
            TranslationLaunchPolicy.canStart(
                origin = TranslationRequestOrigin.DOWNLOAD_COMPLETION,
                autoTranslateEnabled = false,
            ),
        )
    }

    @Test
    fun `download completion is allowed after manga opt in`() {
        assertTrue(
            TranslationLaunchPolicy.canStart(
                origin = TranslationRequestOrigin.DOWNLOAD_COMPLETION,
                autoTranslateEnabled = true,
            ),
        )
    }

    @Test
    fun `explicit user action is independent of manga opt in`() {
        assertTrue(TranslationLaunchPolicy.canStart(TranslationRequestOrigin.USER_ACTION, false))
        assertTrue(TranslationLaunchPolicy.canStart(TranslationRequestOrigin.USER_ACTION, true))
    }
}
