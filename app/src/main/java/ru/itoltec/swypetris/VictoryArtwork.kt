package ru.itoltec.swypetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt

private const val STAR_COUNT = 48
private const val STAR_CROSS_PIXELS = 3
private val SKY_TOP = Color(0xFF001333)
private val SKY_BOTTOM = Color(0xFF001020)
private const val FLOOR_LINES = 6
private const val FLOOR_COLUMNS = 10
private const val SPRITE_BASE_FRACTION = .905f
private const val FLOOR_DEPTH_FRACTION = .05f
private const val FLOOR_CONVERGENCE = .4f
private const val ACTION_HEIGHT_DP = 64
private const val ACTION_SHADOW_DP = 4
private val FLOOR_COLOR = Color(0xFF145DA4)
private val ACTION_HIGHLIGHT = Color(0xFF48CF38)
private val ACTION_FACE = Color(0xFF007E23)
private val ACTION_BOTTOM = Color(0xFF006B1D)
private val ACTION_EDGE = listOf(Color(0xFFB5FF70), Color(0xFF20B843), Color(0xFF004A1D))

/** Full-screen night sky stays fixed beneath the two animated fireworks. */
@Composable
internal fun VictoryBackdrop(layout: VictoryLayout) {
    Canvas(Modifier.fillMaxSize().testTag("victoryBackdrop")) {
        drawRect(Brush.verticalGradient(listOf(SKY_TOP, SKY_BOTTOM)))
        val pixel = 2.dp.toPx()
        repeat(STAR_COUNT) { star ->
            val point = Offset(size.width * ((star * 37 % 97 + 1) / 100f),
                size.height * ((star * 19 % 55 + 2) / 100f))
            val bright = star % 3 == 0
            val color = if (bright) Color(0xFFFFCB54) else Color(0xFF327BD4)
            drawRect(color.copy(alpha = if (bright) .9f else .35f), point, Size(pixel, pixel))
            if (bright) {
                drawRect(color.copy(alpha = .6f), point - Offset(pixel, 0f), Size(pixel * STAR_CROSS_PIXELS, pixel))
                drawRect(color.copy(alpha = .6f), point - Offset(0f, pixel), Size(pixel, pixel * STAR_CROSS_PIXELS))
            }
        }
        val floorTop = size.height * (layout.artworkTop + layout.artworkHeight * SPRITE_BASE_FRACTION)
        val floorDepth = size.height * FLOOR_DEPTH_FRACTION
        repeat(FLOOR_LINES) { line ->
            val fraction = line / (FLOOR_LINES - 1f)
            val y = floorTop + floorDepth * fraction * fraction
            drawLine(FLOOR_COLOR.copy(alpha = .3f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        repeat(FLOOR_COLUMNS + 1) { column ->
            val x = size.width * (column / FLOOR_COLUMNS.toFloat() - .5f)
            drawLine(FLOOR_COLOR.copy(alpha = .3f), Offset(size.width / 2 + x * FLOOR_CONVERGENCE, floorTop),
                Offset(size.width / 2 + x, floorTop + floorDepth), 1.dp.toPx())
        }
    }
}

/** Fit the complete transparent trophy/fruit sprite; no fruit is lost to portrait cropping. */
@Composable
internal fun VictoryForeground(layout: VictoryLayout) {
    val artwork = ImageBitmap.imageResource(R.drawable.victory_foreground)
    Canvas(Modifier.fillMaxSize().testTag("victoryArtwork").semantics {
        contentDescription = "Кубок из блоков и восемь фруктов"
    }) {
        val artworkScale = minOf(size.width / artwork.width,
            size.height * layout.artworkHeight / artwork.height)
        val width = (artwork.width * artworkScale).roundToInt().coerceAtLeast(1)
        val height = (artwork.height * artworkScale).roundToInt().coerceAtLeast(1)
        val position = IntOffset(((size.width - width) / 2).roundToInt(),
            (size.height * layout.artworkTop).roundToInt())
        val baseline = position.y + height * SPRITE_BASE_FRACTION
        clipRect(top = baseline, bottom = baseline + size.height * FLOOR_DEPTH_FRACTION) {
            scale(1f, -1f, pivot = Offset(0f, baseline)) {
                drawImage(artwork, dstOffset = position, dstSize = IntSize(width, height),
                    alpha = .22f, filterQuality = FilterQuality.None)
            }
        }
        drawImage(artwork, dstOffset = position, dstSize = IntSize(width, height),
            filterQuality = FilterQuality.None)
    }
}

/** The approved celebration action has its own pixel bevel; ordinary app buttons keep their style. */
@Composable
internal fun VictoryActionButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = CutCornerShape(8.dp)
    Button(onClick, modifier.heightIn(min = ACTION_HEIGHT_DP.dp).shadow(ACTION_SHADOW_DP.dp, shape)
        .background(Brush.verticalGradient(0f to ACTION_HIGHLIGHT, .18f to ACTION_FACE,
            1f to ACTION_BOTTOM), shape), shape = shape,
        border = BorderStroke(3.dp, Brush.verticalGradient(ACTION_EDGE)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
