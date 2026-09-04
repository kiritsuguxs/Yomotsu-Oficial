package eu.kanade.translation.translator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MachineTranslationContextTest {

    @Test
    fun `builds chunks without changing text order`() {
        val chunks = buildMachineTranslationChunks(
            texts = listOf("first line", "second line", "third line"),
            maxCharacters = 65,
            maxItems = 2,
        )

        assertEquals(listOf(0, 1, 2), chunks.flatten().map { it.index })
        assertEquals(listOf("first line", "second line", "third line"), chunks.flatten().map { it.value })
        assertEquals(2, chunks.size)
    }

    @Test
    fun `restores translated blocks from preserved markers`() {
        val chunk = buildMachineTranslationChunks(
            texts = listOf("Good morning", "Wake up your brother"),
            maxCharacters = 500,
            maxItems = 10,
        ).single()
        val translated = """
            __YMX_BLOCK_0000__
            Bom dia
            __YMX_BLOCK_0001__
            Acorde seu irmão
        """.trimIndent()

        assertEquals(
            mapOf(0 to "Bom dia", 1 to "Acorde seu irmão"),
            parseMarkedMachineTranslations(translated, chunk),
        )
    }

    @Test
    fun `rejects output when translator removes a marker`() {
        val chunk = buildMachineTranslationChunks(
            texts = listOf("One", "Two"),
            maxCharacters = 500,
            maxItems = 10,
        ).single()

        assertNull(parseMarkedMachineTranslations("Um Dois", chunk))
    }

    @Test
    fun `retries a substantive translation repeated for different source text`() {
        assertEquals(
            setOf(0, 1, 2),
            findSuspiciousDuplicateTranslationIndices(
                sourceTexts = listOf(
                    "Shadow Monarch?",
                    "Dodge the attack!",
                    "Everyone fall back!",
                ),
                translations = listOf(
                    "Sombra Monarca?",
                    "Sombra Monarca!",
                    "Sombra Monarca.",
                ),
            ),
        )
    }

    @Test
    fun `preserves a repeated translation when the original is also repeated`() {
        assertEquals(
            emptySet<Int>(),
            findSuspiciousDuplicateTranslationIndices(
                sourceTexts = listOf("Shadow Monarch?", "Shadow Monarch?"),
                translations = listOf("Monarca das Sombras?", "Monarca das Sombras?"),
            ),
        )
    }

    @Test
    fun `preserves short dialogue that can legitimately translate the same way`() {
        assertEquals(
            emptySet<Int>(),
            findSuspiciousDuplicateTranslationIndices(
                sourceTexts = listOf("No!", "Don't!", "Stop!"),
                translations = listOf("Não!", "Não!", "Não!"),
            ),
        )
    }
}
