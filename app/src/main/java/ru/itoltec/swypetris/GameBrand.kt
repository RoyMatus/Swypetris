package ru.itoltec.swypetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Renders the shared logo at a size that leaves room for buttons on short screens. */
@Composable
internal fun GameTitle(imageModifier: Modifier = Modifier, wordmarkOnly: Boolean = false,
    heightLimit: Dp? = null) {
    val logoWidth = if (heightLimit == null)
        minOf(360f, LocalConfiguration.current.screenHeightDp * .45f).dp
    else minOf(420f, heightLimit.value * 1.5f).dp
    val logo = ImageBitmap.imageResource(R.drawable.swypetris_logo)
    val palette = LocalGamePalette.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.widthIn(max = logoWidth).fillMaxWidth().aspectRatio(1.5f)
            .testTag("gameLogo").semantics { contentDescription = "SWYPETRIS" }.then(imageModifier)) {
            drawBrandLogo(logo, Rect(Offset.Zero, size), palette, wordmarkOnly)
        }
    }
}

/** Uses the same PNG geometry and filtering in the menu and the intro's final frame. */
internal fun DrawScope.drawBrandLogo(logo: ImageBitmap, bounds: Rect, palette: GamePalette,
    wordmarkOnly: Boolean = false) {
    val scale = minOf(bounds.width / logo.width, bounds.height / logo.height)
    val origin = bounds.center - Offset(logo.width * scale / 2, logo.height * scale / 2)
    withTransform({ translate(origin.x, origin.y); scale(scale, scale, Offset.Zero) }) {
        if (wordmarkOnly) {
            val top = (logo.height * .69f).toInt()
            drawImage(logo, srcOffset = IntOffset(0, top),
                srcSize = IntSize(logo.width, logo.height - top), dstOffset = IntOffset(0, top),
                dstSize = IntSize(logo.width, logo.height - top))
        } else drawImage(logo, colorFilter = brandColorFilter(palette))
    }
}

/** Maps the original RGB logo channels to theme colors and makes its black matte transparent. */
internal fun brandColorFilter(palette: GamePalette, strength: Float = .5f): ColorFilter {
    fun tone(color: Color) = if (palette.light) lerp(color, palette.text, .35f) else color
    val red = tone(palette.piece(Tetromino.Z))
    val green = tone(palette.piece(Tetromino.S))
    val blue = tone(palette.piece(Tetromino.J))
    return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        red.red * strength, green.red * strength, blue.red * strength, 0f, 0f,
        red.green * strength, green.green * strength, blue.green * strength, 0f, 0f,
        red.blue * strength, green.blue * strength, blue.blue * strength, 0f, 0f,
        1f, 1f, 1f, 0f, 0f
    )))
}
