package eu.kanade.tachiyomi.extension.novel.tts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class EdgeTtsClientTest {

    @Test
    fun `generateSecMsGec produces 64 character uppercase hex SHA-256 token`() {
        val token = EdgeTtsClient.generateSecMsGec()
        assertNotNull(token)
        assertEquals(64, token.length)
        assert(token.all { it in '0'..'9' || it in 'A'..'F' })
    }
}
