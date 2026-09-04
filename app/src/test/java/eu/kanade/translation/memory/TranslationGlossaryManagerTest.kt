package eu.kanade.translation.memory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranslationGlossaryManagerTest {

    @Test
    fun `parses several glossary separators in one paste`() {
        val parsed = TranslationGlossaryManager.parseMany(
            text = """
                Shadow Monarch => Monarca das Sombras
                Bloodlust -> Sede de Sangue
                Ruler's Authority	Autoridade do Governante
                Igris = Igris
                invalid line
            """.trimIndent(),
            type = TranslationMemoryEntryType.TECHNIQUE,
            isProtected = true,
        )

        assertEquals(4, parsed.entries.size)
        assertEquals(listOf(5), parsed.invalidLineNumbers)
        assertEquals("Autoridade do Governante", parsed.entries[2].target)
    }

    @Test
    fun `protected name list accepts one unchanged name per line`() {
        val parsed = TranslationGlossaryManager.parseMany(
            text = """
                Ashborn
                Sung Jin-Woo
            """.trimIndent(),
            type = TranslationMemoryEntryType.NAME,
            isProtected = true,
        )

        assertEquals(
            listOf("Ashborn" to "Ashborn", "Sung Jin-Woo" to "Sung Jin-Woo"),
            parsed.entries.map { it.source to it.target },
        )
        assertEquals(emptyList<Int>(), parsed.invalidLineNumbers)
    }

    @Test
    fun `unchanged ordinary terms are rejected`() {
        val parsed = TranslationGlossaryManager.parseMany(
            text = "Monarch => Monarch",
            type = TranslationMemoryEntryType.TERM,
            isProtected = false,
        )

        assertEquals(emptyList<TranslationMemoryEntry>(), parsed.entries)
        assertEquals(listOf(1), parsed.invalidLineNumbers)
    }
}
