package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundStorageTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val preferences = application.getSharedPreferences("background_test_${UUID.randomUUID()}", 0)
    private val directory = File(application.cacheDir, "background-test-${UUID.randomUUID()}")
    private lateinit var store: BackgroundStore

    @Before fun setUp() {
        check(directory.mkdirs())
        store = BackgroundStore(File(directory, "copies"), preferences)
    }

    @After fun tearDown() {
        preferences.edit().clear().commit()
        directory.deleteRecursively()
    }

    @Test fun savedCopyAndCropSurviveSourceRemovalAndEnableToggle() {
        val source = image("source.png")
        val selected = store.importImage(application.contentResolver, Uri.fromFile(source))
        assertEquals(320, selected.bitmap.width)
        assertEquals(160, selected.bitmap.height)
        val crop = BackgroundCrop(2f, .4f, .6f)
        store.save(selected, crop)
        assertTrue(source.delete())
        val restored = BackgroundStore(File(directory, "copies"), preferences)
        assertTrue(restored.enabled)
        assertEquals(crop, restored.crop)
        assertNotNull(restored.restore())
        restored.setEnabled(false)
        assertFalse(restored.enabled)
        assertNotNull(restored.restore())
        restored.setEnabled(true)
        assertEquals(crop, restored.crop)
        assertEquals(Color.RED, restored.restore()!!.bitmap.getPixel(0, 0))
    }

    @Test fun failedImportAndCorruptOrMissingSavedCopyFailSafely() {
        val selected = store.importImage(application.contentResolver, Uri.fromFile(image("source.png")))
        val saved = store.save(selected, BackgroundCrop())
        val nonEmpty = File(directory, "cleanup-failure").apply { mkdirs() }
        val child = File(nonEmpty, "retain.png").apply { writeText("retained") }
        deleteBackgroundCopy(nonEmpty)
        assertTrue(child.isFile)
        assertNotNull(store.restore())
        val corrupt = File(directory, "bad.png").apply { writeText("not an image") }
        try {
            store.importImage(application.contentResolver, Uri.fromFile(corrupt))
            throw AssertionError("Corrupt image was accepted")
        } catch (_: IOException) { assertNotNull(store.restore()) }
        assertTrue(File(directory, "copies").listFiles()!!.none { it.extension == "tmp" })
        saved.file.writeText("corrupt saved copy")
        assertNull(store.restore())
        assertTrue(saved.file.delete())
        assertNull(store.restore())
        preferences.edit().putString("background_file", "../source.png").commit()
        assertNull(store.restore())
    }

    @Test fun oversizedInputsAreRejectedBeforeDecodeAndNewImagesResetCrop() {
        val large = File(directory, "large.png")
        java.io.RandomAccessFile(large, "rw").use { it.setLength(17L * 1024 * 1024) }
        try {
            store.importImage(application.contentResolver, Uri.fromFile(large))
            throw AssertionError("Oversized image was accepted")
        } catch (_: IOException) { assertNull(store.restore()) }
        val first = store.importImage(application.contentResolver, Uri.fromFile(image("first.png")))
        val firstSaved = store.save(first, BackgroundCrop(3f, .3f, .7f))
        val second = store.importImage(application.contentResolver, Uri.fromFile(image("second.png")))
        val secondSaved = store.save(second, BackgroundCrop())
        assertFalse(firstSaved.file.exists())
        assertTrue(secondSaved.file.exists())
        assertEquals(BackgroundCrop(), store.crop)
    }

    @Test fun photoOrientationAndLargeDimensionsAreNormalizedInTheCopy() {
        val source = File(directory, "portrait-photo.jpg")
        val bitmap = Bitmap.createBitmap(3000, 1500, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        bitmap.recycle()
        android.media.ExifInterface(source.path).apply {
            setAttribute(android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val selected = store.importImage(application.contentResolver, Uri.fromFile(source))
        assertEquals(750, selected.bitmap.width)
        assertEquals(1500, selected.bitmap.height)
        assertEquals(android.media.ExifInterface.ORIENTATION_ROTATE_90,
            android.media.ExifInterface(source.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 0))
        val saved = store.save(selected, BackgroundCrop())
        assertTrue(saved.file.isFile)
        assertEquals(750, store.restore()!!.bitmap.width)
        assertEquals(1500, store.restore()!!.bitmap.height)
    }

    private fun image(name: String): File {
        val bitmap = Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val source = File(directory, name)
        source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return source
    }
}
