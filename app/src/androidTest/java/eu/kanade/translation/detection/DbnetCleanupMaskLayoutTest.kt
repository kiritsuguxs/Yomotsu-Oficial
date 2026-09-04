package eu.kanade.translation.detection

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DbnetCleanupMaskLayoutTest {
    @Test fun oversizedCleanupMaskKeepsPageOriginAtViewportTopLeft() {
        val control = renderAtScale(1f)
        assertPixel("control background", BLACK, control, 20, 20)
        assertPixel("control first cleanup run", WHITE, control, 11, 10)
        assertPixel("control second cleanup run", WHITE, control, 31, 30)

        val zoomed = renderAtScale(2f)
        assertPixel("zoomed background before first run", BLACK, zoomed, 11, 10)
        assertPixel("zoomed first cleanup run stays at page origin", WHITE, zoomed, 21, 20)
        assertPixel("zoomed second cleanup run stays at page origin", WHITE, zoomed, 61, 60)
    }

    private fun renderAtScale(scaleFactor: Float): Bitmap {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(
            instrumentation.targetContext,
            DbnetCleanupMaskTestActivity::class.java,
        ).apply {
            putExtra(DbnetCleanupMaskTestActivity.EXTRA_SCALE_FACTOR, scaleFactor)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val activity = instrumentation.startActivitySync(intent) as DbnetCleanupMaskTestActivity
        try {
            instrumentation.waitForIdleSync()
            lateinit var bitmap: Bitmap
            instrumentation.runOnMainSync {
                assertEquals(
                    DbnetCleanupMaskTestActivity.VIEWPORT_SIZE,
                    activity.composeView.width,
                )
                assertEquals(
                    DbnetCleanupMaskTestActivity.VIEWPORT_SIZE,
                    activity.composeView.height,
                )
                bitmap = Bitmap.createBitmap(
                    DbnetCleanupMaskTestActivity.VIEWPORT_SIZE,
                    DbnetCleanupMaskTestActivity.VIEWPORT_SIZE,
                    Bitmap.Config.ARGB_8888,
                ).also { output ->
                    activity.composeView.draw(Canvas(output))
                }
            }
            return bitmap
        } finally {
            instrumentation.runOnMainSync(activity::finish)
            instrumentation.waitForIdleSync()
        }
    }

    private fun assertPixel(label: String, expected: Int, bitmap: Bitmap, x: Int, y: Int) {
        assertEquals(label, expected.toLong(), bitmap.getPixel(x, y).toLong())
    }

    companion object {
        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
    }
}
