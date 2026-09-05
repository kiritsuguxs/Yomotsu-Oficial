package eu.kanade.translation.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationRecoveryPolicyTest {
    @Test fun `currently valid blocks keep their decision`() {
        assertTrue(TranslationRecoveryPolicy.isTranslatable(block().copy(
            text = "Hi", layoutRegion = TranslationRegion(95f, 95f, 80f, 110f),
        )))
    }

    @Test fun `narrow dialogue with strong balloon evidence is recovered`() {
        assertTrue(TranslationRecoveryPolicy.isTranslatable(block()))
    }

    @Test fun `weak evidence stays available for manual translation`() {
        val source = block()
        listOf(
            source.copy(balloonDetected = false),
            source.copy(backgroundColor = null),
            source.copy(layoutRegion = null),
            source.copy(cleanupRegion = null),
            source.copy(geometryVersion = 1),
            source.copy(angle = 30f),
            source.copy(symHeight = Float.NaN),
            source.copy(layoutRegion = TranslationRegion(0f, 0f, 35f, 110f)),
            source.copy(cleanupRegion = TranslationRegion(95f, 95f, 200f, 200f)),
            source.copy(width = Float.NaN),
        ).forEach { assertFalse(TranslationRecoveryPolicy.isTranslatable(it), it.toString()) }
    }

    @Test fun `sound effects repeated letters and noise are not recovered`() {
        listOf("BOOM!!", "BAM BAM BAM", "HA HA HA", "zzzz zzzz zzzz", "123 @@@ !!", "...", "?!").forEach {
            assertFalse(TranslationRecoveryPolicy.isTranslatable(block().copy(text = it)), it)
        }
    }

    @Test fun `extremely narrow text and bad source metrics stay rejected`() {
        assertFalse(TranslationRecoveryPolicy.isTranslatable(block().copy(
            layoutRegion = TranslationRegion(95f, 95f, 35f, 200f),
        )))
        assertFalse(TranslationRecoveryPolicy.isTranslatable(block().copy(symHeight = 90f)))
    }

    private fun block() = TranslationBlock(
        text = "Where\nare you\ngoing?", x = 100f, y = 100f, width = 25f, height = 100f,
        symWidth = 4f, symHeight = 25f, angle = 0f,
        balloonDetected = true, backgroundColor = -1,
        cleanupRegion = TranslationRegion(98f, 98f, 29f, 104f),
        layoutRegion = TranslationRegion(95f, 95f, 35f, 110f),
        geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
    )
}
