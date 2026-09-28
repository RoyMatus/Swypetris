package ru.itoltec.swypetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource

/** Quiet, palette-driven Red Square setting behind the menu's interactive content. */
@Composable
internal fun ThemeBackdrop(palette: GamePalette) {
    if (!palette.light) {
        val artwork = ImageBitmap.imageResource(R.drawable.red_square_synthwave)
        Box(Modifier.fillMaxSize()) {
            Image(artwork, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = if (palette.id == "synthwave_84") null else artworkColorFilter(palette))
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(0f to Color.Transparent, .42f to Color.Transparent,
                    .6f to palette.background.copy(alpha = .3f),
                    1f to palette.background.copy(alpha = .65f), endY = size.height))
            }
        }
        return
    }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val horizon = h * .45f
        val strength = if (palette.light) .40f else .75f
        drawRect(Brush.verticalGradient(listOf(palette.artSky, palette.background,
            lerp(palette.background, palette.secondary, if (palette.light) .025f else .10f))))

        // Small deterministic points give the sky depth without bitmap assets or animation work.
        for (star in 0 until 22) {
            val x = w * ((star * 37 % 97) / 97f)
            val y = horizon * ((star * 53 % 89) / 120f)
            val side = if (star % 5 == 0) 2f else 1f
            drawRect(palette.text.copy(alpha = strength * if (star % 3 == 0) .32f else .15f),
                Offset(x, y), Size(side, side))
        }

        // The disc and skyline echo the retro-futuristic city without competing with the logo.
        val sunCenter = Offset(w * .5f, horizon * .84f)
        val sunRadius = minOf(w * .21f, h * .15f)
        drawCircle(Brush.verticalGradient(listOf(palette.artSun.copy(alpha = strength),
            palette.secondary.copy(alpha = strength * .45f)),
            sunCenter.y - sunRadius, sunCenter.y + sunRadius), sunRadius, sunCenter)
        for (band in 1..4) {
            val y = sunCenter.y + sunRadius * (.20f + band * .16f)
            val half = sunRadius * (1f - ((y - sunCenter.y) / sunRadius).let { it * it })
            drawLine(palette.background.copy(alpha = .70f), Offset(sunCenter.x - half, y),
                Offset(sunCenter.x + half, y), h * .003f * band)
        }
        drawKremlinSilhouette(palette, horizon, strength)

        val line = palette.accent.copy(alpha = strength * .30f)
        drawLine(line, Offset(0f, horizon), Offset(w, horizon), 1.4f)
        // A sparse perspective grid reads as reflected/digital ground at any aspect ratio.
        for (step in 1..7) {
            val t = step / 7f
            val y = horizon + (h - horizon) * t * t
            drawLine(line.copy(alpha = line.alpha * (1f - .35f * t)), Offset(0f, y), Offset(w, y), 1f)
        }
        for (ray in -5..5) {
            val x = w * (.5f + ray * .17f)
            drawLine(line, Offset(w * .5f, horizon), Offset(x, h), 1f)
        }
    }
}

/** Recolors the shared RGB neon artwork with each dark theme's semantic piece colors. */
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

/** Simplified wall and towers; the colored, sliced cathedral stays in the shared logo above. */
private fun DrawScope.drawKremlinSilhouette(palette: GamePalette, horizon: Float, strength: Float) {
    val w = size.width
    val wall = palette.piece(Tetromino.Z).copy(alpha = strength * .30f)
    val edge = palette.secondary.copy(alpha = strength * .65f)
    drawRect(wall, Offset(0f, horizon * .78f), Size(w * .27f, horizon * .22f))
    drawRect(wall, Offset(w * .73f, horizon * .78f), Size(w * .27f, horizon * .22f))
    for (side in listOf(.18f, .82f)) {
        val x = w * side
        val towerWidth = w * .065f
        drawRect(wall, Offset(x - towerWidth / 2, horizon * .57f),
            Size(towerWidth, horizon * .43f))
        val roof = Path().apply {
            moveTo(x - towerWidth * .72f, horizon * .59f)
            lineTo(x, horizon * .39f)
            lineTo(x + towerWidth * .72f, horizon * .59f)
            close()
        }
        drawPath(roof, edge)
        drawLine(edge, Offset(x, horizon * .37f), Offset(x, horizon * .26f), 1.3f)
    }
    for (index in 0..4) {
        val x = w * (index * .045f)
        drawRect(edge, Offset(x, horizon * .73f), Size(w * .017f, horizon * .075f))
        drawRect(edge, Offset(w - x - w * .017f, horizon * .73f),
            Size(w * .017f, horizon * .075f))
    }
}
