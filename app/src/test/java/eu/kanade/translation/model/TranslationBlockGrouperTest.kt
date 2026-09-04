package eu.kanade.translation.model

import eu.kanade.translation.detection.DbnetCleanupMask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationBlockGrouperTest {

    @Test
    fun `fragments detected inside the same balloon are grouped`() {
        val first = block(text = "Hunters ranked E", x = 100f, y = 100f, layout = TranslationRegion(70f, 70f, 240f, 180f))
        val second = block(text = "and S rank alike", x = 105f, y = 155f, layout = TranslationRegion(75f, 75f, 230f, 170f))
        val result = TranslationBlockGrouper.group(listOf(first, second))
        assertEquals(1, result.size)
        assertEquals("Hunters ranked E and S rank alike", result.single().text)
        assertTrue(result.single().balloonDetected)
        assertEquals(100f, result.single().x)
        assertEquals(100f, result.single().height)
    }

    @Test
    fun `nearby fragments from separate detected balloons remain separate`() {
        val first = block(text = "First balloon", x = 100f, y = 100f, layout = TranslationRegion(60f, 60f, 180f, 130f))
        val second = block(text = "Second balloon", x = 105f, y = 175f, layout = TranslationRegion(60f, 195f, 180f, 130f))
        val result = TranslationBlockGrouper.group(listOf(first, second))
        assertEquals(2, result.size)
        assertTrue(result.all(TranslationBlock::balloonDetected))
    }

    @Test
    fun `partially overlapping regions from the same detected balloon are grouped`() {
        val first = block(text = "Stay farther", x = 100f, y = 100f, layout = TranslationRegion(60f, 60f, 220f, 150f))
        val second = block(text = "from the enemy", x = 105f, y = 165f, layout = TranslationRegion(65f, 180f, 210f, 150f))
        val result = TranslationBlockGrouper.group(listOf(first, second))
        assertEquals(1, result.size)
        assertEquals("Stay farther from the enemy", result.single().text)
        assertTrue(result.single().balloonDetected)
        assertEquals(270f, result.single().layoutRegion?.height)
    }

    @Test
    fun `separate experimental mask owners never fuse before or after translation`() {
        val first = block(text = "First line", x = 100f, y = 100f, layout = TranslationRegion(60f, 60f, 220f, 150f)).copy(
            dbnetCleanupMask = DbnetCleanupMask.fromRuns(400, 400, intArrayOf(100, 100, 2)),
        )
        val second = block(text = "Second line", x = 105f, y = 165f, layout = TranslationRegion(65f, 180f, 210f, 150f)).copy(
            dbnetCleanupMask = DbnetCleanupMask.fromRuns(400, 400, intArrayOf(165, 105, 2)),
        )

        assertEquals(2, TranslationBlockGrouper.group(listOf(first, second)).size)
        assertEquals(
            2,
            TranslationBlockGrouper.group(
                listOf(first.copy(translation = "Primeira"), second.copy(translation = "Segunda")),
            ).size,
        )
    }

    @Test
    fun `fragment missed by detection joins balloon that contains its center`() {
        val detected = block(text = "You should stay", x = 100f, y = 100f, layout = TranslationRegion(60f, 60f, 240f, 200f))
        val missed = block(text = "farther away", x = 110f, y = 170f, detected = false, layout = null)
        val result = TranslationBlockGrouper.group(listOf(detected, missed))
        assertEquals(1, result.size)
        assertTrue(result.single().balloonDetected)
        assertEquals(detected.layoutRegion, result.single().layoutRegion)
    }

    @Test
    fun `open ended OCR fragments remain one continuous paragraph`() {
        val first = block(text = "I can hear the knife on the", x = 100f, y = 100f, layout = TranslationRegion(60f, 60f, 180f, 100f))
        val second = block(text = "cutting board.", x = 102f, y = 146f, layout = TranslationRegion(60f, 170f, 180f, 100f))
        val result = TranslationBlockGrouper.group(listOf(first, second))
        assertEquals(1, result.size)
        assertEquals("I can hear the knife on the cutting board.", result.single().text)
    }

    @Test
    fun `finished sentences in separate detected regions remain separate`() {
        val first = block(text = "One sentence.", x = 100f, y = 100f, layout = TranslationRegion(60f, 60f, 180f, 100f))
        val second = block(text = "Another sentence.", x = 102f, y = 146f, layout = TranslationRegion(60f, 170f, 180f, 100f))
        assertEquals(2, TranslationBlockGrouper.group(listOf(first, second)).size)
    }

    @Test
    fun `post translation grouping does not bridge detached balloons because source OCR is open ended`() {
        val first = block(
            text = "First OCR region without terminal punctuation",
            translation = "Obrigado por me trazer até aqui.",
            x = 100f,
            y = 100f,
            layout = TranslationRegion(60f, 60f, 180f, 100f),
        )
        val second = block(
            text = "Second OCR region.",
            translation = "Tá tudo bem! Você veio de tão longe!",
            x = 102f,
            y = 146f,
            layout = TranslationRegion(60f, 170f, 180f, 100f),
        )

        assertEquals(2, TranslationBlockGrouper.group(listOf(first, second)).size)
    }

    @Test
    fun `connector word bridges a wider OCR gap inside one sentence`() {
        val first = block(text = "I can hear my mother's knife on the", x = 100f, y = 100f, layout = TranslationRegion(60f, 60f, 180f, 120f))
        val second = block(text = "cutting board.", x = 102f, y = 206f, layout = TranslationRegion(60f, 190f, 180f, 120f))
        val result = TranslationBlockGrouper.group(listOf(first, second))
        assertEquals(1, result.size)
        assertEquals("I can hear my mother's knife on the cutting board.", result.single().text)
    }

    @Test
    fun `strict mode does not bridge separate detected regions through connector`() {
        val first = block(text = "I can hear my mother's knife on the", x = 100f, y = 100f, layout = TranslationRegion(60f, 60f, 180f, 120f))
        val second = block(text = "watermark", x = 102f, y = 206f, layout = TranslationRegion(60f, 190f, 180f, 120f))
        assertEquals(2, TranslationBlockGrouper.group(listOf(first, second), strict = true).size)
    }

    @Test
    fun `strict mode still groups strongly overlapping balloon regions`() {
        val first = block(text = "First line", x = 100f, y = 100f, layout = TranslationRegion(60f, 60f, 220f, 150f))
        val second = block(text = "Second line", x = 105f, y = 165f, layout = TranslationRegion(65f, 180f, 210f, 150f))
        assertEquals(1, TranslationBlockGrouper.group(listOf(first, second), strict = true).size)
    }

    @Test
    fun `strict mode groups nearby PaddleOCR fragments that form one unfinished sentence`() {
        val first = block(text = "THE HUNTERS WERE", x = 100f, y = 100f, layout = TranslationRegion(60f, 60f, 190f, 90f))
        val second = block(text = "SUMMONED TOGETHER.", x = 104f, y = 148f, layout = TranslationRegion(64f, 142f, 186f, 92f))
        val result = TranslationBlockGrouper.group(listOf(first, second), strict = true)
        assertEquals(1, result.size)
        assertEquals("THE HUNTERS WERE SUMMONED TOGETHER.", result.single().text)
    }

    @Test
    fun `strict mode groups PaddleOCR lines across a normal balloon gap`() {
        val first = block(text = "THE HUNTERS WERE", x = 100f, y = 100f, layout = TranslationRegion(60f, 60f, 190f, 90f))
        val second = block(text = "SUMMONED TOGETHER.", x = 104f, y = 180f, layout = TranslationRegion(64f, 174f, 186f, 92f))
        val result = TranslationBlockGrouper.group(listOf(first, second), strict = true)
        assertEquals(1, result.size)
        assertEquals("THE HUNTERS WERE SUMMONED TOGETHER.", result.single().text)
    }

    @Test
    fun `strict mode keeps separate finished PaddleOCR balloons apart`() {
        val first = block(text = "THE HUNTERS WERE SUMMONED.", x = 100f, y = 100f, layout = TranslationRegion(60f, 60f, 190f, 90f))
        val second = block(text = "EVERYONE WAS READY.", x = 104f, y = 180f, layout = TranslationRegion(64f, 174f, 186f, 92f))
        assertEquals(2, TranslationBlockGrouper.group(listOf(first, second), strict = true).size)
    }

    @Test
    fun `translated connector can reveal a continuation after translation`() {
        val first = block(text = "First OCR region", translation = "Posso ouvir a faca da minha mãe no", x = 100f, y = 100f, layout = TranslationRegion(60f, 60f, 180f, 120f))
        val second = block(text = "Second OCR region", translation = "tábua de corte.", x = 102f, y = 206f, layout = TranslationRegion(60f, 190f, 180f, 120f))
        val result = TranslationBlockGrouper.group(listOf(first, second))
        assertEquals(1, result.size)
        assertEquals("Posso ouvir a faca da minha mãe no tábua de corte.", result.single().translation)
    }

    @Test
    fun `wide gap without connector does not join separate balloons`() {
        val first = block(text = "First balloon", translation = "Primeiro balão.", x = 100f, y = 100f, layout = TranslationRegion(60f, 60f, 180f, 120f))
        val second = block(text = "Second balloon", translation = "Segundo balão.", x = 102f, y = 206f, layout = TranslationRegion(60f, 190f, 180f, 120f))
        assertEquals(2, TranslationBlockGrouper.group(listOf(first, second)).size)
    }

    @Test
    fun `strict proximity fallback still groups split OCR lines`() {
        val first = block(text = "Split", x = 100f, y = 100f, detected = false, layout = null)
        val second = block(text = "line", x = 102f, y = 145f, detected = false, layout = null)
        val result = TranslationBlockGrouper.group(listOf(first, second))
        assertEquals(1, result.size)
        assertFalse(result.single().balloonDetected)
        assertEquals("Split line", result.single().text)
    }

    @Test
    fun `proximity fallback does not bridge a large vertical gap`() {
        val first = block(text = "Top", x = 100f, y = 100f, detected = false, layout = null)
        val second = block(text = "Bottom", x = 102f, y = 210f, detected = false, layout = null)
        assertEquals(2, TranslationBlockGrouper.group(listOf(first, second)).size)
    }

    private fun block(text: String, translation: String = "", x: Float, y: Float, detected: Boolean = true, layout: TranslationRegion?) = TranslationBlock(
        text = text, translation = translation, width = 100f, height = 45f, x = x, y = y,
        symHeight = 20f, symWidth = 12f, angle = 0f,
        cleanupRegion = TranslationRegion(x - 8f, y - 5f, 116f, 55f), layoutRegion = layout,
        backgroundColor = 0xfff4f4f4.toInt(), foregroundColor = 0xff000000.toInt(),
        balloonDetected = detected, geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
    )
}
