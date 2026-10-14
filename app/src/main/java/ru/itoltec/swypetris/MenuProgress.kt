package ru.itoltec.swypetris

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
internal fun MenuProgressIndicator(progress: Float, modifier: Modifier = Modifier) {
    val skin = LocalMenuSkin.current
    if (skin == null) {
        LinearProgressIndicator(progress = { progress }, modifier = modifier)
        return
    }
    val context = LocalContext.current
    val id = menuDrawableId("progress")
    val base = remember(context, id) {
        disabledMenuDrawable(context.resources, id, 1f, 1f, hideActiveSegment = true)
    }
    val sourceDensity = context.resources.displayMetrics.density
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fraction = progress.coerceIn(0f, 1f)
    Canvas(modifier.progressSemantics(fraction).fillMaxWidth().height(12.dp)) {
        drawMenuSurface(base, sourceDensity)
        val inset = 6.dp.toPx().coerceAtMost(size.width / 2)
        val start = if (rtl) size.width - inset else inset
        val end = start + (size.width - 2 * inset) * fraction * if (rtl) -1f else 1f
        if (fraction > 0f) drawLine(skin.control, Offset(start, size.height / 2),
            Offset(end, size.height / 2), strokeWidth = 4.dp.toPx())
    }
}
