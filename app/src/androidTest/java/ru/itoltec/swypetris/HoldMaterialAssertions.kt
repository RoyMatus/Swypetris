package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertTrue

/** Compare Hold against the active block path at the same cell size, artwork orientation and background. */
internal object HoldMaterialAssertions {
    fun exportPortraitIfRequested(theme: String, piece: Tetromino, capture: () -> ImageBitmap) {
        if (InstrumentationRegistry.getArguments().getString("holdCapture") != "true" ||
            piece != Tetromino.T || theme !in listOf("classic", "solarized_light", "gruvbox")) return
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.getExternalFilesDir(null), "hold-271").apply { mkdirs() }
        File(directory, "$theme-portrait-used.png").outputStream().use {
            assertTrue(capture().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    fun matchesActive(actual: PixelMap, material: BlockMaterial, palette: GamePalette,
        piece: Tetromino, density: Float) {
        val expected = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
        val cells = piece.shape
        val left = cells.minOf { it.x }
        val top = cells.minOf { it.y }
        val columns = cells.maxOf { it.x } - left + 1
        val rows = cells.maxOf { it.y } - top + 1
        val step = minOf((actual.width - 8 * density) / columns, (actual.height - 8 * density) / rows)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(expected.asImageBitmap()),
            Size(actual.width.toFloat(), actual.height.toFloat())) {
            drawRect(palette.background)
            cells.forEachIndexed { index, cell ->
                block(Cell(cell.x - left, cell.y - top), palette.piece(piece), palette.finish, palette.texture,
                    Offset(-step * .07f, -step * .07f), Size(step, step), material = material,
                    type = piece, appearance = BlockAppearance(index))
            }
        }
        val reference = expected.asImageBitmap().toPixelMap()
        // Ignore the two-pixel sampling fringe: hardware/software Canvas can place a filtered edge differently.
        cells.forEach { cell ->
            var error = 0f
            var samples = 0
            for (y in 2 until (step * .86f).toInt() - 2) for (x in 2 until (step * .86f).toInt() - 2) {
                val px = ((cell.x - left) * step).toInt() + x
                val py = ((cell.y - top) * step).toInt() + y
                val observed = actual[px, py]
                val target = reference[px, py]
                error += abs(observed.red - target.red) + abs(observed.green - target.green) +
                    abs(observed.blue - target.blue)
                samples++
            }
            assertTrue("Reference must sample every block", samples > 0)
            assertTrue("Full active material ${palette.id}/$piece/$cell: mean RGB error ${error / samples}",
                error / samples < .04f)
        }
        exportIfRequested(actual, expected, palette.id, piece)
        expected.recycle()
    }

    private fun exportIfRequested(actual: PixelMap, expected: Bitmap, theme: String, piece: Tetromino) {
        if (InstrumentationRegistry.getArguments().getString("holdCapture") != "true") return
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.getExternalFilesDir(null), "hold-271").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until actual.height) for (x in 0 until actual.width) bitmap.setPixel(x, y, actual[x, y].toArgb())
        for ((name, image) in listOf("hold" to bitmap, "active-reference" to expected)) {
            File(directory, "$theme-${piece.name}-$name.png").outputStream().use {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        bitmap.recycle()
    }
}
