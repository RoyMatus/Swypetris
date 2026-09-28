package ru.itoltec.swypetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource

/** The reference Red Square composition has matching night and day lighting. */
@Composable
internal fun ThemeBackdrop(palette: GamePalette, approach: Float = 1f) {
    val artwork = ImageBitmap.imageResource(
        if (palette.light) R.drawable.red_square_day else R.drawable.red_square_synthwave)
    Box(Modifier.fillMaxSize()) {
        Image(artwork, contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = 1f + .08f * approach
            scaleY = scaleX
            transformOrigin = TransformOrigin(.5f, .28f)
        }, contentScale = ContentScale.Crop,
            colorFilter = if (palette.light || palette.id == "classic" || palette.id == "synthwave_84") null else artworkColorFilter(palette))
        Canvas(Modifier.fillMaxSize()) {
            val travelAlpha = (1f - approach) * .35f
            if (travelAlpha > 0f) {
                val horizon = size.height * .50f
                for (line in 0..9) {
                    val depth = (line / 10f + approach * .75f) % 1f
                    val y = horizon + (size.height - horizon) * depth * depth
                    drawLine(palette.accent.copy(alpha = travelAlpha * depth),
                        Offset(0f, y), Offset(size.width, y), 1f + depth * 2f)
                }
            }
            drawRect(Brush.verticalGradient(0f to Color.Transparent, .50f to Color.Transparent,
                .68f to palette.background.copy(alpha = if (palette.light) .08f else .16f),
                1f to palette.background.copy(alpha = if (palette.light) .28f else .48f),
                endY = size.height))
        }
    }
}

/** Recolors RGB neon artwork with the selected dark theme's semantic piece colors. */
private fun artworkColorFilter(palette: GamePalette): ColorFilter {
    val red = palette.piece(Tetromino.Z)
    val green = palette.piece(Tetromino.S)
    val blue = palette.piece(Tetromino.J)
    return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        red.red * .6f, green.red * .6f, blue.red * .6f, 0f, 0f,
        red.green * .6f, green.green * .6f, blue.green * .6f, 0f, 0f,
        red.blue * .6f, green.blue * .6f, blue.blue * .6f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )))
}
