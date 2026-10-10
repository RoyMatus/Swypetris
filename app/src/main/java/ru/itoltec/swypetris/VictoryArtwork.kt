package ru.itoltec.swypetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val STAR_COUNT = 48
private const val STAR_CROSS_PIXELS = 3
private val SKY_TOP = Color(0xFF001333)
private val SKY_BOTTOM = Color(0xFF001020)

/** Full-screen night sky stays fixed beneath the two animated fireworks. */
@Composable
internal fun VictoryBackdrop() {
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
    }
}

/** Fit the complete transparent trophy/fruit sprite; no fruit is lost to portrait cropping. */
@Composable
internal fun VictoryForeground(layout: VictoryLayout) {
    val artwork = ImageBitmap.imageResource(R.drawable.victory_foreground)
    Canvas(Modifier.fillMaxSize().testTag("victoryArtwork").semantics {
        contentDescription = "Кубок из блоков и восемь фруктов"
    }) {
        val scale = minOf(size.width / artwork.width,
            size.height * layout.artworkHeight / artwork.height)
        val width = (artwork.width * scale).roundToInt().coerceAtLeast(1)
        val height = (artwork.height * scale).roundToInt().coerceAtLeast(1)
        drawImage(artwork, dstOffset = IntOffset(((size.width - width) / 2).roundToInt(),
            (size.height * layout.artworkTop).roundToInt()), dstSize = IntSize(width, height),
            filterQuality = FilterQuality.None)
    }
}
