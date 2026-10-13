package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Exercises native Canvas sampling of the actual shipped atlases, with independent pixel coordinates. */
class ConnectedMaterialRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allCanonicalCellsRotationsAndQuadrantsUseTheSelectedSharedAtlas() {
        val materials = mutableListOf<BlockMaterial>()
        compose.setContent {
            GamePalettes.all.forEach { palette ->
                val material = blockMaterial(palette)
                CompositionLocalProvider(LocalBlockMaterial provides material) {
                    assertSame(material, blockMaterial(palette))
                }
                materials.add(material)
            }
        }
        compose.runOnIdle {
            assertEquals(12, materials.size)
            materials.forEach { material ->
                val source = material.image.asAndroidBitmap()
                assertEquals(924, source.width)
                assertEquals(528, source.height)
                for (type in Tetromino.entries) for (index in 0..3) for (rotation in 0..3) {
                    val appearance = BlockAppearance(index, rotation)
                    val square = render(material, type, appearance, false, 128)
                    val full = render(material, type, appearance, false)
                    val parts = render(material, type, appearance, true)
                    val stretched = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(stretched).drawBitmap(square, null, Rect(0, 0, 256, 128),
                        Paint(Paint.FILTER_BITMAP_FLAG))
                    for ((x, y) in listOf(17 to 23, 91 to 41, 33 to 103, 101 to 89)) {
                        var sx = x
                        var sy = y
                        repeat(if (type == Tetromino.O) 0 else rotation) {
                            val previous = sx
                            sx = sy
                            sy = 127 - previous
                        }
                        val expected = source.getPixel(type.ordinal * 132 + 2 + sx, index * 132 + 2 + sy)
                        assertPixel("${material.texture}/$type/$appearance ($x,$y)", expected,
                            square.getPixel(x, y))
                        assertPixel("rotate before stretching ${material.texture}/$type/$appearance",
                            stretched.getPixel(x * 2, y), full.getPixel(x * 2, y))
                        assertPixel("quadrant ${material.texture}/$type/$appearance", full.getPixel(x * 2, y),
                            parts.getPixel(x * 2, y))
                    }
                    full.recycle()
                    parts.recycle()
                    square.recycle()
                    stretched.recycle()
                }
                exportFigures(material)
            }
        }
    }

    private fun render(material: BlockMaterial, type: Tetromino, appearance: BlockAppearance,
        fragments: Boolean, width: Int = 256): Bitmap {
        val bitmap = Bitmap.createBitmap(width, 128, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()),
            Size(width.toFloat(), 128f)) {
            if (fragments) repeat(4) { part ->
                material.draw(this, Offset((part % 2 * 128).toFloat(), (part / 2 * 64).toFloat()),
                    Size(128f, 64f), type, appearance, 1f, part)
            } else material.draw(this, Offset.Zero, Size(width.toFloat(), 128f), type, appearance, 1f)
        }
        return bitmap
    }

    private fun assertPixel(label: String, expected: Int, actual: Int) {
        // Premultiplied-alpha rounding may change a few channel values; interpolation is compared like-for-like.
        for (shift in listOf(0, 8, 16, 24)) {
            assertTrue("$label channel $shift: expected $expected, actual $actual",
                abs((expected ushr shift and 255) - (actual ushr shift and 255)) <= 3)
        }
    }

    private fun exportFigures(material: BlockMaterial) {
        if (InstrumentationRegistry.getArguments().getString("materialCapture") != "true") return
        val bitmap = Bitmap.createBitmap(1120, 640, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(1120f, 640f)) {
            for (type in Tetromino.entries) for (rotation in 0..3) {
                Piece(type, x = 0, y = 0, rotation = rotation).cells().forEachIndexed { index, cell ->
                    material.draw(this, Offset((type.ordinal * 160 + cell.x * 40).toFloat(),
                        (rotation * 160 + cell.y * 40).toFloat()), Size(40f, 40f), type,
                        BlockAppearance(index, rotation), 1f)
                }
            }
        }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.getExternalFilesDir(null), "connected-269").apply { mkdirs() }
        File(directory, "figures-${material.texture}.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
