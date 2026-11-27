package dev.lolokeksu.legioncontrol

import android.graphics.Bitmap
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureMediaLibraryInstrumentedTest {
    @Test fun publishedScreenshotCanBeFoundAndMissingFileIsRejected() {
        assertTrue("CI emulator only", Build.FINGERPRINT.lowercase().contains("generic") ||
            Build.FINGERPRINT.lowercase().contains("emulator") || Build.PRODUCT.lowercase().contains("sdk"))
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "LegionShot_${System.currentTimeMillis()}.png"
        val uri = CaptureScreenshotLibrary.createPending(app, name)
        try {
            assertTrue(CaptureMediaLibrary.page(app, name).items.isEmpty())
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            try {
                requireNotNull(app.contentResolver.openOutputStream(uri)).use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                bitmap.recycle()
            }
            CaptureScreenshotLibrary.publish(app, uri)
            val repository = CaptureStudioRepository(app)
            val item = repository.mediaPage(name, CaptureMediaFormat.PNG, null).items.single()
            assertEquals(name, item.name)
            assertEquals(CaptureMediaFormat.PNG, item.format)
            assertTrue(repository.mediaPage(name, CaptureMediaFormat.MP4, null).items.isEmpty())
            assertEquals(item.uri, repository.requireReadableMedia(item))
            assertTrue(runCatching {
                repository.requireReadableMedia(item.copy(name = "LegionShot_1000000000000.png"))
            }.isFailure)
            assertTrue(repository.deleteOwnMedia(item))
            assertTrue(runCatching { repository.requireReadableMedia(item) }.isFailure)
            assertFalse(repository.deleteOwnMedia(item))
        } finally {
            runCatching { CaptureScreenshotLibrary.delete(app, uri) }
        }
    }
}
