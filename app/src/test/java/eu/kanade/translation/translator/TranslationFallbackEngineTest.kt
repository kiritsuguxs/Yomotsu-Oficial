package eu.kanade.translation.translator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TranslationFallbackEngineTest {

    @Test
    fun `unknown fallback values resolve to none`() {
        assertEquals(TranslationFallbackEngine.NONE, TranslationFallbackEngine.fromValue(-1))
        assertEquals(TranslationFallbackEngine.NONE, TranslationFallbackEngine.fromValue(99))
    }

    @Test
    fun `only explicitly authorized no key fallbacks are available`() {
        assertEquals(
            listOf(
                TranslationFallbackEngine.NONE,
                TranslationFallbackEngine.ML_KIT,
                TranslationFallbackEngine.GOOGLE,
            ),
            TranslationFallbackEngine.entries,
        )
    }

    @Test
    fun `none never resolves a fallback translator`() {
        assertNull(
            TranslationFallbackEngine.NONE.resolve(
                primary = TextTranslators.GEMINI,
                mlKitTargetSupported = true,
            ),
        )
    }

    @Test
    fun `explicit local fallback resolves only when supported and different from primary`() {
        assertEquals(
            TextTranslators.MLKIT,
            TranslationFallbackEngine.ML_KIT.resolve(
                primary = TextTranslators.OPENROUTER,
                mlKitTargetSupported = true,
            ),
        )
        assertNull(
            TranslationFallbackEngine.ML_KIT.resolve(
                primary = TextTranslators.OPENROUTER,
                mlKitTargetSupported = false,
            ),
        )
        assertNull(
            TranslationFallbackEngine.ML_KIT.resolve(
                primary = TextTranslators.MLKIT,
                mlKitTargetSupported = true,
            ),
        )
    }

    @Test
    fun `explicit keyless online fallback never duplicates the primary`() {
        assertEquals(
            TextTranslators.GOOGLE,
            TranslationFallbackEngine.GOOGLE.resolve(
                primary = TextTranslators.DEEPL,
                mlKitTargetSupported = false,
            ),
        )
        assertNull(
            TranslationFallbackEngine.GOOGLE.resolve(
                primary = TextTranslators.GOOGLE,
                mlKitTargetSupported = true,
            ),
        )
    }
}
