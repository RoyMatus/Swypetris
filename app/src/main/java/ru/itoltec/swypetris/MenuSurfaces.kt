package ru.itoltec.swypetris

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
internal fun Modifier.menuSurfaceArt(kind: String): Modifier {
    if (LocalMenuSkin.current == null) return this
    val context = LocalContext.current
    val id = menuDrawableId(kind)
    val drawable = remember(context, id) { checkNotNull(ContextCompat.getDrawable(context, id)).mutate() }
    val sourceDensity = context.resources.displayMetrics.density
    return drawBehind { drawMenuSurface(drawable, sourceDensity) }
}

/** Nine slices preserve the supplied paths; only the flat middle stretches. */
internal fun DrawScope.drawMenuSurface(drawable: Drawable, sourceDensity: Float) {
    val sourceW = drawable.intrinsicWidth.toFloat()
    val sourceH = drawable.intrinsicHeight.toFloat()
    if (sourceW <= 0 || sourceH <= 0) return
    if (size.width <= 0 || size.height <= 0) return
    val sourceCorner = minOf(12f * sourceDensity, sourceW / 2, sourceH / 2)
    val targetCorner = minOf(12.dp.toPx(), size.width / 2, size.height / 2)
    val sourceX = floatArrayOf(0f, sourceCorner, sourceW - sourceCorner, sourceW)
    val sourceY = floatArrayOf(0f, sourceCorner, sourceH - sourceCorner, sourceH)
    val targetX = floatArrayOf(0f, targetCorner, size.width - targetCorner, size.width)
    val targetY = floatArrayOf(0f, targetCorner, size.height - targetCorner, size.height)
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        drawable.setBounds(0, 0, sourceW.toInt(), sourceH.toInt())
        for (row in 0..2) for (column in 0..2) {
            val sw = sourceX[column + 1] - sourceX[column]
            val sh = sourceY[row + 1] - sourceY[row]
            if (sw <= 0 || sh <= 0) continue
            val checkpoint = native.save()
            native.clipRect(targetX[column], targetY[row], targetX[column + 1], targetY[row + 1])
            native.translate(targetX[column], targetY[row])
            native.scale((targetX[column + 1] - targetX[column]) / sw,
                (targetY[row + 1] - targetY[row]) / sh)
            native.translate(-sourceX[column], -sourceY[row])
            drawable.draw(native)
            native.restoreToCount(checkpoint)
        }
    }
}
