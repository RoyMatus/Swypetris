package ru.itoltec.swypetris

import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser
import org.xmlpull.v1.XmlPullParser
import kotlin.math.roundToInt

private const val VECTOR_ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
private const val VECTOR_ALPHA_MAX = 255

private data class MenuVectorPath(val path: Path, val fill: Paint, val stroke: Paint)

/** Cached renderer for the handoff's flat vector paths; no plaintext framework inflation. */
internal fun readMenuVector(resources: Resources, id: Int, fillAlpha: Float,
    strokeAlpha: Float, hideActiveSegment: Boolean): Drawable {
    var viewportWidth = 0f
    var viewportHeight = 0f
    val paths = mutableListOf<MenuVectorPath>()
    resources.getXml(id).use { reader ->
        var event = reader.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) when (reader.name) {
                "vector" -> {
                    viewportWidth = reader.floatAttribute("viewportWidth", 0f)
                    viewportHeight = reader.floatAttribute("viewportHeight", 0f)
                }
                "path" -> {
                    val hidden = hideActiveSegment && paths.isNotEmpty()
                    paths += reader.menuVectorPath(resources,
                        if (hidden) 0f else fillAlpha, if (hidden) 0f else strokeAlpha)
                }
                else -> error("Unsupported menu vector element: ${reader.name}")
            }
            event = reader.next()
        }
    }
    require(viewportWidth > 0 && viewportHeight > 0)
    return MenuVectorDrawable(viewportWidth, viewportHeight, resources.displayMetrics.density, paths)
}

private fun XmlResourceParser.floatAttribute(name: String, fallback: Float): Float =
    getAttributeFloatValue(VECTOR_ANDROID_NAMESPACE, name, fallback)

private fun XmlResourceParser.menuVectorPath(resources: Resources,
    fillAlpha: Float, strokeAlpha: Float): MenuVectorPath {
    val path = checkNotNull(PathParser.createPathFromPathData(getAttributeValue(VECTOR_ANDROID_NAMESPACE, "pathData")))
    path.fillType = if (getAttributeIntValue(VECTOR_ANDROID_NAMESPACE, "fillType", 0) == 1)
        Path.FillType.EVEN_ODD else Path.FillType.WINDING
    val fill = menuPathPaint(resources, "fillColor", "fillAlpha", fillAlpha).apply { style = Paint.Style.FILL }
    val stroke = menuPathPaint(resources, "strokeColor", "strokeAlpha", strokeAlpha).apply {
        style = Paint.Style.STROKE
        strokeWidth = floatAttribute("strokeWidth", 0f)
        strokeMiter = floatAttribute("strokeMiterLimit", 4f)
        strokeCap = when (getAttributeIntValue(VECTOR_ANDROID_NAMESPACE, "strokeLineCap", 0)) {
            1 -> Paint.Cap.ROUND
            2 -> Paint.Cap.SQUARE
            else -> Paint.Cap.BUTT
        }
        strokeJoin = when (getAttributeIntValue(VECTOR_ANDROID_NAMESPACE, "strokeLineJoin", 0)) {
            1 -> Paint.Join.ROUND
            2 -> Paint.Join.BEVEL
            else -> Paint.Join.MITER
        }
    }
    return MenuVectorPath(path, fill, stroke)
}

private fun XmlResourceParser.menuPathPaint(resources: Resources, colorAttribute: String, alphaAttribute: String,
    multiplier: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    val resourceId = getAttributeResourceValue(VECTOR_ANDROID_NAMESPACE, colorAttribute, 0)
    color = if (resourceId != 0) resources.getColor(resourceId, null)
        else getAttributeIntValue(VECTOR_ANDROID_NAMESPACE, colorAttribute, Color.TRANSPARENT)
    alpha = (Color.alpha(color) * floatAttribute(alphaAttribute, 1f) * multiplier)
        .roundToInt().coerceIn(0, VECTOR_ALPHA_MAX)
}

private class MenuVectorDrawable(private val width: Float, private val height: Float,
    private val resourceDensity: Float, private val paths: List<MenuVectorPath>) : Drawable() {
    private val initialAlphas = paths.map { it.fill.alpha to it.stroke.alpha }
    override fun getIntrinsicWidth(): Int = (width * resourceDensity).roundToInt()
    override fun getIntrinsicHeight(): Int = (height * resourceDensity).roundToInt()

    override fun draw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / width, bounds.height() / height)
        paths.forEach {
            if (it.fill.alpha > 0) canvas.drawPath(it.path, it.fill)
            if (it.stroke.alpha > 0 && it.stroke.strokeWidth > 0) canvas.drawPath(it.path, it.stroke)
        }
        canvas.restoreToCount(checkpoint)
    }

    override fun setAlpha(alpha: Int) {
        paths.forEachIndexed { index, item ->
            item.fill.alpha = initialAlphas[index].first * alpha / VECTOR_ALPHA_MAX
            item.stroke.alpha = initialAlphas[index].second * alpha / VECTOR_ALPHA_MAX
        }
        invalidateSelf()
    }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paths.forEach { it.fill.colorFilter = colorFilter; it.stroke.colorFilter = colorFilter }
        invalidateSelf()
    }
    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
