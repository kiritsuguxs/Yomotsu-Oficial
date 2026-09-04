package eu.kanade.translation.translator

import eu.kanade.translation.recognizer.TextRecognizerLanguage
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationQualityGuardTest {

    @Test
    fun `blank translation is retried`() {
        assertTrue(guard("I will protect you.", ""))
    }

    @Test
    fun `unchanged translation is retried`() {
        assertTrue(guard("I will protect you.", "I will protect you."))
    }

    @Test
    fun `english result with strong source overlap is retried`() {
        assertTrue(
            guard(
                "You should stay away from the enemy because they are dangerous.",
                "You should stay away from the enemy because they are dangerous.",
            ),
        )
    }

    @Test
    fun `proper portuguese translation is accepted`() {
        assertFalse(
            guard(
                "You should stay away from the enemy because they are dangerous.",
                "Você deveria ficar longe do inimigo porque eles são perigosos.",
            ),
        )
    }

    @Test
    fun `short names do not trigger language heuristic`() {
        assertFalse(guard("Shadow Monarch", "Monarca das Sombras"))
    }

    @Test
    fun `same language pair does not retry unchanged text`() {
        assertFalse(
            TranslationQualityGuard.shouldRetry(
                sourceText = "Hello world",
                translatedText = "Hello world",
                fromLang = TextRecognizerLanguage.ENGLISH,
                toLang = TextTranslatorLanguage.ENGLISH,
            ),
        )
    }

    private fun guard(source: String, target: String): Boolean =
        TranslationQualityGuard.shouldRetry(
            sourceText = source,
            translatedText = target,
            fromLang = TextRecognizerLanguage.ENGLISH,
            toLang = TextTranslatorLanguage.PORTUGUESE,
        )
}
