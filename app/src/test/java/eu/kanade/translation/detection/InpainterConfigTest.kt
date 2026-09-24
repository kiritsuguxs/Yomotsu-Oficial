package eu.kanade.translation.detection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InpainterConfigTest {

    @Test
    fun `default config aligns with yakuyomi benchmarks`() {
        val cfg = InpainterConfig()
        assertEquals(InpainterConfig.METHOD_AOT, cfg.method)
        assertEquals(768, cfg.tileSize)
        assertEquals(24f, cfg.maskDilate)
        assertEquals(16, cfg.bboxPad)
    }

    @Test
    fun `boxfill config properties are retained`() {
        val cfg = InpainterConfig(method = InpainterConfig.METHOD_BOXFILL, tileSize = 512, maskDilate = 12f, bboxPad = 8)
        assertEquals("boxfill", cfg.method)
        assertEquals(512, cfg.tileSize)
        assertEquals(12f, cfg.maskDilate)
        assertEquals(8, cfg.bboxPad)
    }
}
