package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundCropTest {
    @Test fun portraitFieldsPreserveImageAspectAndNeverExposeEdges() {
        for ((width, height) in listOf(320f to 640f, 393f to 873f, 600f to 960f)) {
            for ((imageWidth, imageHeight) in listOf(1600f to 900f, 900f to 1600f)) {
                val crop = transformBackground(BackgroundCrop(), imageWidth, imageHeight,
                    width, height, BackgroundTransform(width / 3f, height / 4f, 5000f, -5000f, 3f))
                val placed = backgroundPlacement(imageWidth, imageHeight, width, height, crop)
                assertEquals(imageWidth / imageHeight, placed.width / placed.height, .0001f)
                assertTrue(placed.left <= .001f && placed.top <= .001f)
                assertTrue(placed.left + placed.width >= width - .001f)
                assertTrue(placed.top + placed.height >= height - .001f)
            }
        }
    }

    @Test fun pinchKeepsItsImagePointAndDragMovesTheCrop() {
        val before = backgroundPlacement(1000f, 1000f, 400f, 800f, BackgroundCrop(2f))
        val crop = transformBackground(BackgroundCrop(2f), 1000f, 1000f, 400f, 800f,
            BackgroundTransform(150f, 300f, 0f, 0f, 1.5f))
        val after = backgroundPlacement(1000f, 1000f, 400f, 800f, crop)
        assertEquals((150f - before.left) / before.width, (150f - after.left) / after.width, .0001f)
        assertEquals((300f - before.top) / before.height, (300f - after.top) / after.height, .0001f)
        val dragged = transformBackground(crop, 1000f, 1000f, 400f, 800f,
            BackgroundTransform(150f, 300f, 30f, 40f, 1f))
        val moved = backgroundPlacement(1000f, 1000f, 400f, 800f, dragged)
        assertEquals(after.left + 30f, moved.left, .001f)
        assertEquals(after.top + 40f, moved.top, .001f)
    }

    @Test fun corruptStoredValuesAreBoundedAndEmptyViewportsAreSafe() {
        assertEquals(BackgroundCrop(), BackgroundCrop(Float.NaN, Float.NaN, Float.NaN).normalized())
        assertEquals(BackgroundCrop(5f, 0f, 1f), BackgroundCrop(100f, -1f, 2f).normalized())
        assertEquals(0f, backgroundPlacement(0f, 10f, 20f, 30f, BackgroundCrop()).width)
    }
}
