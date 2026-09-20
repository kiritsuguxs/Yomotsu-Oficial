package eu.kanade.tachiyomi.data.updater

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppUpdateRepositoryTest {

    @Test
    fun `updates are fetched from the official repository`() {
        assertEquals("kiritsuguxs/yomotsu", GITHUB_REPO)
    }
}
