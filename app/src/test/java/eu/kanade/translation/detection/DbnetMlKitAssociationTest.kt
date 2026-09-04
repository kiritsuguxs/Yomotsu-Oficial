package eu.kanade.translation.detection

import eu.kanade.translation.recognizer.OcrPage
import eu.kanade.translation.recognizer.OcrTextBlock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DbnetMlKitAssociationTest {
    @Test fun `one ML Kit block may own multiple DBNet lines in one group`() {
        val first = line(20f, 20f, 80f, 30f)
        val second = line(20f, 34f, 80f, 44f)
        val group = DbnetLineGrouping.group(listOf(first, second)).single()

        val result = associate(
            groups = listOf(group),
            blocks = listOf(block("First line\nSecond line", 18f, 18f, 64f, 28f)),
        )

        assertEquals(listOf("First line\nSecond line"), result.blocks.map { it.text })
        assertEquals(group.bounds.left, result.blocks.single().x)
        assertEquals(group.bounds.top, result.blocks.single().y)
        val metadata = requireNotNull(result.dbnetAssociation)
        assertEquals(group.memberLines, metadata.groups.single().group.memberLines)
        assertEquals(0, metadata.groups.single().blockIndex)
    }

    @Test fun `multiple compatible ML Kit blocks become one coherent group block`() {
        val group = DbnetLineGrouping.group(
            listOf(line(20f, 20f, 80f, 30f), line(20f, 34f, 80f, 44f)),
        ).single()

        val result = associate(
            groups = listOf(group),
            blocks = listOf(
                block("First line", 18f, 18f, 64f, 14f),
                block("Second line", 18f, 32f, 64f, 14f),
            ),
        )

        assertEquals(1, result.blocks.size)
        assertEquals("First line\nSecond line", result.blocks.single().text)
        assertEquals(2, result.dbnetAssociation!!.groups.single().group.memberLines.size)
    }

    @Test fun `partially recognized grouped region is rejected before cleanup can own every member`() {
        val group = DbnetLineGrouping.group(
            listOf(line(20f, 20f, 80f, 30f), line(20f, 34f, 80f, 44f)),
        ).single()

        assertThrows(DbnetAssociationException::class.java) {
            associate(
                groups = listOf(group),
                blocks = listOf(block("Only the first line", 18f, 18f, 64f, 14f)),
            )
        }
    }

    @Test fun `adjacent groups remain separate association owners`() {
        val first = DbnetLineGrouping.group(listOf(line(10f, 20f, 60f, 30f))).single()
        val second = DbnetLineGrouping.group(listOf(line(75f, 20f, 125f, 30f))).single()

        val result = associate(
            groups = listOf(second, first),
            blocks = listOf(
                block("Right", 74f, 18f, 52f, 14f),
                block("Left", 9f, 18f, 52f, 14f),
            ),
        )

        assertEquals(listOf("Left", "Right"), result.blocks.map { it.text })
        val metadata = requireNotNull(result.dbnetAssociation)
        assertEquals(listOf(0, 1), metadata.groups.map { it.blockIndex })
        assertEquals(listOf(first, second), metadata.groups.map { it.group })
    }

    @Test fun `unmatched detector group is rejected as noise without losing assigned text`() {
        val text = DbnetLineGrouping.group(listOf(line(10f, 20f, 60f, 30f))).single()
        val detectorNoise = DbnetLineGrouping.group(listOf(line(140f, 150f, 150f, 160f))).single()

        val result = associate(
            groups = listOf(text, detectorNoise),
            blocks = listOf(block("Kept", 9f, 18f, 52f, 14f)),
        )

        assertEquals(listOf("Kept"), result.blocks.map { it.text })
        assertEquals(listOf(text), result.dbnetAssociation!!.groups.map { it.group })
    }

    @Test fun `same-geometry duplicate is emitted once but repeated text elsewhere is preserved`() {
        val first = DbnetLineGrouping.group(listOf(line(10f, 20f, 60f, 30f))).single()
        val second = DbnetLineGrouping.group(listOf(line(10f, 70f, 60f, 80f))).single()
        val duplicate = block("Again", 9f, 18f, 52f, 14f)

        val result = associate(
            groups = listOf(first, second),
            blocks = listOf(
                duplicate,
                duplicate.copy(),
                block("Again", 9f, 68f, 52f, 14f),
            ),
        )

        assertEquals(listOf("Again", "Again"), result.blocks.map { it.text })
        assertEquals(2, result.dbnetAssociation!!.groups.size)
    }

    @Test fun `ML Kit block spanning incompatible groups is ambiguous`() {
        val first = DbnetLineGrouping.group(listOf(line(10f, 20f, 60f, 30f))).single()
        val second = DbnetLineGrouping.group(listOf(line(70f, 20f, 120f, 30f))).single()

        assertThrows(DbnetAssociationException::class.java) {
            associate(
                groups = listOf(first, second),
                blocks = listOf(block("Cannot be split", 8f, 18f, 114f, 14f)),
            )
        }
    }

    @Test fun `meaningful unmatched ML Kit text cannot silently disappear`() {
        val group = DbnetLineGrouping.group(listOf(line(10f, 20f, 60f, 30f))).single()

        assertThrows(DbnetAssociationException::class.java) {
            associate(
                groups = listOf(group),
                blocks = listOf(
                    block("Matched", 9f, 18f, 52f, 14f),
                    block("Unassigned", 120f, 120f, 40f, 14f),
                ),
            )
        }
    }

    @Test fun `no usable association fails instead of producing a partial page`() {
        val group = DbnetLineGrouping.group(listOf(line(10f, 20f, 60f, 30f))).single()

        assertThrows(DbnetAssociationException::class.java) {
            associate(groups = listOf(group), blocks = emptyList())
        }
    }

    @Test fun `page dimensions geometry and text are validated independently`() {
        val group = DbnetLineGrouping.group(listOf(line(10f, 20f, 60f, 30f))).single()
        val detection = detection(listOf(group))

        assertThrows(DbnetAssociationException::class.java) {
            DbnetMlKitAssociation.associate(detection, listOf(group), page(listOf(block("Text", 9f, 18f, 52f, 14f)), width = 199))
        }
        assertThrows(DbnetAssociationException::class.java) {
            DbnetMlKitAssociation.associate(detection, listOf(group), page(listOf(block("Text", Float.NaN, 18f, 52f, 14f))))
        }
        assertThrows(DbnetAssociationException::class.java) {
            DbnetMlKitAssociation.associate(detection, listOf(group), page(listOf(block("   ", 9f, 18f, 52f, 14f))))
        }
    }

    @Test fun `association retains the exact detector mask metadata`() {
        val group = DbnetLineGrouping.group(listOf(line(10f, 20f, 60f, 30f))).single()
        val detection = detection(listOf(group))

        val result = DbnetMlKitAssociation.associate(
            detection,
            listOf(group),
            page(listOf(block("Text", 9f, 18f, 52f, 14f))),
        )

        val metadata = requireNotNull(result.dbnetAssociation)
        assertSame(detection.mask, metadata.mask)
        assertEquals(group.memberLines, metadata.groups.single().group.memberLines)
    }

    private fun associate(groups: List<DbnetTextGroup>, blocks: List<OcrTextBlock>): OcrPage {
        val detection = detection(groups)
        return DbnetMlKitAssociation.associate(detection, groups, page(blocks))
    }

    private fun detection(groups: List<DbnetTextGroup>) = DetectionResult.Success(
        width = PAGE_WIDTH,
        height = PAGE_HEIGHT,
        regions = groups.flatMap { it.memberLines },
        mask = DbnetMask(1, 1, PAGE_WIDTH, PAGE_HEIGHT, 1f, byteArrayOf(1)),
    )

    private fun page(blocks: List<OcrTextBlock>, width: Int = PAGE_WIDTH) = OcrPage(
        width = width,
        height = PAGE_HEIGHT,
        blocks = blocks,
    )

    private fun block(text: String, x: Float, y: Float, width: Float, height: Float) = OcrTextBlock(
        text = text,
        x = x,
        y = y,
        width = width,
        height = height,
        symbolWidth = 8f,
        symbolHeight = 10f,
        angle = 0f,
        confidence = 0.9f,
    )

    private fun line(left: Float, top: Float, right: Float, bottom: Float) = TextRegion(
        topLeft = DetectionPoint(left, top),
        topRight = DetectionPoint(right, top),
        bottomRight = DetectionPoint(right, bottom),
        bottomLeft = DetectionPoint(left, bottom),
        confidence = 0.9f,
    )

    private companion object {
        const val PAGE_WIDTH = 200
        const val PAGE_HEIGHT = 200
    }
}

