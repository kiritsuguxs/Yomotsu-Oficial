package eu.kanade.translation.memory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationGlossaryTest {

    @Test
    fun `protected names survive an engine changing token spacing`() {
        val entries = listOf(
            TranslationMemoryEntry(
                source = "Sung Jin-Woo",
                target = "Sung Jin-Woo",
                type = TranslationMemoryEntryType.NAME,
                isProtected = true,
            ),
        )
        val prepared = TranslationGlossary.prepare("Lord Sung Jin-Woo arrived.", entries)
        val spacedToken = prepared.protectedTerms.single().token.toCharArray().joinToString(" ")

        assertFalse(prepared.textForTranslation.contains("Sung Jin-Woo"))
        assertEquals(
            "O senhor Sung Jin-Woo chegou.",
            TranslationGlossary.resolve(prepared, "O senhor $spacedToken chegou.", entries),
        )
    }

    @Test
    fun `multiple protected titles and techniques are restored together`() {
        val entries = listOf(
            TranslationMemoryEntry(
                "Shadow Monarch",
                "Monarca das Sombras",
                TranslationMemoryEntryType.TITLE,
                isProtected = true,
            ),
            TranslationMemoryEntry(
                "Bloodlust",
                "Sede de Sangue",
                TranslationMemoryEntryType.TECHNIQUE,
                isProtected = true,
            ),
        )
        val prepared = TranslationGlossary.prepare("The Shadow Monarch used Bloodlust.", entries)
        val translated = "O ${prepared.protectedTerms[0].token} usou ${prepared.protectedTerms[1].token}."

        assertEquals(
            "O Monarca das Sombras usou Sede de Sangue.",
            TranslationGlossary.resolve(prepared, translated, entries),
        )
    }

    @Test
    fun `manual correction wins for the same complete speech even after punctuation changes`() {
        val correction = TranslationMemoryEntry(
            source = "I will protect you!",
            target = "Eu vou proteger você!",
            type = TranslationMemoryEntryType.MANUAL_CORRECTION,
            isProtected = true,
        )

        assertEquals(
            "Eu vou proteger você!",
            TranslationGlossary.apply("I WILL PROTECT YOU?", "", listOf(correction)),
        )
        assertEquals(
            "Ele prometeu proteger você.",
            TranslationGlossary.apply(
                "He said: I will protect you!",
                "Ele prometeu proteger você.",
                listOf(correction),
            ),
        )
    }

    @Test
    fun `prompt identifies entry types and excludes learned complete lines`() {
        val instructions = TranslationGlossary.instructions(
            listOf(
                TranslationMemoryEntry(
                    "Ashborn",
                    "Ashborn",
                    TranslationMemoryEntryType.NAME,
                    isProtected = true,
                ),
                TranslationMemoryEntry(
                    "Arise",
                    "Erga-se",
                    TranslationMemoryEntryType.TECHNIQUE,
                ),
                TranslationMemoryEntry(
                    "A full sentence",
                    "Uma frase completa",
                    TranslationMemoryEntryType.MANUAL_CORRECTION,
                    isProtected = true,
                ),
            ),
        )

        assertTrue(instructions.contains("[NOME PROTEGIDO] Ashborn => Ashborn"))
        assertTrue(instructions.contains("[TÉCNICA] Arise => Erga-se"))
        assertFalse(instructions.contains("A full sentence"))
    }
}
