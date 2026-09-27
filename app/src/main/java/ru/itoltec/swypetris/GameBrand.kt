package ru.itoltec.swypetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Renders the shared logo at a size that leaves room for buttons on short screens. */
@Composable
internal fun GameTitle(imageModifier: Modifier = Modifier) {
    val logoWidth = minOf(360f, LocalConfiguration.current.screenHeightDp * .45f).dp
    val logo = ImageBitmap.imageResource(R.drawable.swypetris_logo)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.widthIn(max = logoWidth).fillMaxWidth().aspectRatio(1.5f)
            .testTag("gameLogo").semantics { contentDescription = "SWYPETRIS" }.then(imageModifier)) {
            drawBrandLogo(logo, Rect(Offset.Zero, size))
        }
    }
}

/** Uses the same PNG geometry and filtering in the menu and the intro's final frame. */
internal fun DrawScope.drawBrandLogo(logo: ImageBitmap, bounds: Rect) {
    val scale = minOf(bounds.width / logo.width, bounds.height / logo.height)
    val origin = bounds.center - Offset(logo.width * scale / 2, logo.height * scale / 2)
    withTransform({ translate(origin.x, origin.y); scale(scale, scale, Offset.Zero) }) { drawImage(logo) }
}
