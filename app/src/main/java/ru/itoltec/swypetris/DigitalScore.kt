package ru.itoltec.swypetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp

internal const val SCORE_MAX_SCALE = 1.08f
private const val DIGIT_WIDTH = .48f
private const val DIGIT_GAP = .14f
private val digitSegments = intArrayOf(0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f)

/** Narrow seven-segment digits, including faint inactive segments, without a bundled font. */
@Composable
internal fun DigitalScore(value: String, height: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(height * digitalScoreAspect(value.length), height)
        .semantics { text = AnnotatedString(value) }) {
        value.forEachIndexed { index, character ->
            val mask = if (character in '0'..'9') digitSegments[character - '0'] else 0x40
            val x = index * (DIGIT_WIDTH + DIGIT_GAP) * size.height
            val width = DIGIT_WIDTH * size.height
            val thickness = .075f * size.height
            val middle = size.height / 2
            val segments = listOf(
                RectSegment(Offset(x + thickness / 2, 0f), width - thickness, thickness),
                RectSegment(Offset(x + width - thickness, thickness), thickness, middle - thickness * 1.5f),
                RectSegment(Offset(x + width - thickness, middle + thickness / 2), thickness, middle - thickness * 1.5f),
                RectSegment(Offset(x + thickness / 2, size.height - thickness), width - thickness, thickness),
                RectSegment(Offset(x, middle + thickness / 2), thickness, middle - thickness * 1.5f),
                RectSegment(Offset(x, thickness), thickness, middle - thickness * 1.5f),
                RectSegment(Offset(x + thickness / 2, middle - thickness / 2), width - thickness, thickness)
            )
            segments.forEachIndexed { segment, bounds ->
                digitalSegment(bounds, Color.White.copy(alpha = if (mask and (1 shl segment) != 0) 1f else .10f))
            }
        }
    }
}

internal fun digitalScoreAspect(digits: Int): Float = digits * DIGIT_WIDTH + (digits - 1).coerceAtLeast(0) * DIGIT_GAP

private data class RectSegment(val at: Offset, val width: Float, val height: Float)

private fun DrawScope.digitalSegment(segment: RectSegment, color: Color) {
    val (at, width, height) = segment
    val bevel = minOf(width, height) / 2
    val path = Path().apply {
        if (width > height) {
            moveTo(at.x, at.y + height / 2)
            lineTo(at.x + bevel, at.y)
            lineTo(at.x + width - bevel, at.y)
            lineTo(at.x + width, at.y + height / 2)
            lineTo(at.x + width - bevel, at.y + height)
            lineTo(at.x + bevel, at.y + height)
        } else {
            moveTo(at.x + width / 2, at.y)
            lineTo(at.x + width, at.y + bevel)
            lineTo(at.x + width, at.y + height - bevel)
            lineTo(at.x + width / 2, at.y + height)
            lineTo(at.x, at.y + height - bevel)
            lineTo(at.x, at.y + bevel)
        }
        close()
    }
    drawPath(path, color)
}