class DbnetFullPageMlKitCoordinatorTest {
    @Test fun `selected ML Kit success is reused unchanged when association later fails`() = runTest {
        val selectedPage = "complete selected page"
        val selected = FakeSession(selectedPage)
        val owned = FakeSession("owned page")
        val owner = DbnetFullPageMlKitOwner(
            selectedIsMlKit = true,
            selected = selected,
            createOwnedMlKit = { owned },
        )
        val attempt = owner.beginPage()

        val result = ExperimentalDetectionRoute.execute(
            enabled = true,
            supported = true,
            experimental = {
                assertSame(selectedPage, attempt.recognizeForAssociation(Unit))
                throw DbnetAssociationException("ambiguous")
            },
            existing = { attempt.fallback(Unit) },
            onFallback = {},
        )

        assertSame(selectedPage, result)
        assertEquals(1, selected.recognitions)
        assertEquals(0, owned.recognitions)
    }

    @Test fun `Paddle selection uses one owned ML Kit pass then one selected fallback`() = runTest {
        val selected = FakeSession("selected Paddle page")
        val owned = FakeSession("full-page ML Kit")
        val owner = DbnetFullPageMlKitOwner(
            selectedIsMlKit = false,
            selected = selected,
            createOwnedMlKit = { owned },
        )
        val attempt = owner.beginPage()

        val result = ExperimentalDetectionRoute.execute(
            enabled = true,
            supported = true,
            experimental = {
                assertEquals("full-page ML Kit", attempt.recognizeForAssociation(Unit))
                throw DbnetAssociationException("ambiguous")
            },
            existing = { attempt.fallback(Unit) },
            onFallback = {},
        )

        assertEquals("selected Paddle page", result)
        assertEquals(1, selected.recognitions)
        assertEquals(1, owned.recognitions)
    }

