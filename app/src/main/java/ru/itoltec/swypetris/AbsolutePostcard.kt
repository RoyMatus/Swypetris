package ru.itoltec.swypetris

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.File
import java.io.IOException
import java.util.UUID

private const val POSTCARD_WIDTH = 1080
private const val POSTCARD_HEIGHT = 1440
private const val POSTCARD_TEXT_WIDTH = 960
private const val POSTCARD_GOLD = 0xFFFFD54F.toInt()
private const val PNG_QUALITY = 100
private const val TEXT_SHADOW_RADIUS = 4f
private const val CAPTION_SIZE = 60f
private const val CAPTION_TOP = 55f
private const val TITLE_SIZE = 78f
private const val TITLE_TOP = 265f
private const val SCORE_LABEL_SIZE = 46f
private const val SCORE_LABEL_TOP = 1230f
private const val SCORE_SIZE = 88f
private const val SCORE_TOP = 1290f
private const val NAME_SIZE = 96f
private const val NAME_TOP = 125f
private const val NAME_HEIGHT = 135f
private const val TITLE_HEIGHT = 180f
private const val FONT_SHRINK_FACTOR = .9f
private const val MIN_POSTCARD_FONT = 24f
private const val POSTCARD_DENSITY = 3f
private const val STATIC_POSTCARD_MILLIS = 2400L
private const val FRAME_INSET = 14f
private const val FRAME_STROKE = 10f
private const val FRAME_CORNER = 24f
private const val POSTCARD_DARK_GOLD = 0xFFED9700.toInt()
private val POSTCARD_LAYOUT = VictoryLayout(.32f, .52f, .0f, false)
private val POSTCARD_BURSTS = listOf(Offset(.15f, .51f), Offset(.84f, .51f), Offset(.85f, .69f))

/** Only the postcard cache directory is exposed; other app files remain private. */
class PostcardProvider : FileProvider()

/** A fixed-size image is independent of device size, animation phase, and screenshot permissions. */
internal object AbsolutePostcard {
    fun create(context: Context, name: String): Bitmap {
        require(name.isNotBlank() && name.length <= MAX_PLAYER_NAME_LENGTH)
        val result = createBitmap(POSTCARD_WIDTH, POSTCARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val artwork = BitmapFactory.decodeResource(context.resources, R.drawable.victory_foreground)
        try {
            CanvasDrawScope().draw(Density(POSTCARD_DENSITY), LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(canvas), Size(POSTCARD_WIDTH.toFloat(), POSTCARD_HEIGHT.toFloat())) {
                drawVictoryBackdrop(POSTCARD_LAYOUT)
                drawVictoryFireworks(STATIC_POSTCARD_MILLIS, VictoryCelebration.POSTCARD,
                    POSTCARD_BURSTS.map { Offset(it.x * size.width, it.y * size.height) })
                drawVictoryForeground(artwork.asImageBitmap(), POSTCARD_LAYOUT)
            }
        } finally { artwork.recycle() }
        frame(canvas)
        text(canvas, "Я прошёл Swypetris!", CAPTION_SIZE, CAPTION_TOP, Color.WHITE)
        text(canvas, name.trim(), NAME_SIZE, NAME_TOP, POSTCARD_GOLD, NAME_HEIGHT)
        text(canvas, "АБСОЛЮТНЫЙ\nПОБЕДИТЕЛЬ", TITLE_SIZE, TITLE_TOP, POSTCARD_GOLD, TITLE_HEIGHT)
        text(canvas, "Максимальный счёт", SCORE_LABEL_SIZE, SCORE_LABEL_TOP, Color.WHITE)
        text(canvas, "999 999", SCORE_SIZE, SCORE_TOP, POSTCARD_GOLD)
        return result
    }

    private fun frame(canvas: Canvas) {
        val paint = Paint().apply { color = POSTCARD_GOLD; strokeWidth = FRAME_STROKE; style = Paint.Style.STROKE }
        canvas.drawRect(FRAME_INSET, FRAME_INSET, POSTCARD_WIDTH - FRAME_INSET, POSTCARD_HEIGHT - FRAME_INSET, paint)
        paint.style = Paint.Style.FILL
        for (x in listOf(FRAME_INSET, POSTCARD_WIDTH - FRAME_INSET - FRAME_CORNER)) {
            for (y in listOf(FRAME_INSET, POSTCARD_HEIGHT - FRAME_INSET - FRAME_CORNER)) {
                canvas.drawRect(x, y, x + FRAME_CORNER, y + FRAME_CORNER, paint)
                paint.color = Color.WHITE
                canvas.drawRect(x, y, x + FRAME_STROKE, y + FRAME_STROKE, paint)
                paint.color = POSTCARD_GOLD
            }
        }
    }

    private fun text(canvas: Canvas, value: String, size: Float, top: Float, color: Int,
        maxHeight: Float = TITLE_HEIGHT) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(TEXT_SHADOW_RADIUS, 1f, 2f, Color.BLACK)
        }
        fun measureLayout() = StaticLayout.Builder.obtain(value, 0, value.length, paint, POSTCARD_TEXT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        var layout = measureLayout()
        while (layout.height > maxHeight && paint.textSize > MIN_POSTCARD_FONT) {
            paint.textSize = (paint.textSize * FONT_SHRINK_FACTOR).coerceAtLeast(MIN_POSTCARD_FONT)
            layout = measureLayout()
        }
        if (color == POSTCARD_GOLD) paint.shader = LinearGradient(0f, 0f, 0f, layout.height.toFloat(),
            intArrayOf(Color.WHITE, POSTCARD_GOLD, POSTCARD_DARK_GOLD), null, Shader.TileMode.CLAMP)
        canvas.withTranslation((POSTCARD_WIDTH - POSTCARD_TEXT_WIDTH) / 2f, top) { layout.draw(this) }
    }

    /** Share the same bitmap as the preview, with a read-only grant for a narrowly scoped content URI. */
    fun shareIntent(context: Context, bitmap: Bitmap): Intent {
        val directory = File(context.cacheDir, "postcards")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create postcard cache")
        val file = File(directory, "${UUID.randomUUID()}.png")
        try {
            write(file, bitmap)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.postcards", file)
            return Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Swypetris postcard", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (error: IOException) {
            if (file.exists() && !file.delete()) error.addSuppressed(IOException("Cannot remove partial postcard"))
            throw error
        }
    }

    private fun write(file: File, bitmap: Bitmap) {
        file.outputStream().use {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it))
                throw IOException("Cannot encode postcard")
        }
    }
}
