package ru.itoltec.swypetris

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import org.junit.Assert.assertEquals

/** Supplied cutouts have intrinsic alpha (including 253/255 centers); do not assume an opaque bitmap. */
internal fun artworkAlpha(atlas: PixelMap, type: Tetromino, index: Int,
    x: Float = 64f, y: Float = 64f, radius: Int = 2): Float {
    val sourceX = type.ordinal * 132 + 2
    val sourceY = index * 132 + 2
    val centerX = x.toInt().coerceIn(0, 127)
    val centerY = y.toInt().coerceIn(0, 127)
    // Include the footprint of minification filtering rather than choosing an unusually opaque texel.
    return (maxOf(0, centerY - radius)..minOf(127, centerY + radius)).minOf { row ->
        (maxOf(0, centerX - radius)..minOf(127, centerX + radius)).minOf { column ->
            atlas[sourceX + column, sourceY + row].alpha
        }
    }
}

/** A changed lower layer can contribute at most (1 - artwork alpha) to each color channel. */
internal fun assertArtworkAbove(message: String, before: Color, after: Color, alpha: Float) {
    val tolerance = 1f - alpha + 1f / 255f + .000001f
    assertEquals(message, before.red, after.red, tolerance)
    assertEquals(message, before.green, after.green, tolerance)
    assertEquals(message, before.blue, after.blue, tolerance)
    assertEquals(message, before.alpha, after.alpha, 0f)
}