    @Test fun `owned ML Kit is lazy and released without releasing selected engine`() = runTest {
        val selected = FakeSession("selected")
        val owned = FakeSession("owned")
        var creations = 0
        val owner = DbnetFullPageMlKitOwner(
            selectedIsMlKit = false,
            selected = selected,
            createOwnedMlKit = {
                creations++
                owned
            },
        )

        assertEquals(0, creations)
        owner.beginPage().recognizeForAssociation(Unit)
        owner.beginPage().recognizeForAssociation(Unit)
        owner.releaseOwned()

        assertEquals(1, creations)
        assertEquals(2, owned.recognitions)
        assertEquals(1, owned.releases)
        assertEquals(0, selected.releases)
    }

    @Test fun `failure before selected ML Kit success invokes selected fallback once`() = runTest {
        val selected = FakeSession("fallback")
        val owner = DbnetFullPageMlKitOwner(
            selectedIsMlKit = true,
            selected = selected,
            createOwnedMlKit = { error("owned engine must not be created") },
        )
        val attempt = owner.beginPage()

        val result = ExperimentalDetectionRoute.execute(
            enabled = true,
            supported = true,
            experimental = { throw IllegalStateException("detector failed before ML Kit") },
            existing = { attempt.fallback(Unit) },
            onFallback = {},
        )

        assertEquals("fallback", result)
        assertEquals(1, selected.recognitions)
    }

    private class FakeSession(private val value: String) : DbnetFullPageSession<Unit, String> {
        var recognitions = 0
        var releases = 0

        override suspend fun recognize(input: Unit): String {
            recognitions++
            return value
        }

        override suspend fun release() {
            releases++
        }
    }
}
