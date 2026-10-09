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
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import java.io.File
import java.io.IOException
import java.util.UUID

private const val POSTCARD_WIDTH = 1080
private const val POSTCARD_HEIGHT = 1440
private const val POSTCARD_TEXT_WIDTH = 960
private const val POSTCARD_GOLD = 0xFFFFD54F.toInt()
private const val POSTCARD_SHADE = 0xEE001020.toInt()
private const val POSTCARD_BACKGROUND = 0xFF001020.toInt()
private const val PNG_QUALITY = 100
private const val TEXT_SHADOW_RADIUS = 4f
private const val CAPTION_SIZE = 60f
private const val CAPTION_TOP = 45f
private const val TITLE_SIZE = 48f
private const val TITLE_TOP = 135f
private const val SCORE_LABEL_SIZE = 46f
private const val SCORE_LABEL_TOP = 1030f
private const val SCORE_SIZE = 88f
private const val SCORE_TOP = 1100f
private const val NAME_SIZE = 58f
private const val NAME_TOP = 1225f
private const val ARTWORK_TOP = 260

/** Only the postcard cache directory is exposed; other app files remain private. */
class PostcardProvider : FileProvider()

/** A fixed-size image is independent of device size, animation phase, and screenshot permissions. */
internal object AbsolutePostcard {
    fun create(context: Context, name: String): Bitmap {
        require(name.isNotBlank() && name.length <= MAX_PLAYER_NAME_LENGTH)
        val result = createBitmap(POSTCARD_WIDTH, POSTCARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(POSTCARD_BACKGROUND)
        val artwork = BitmapFactory.decodeResource(context.resources, R.drawable.victory_trophy)
        try {
            val height = artwork.height * POSTCARD_WIDTH / artwork.width
            canvas.drawBitmap(artwork, null, Rect(0, ARTWORK_TOP, POSTCARD_WIDTH, ARTWORK_TOP + height), Paint())
        } finally { artwork.recycle() }
        val shade = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, POSTCARD_HEIGHT.toFloat(),
                intArrayOf(POSTCARD_SHADE, Color.TRANSPARENT, Color.TRANSPARENT, POSTCARD_SHADE),
                floatArrayOf(0f, .25f, .60f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, POSTCARD_WIDTH.toFloat(), POSTCARD_HEIGHT.toFloat(), shade)
        text(canvas, "Я прошёл Swypetris!", CAPTION_SIZE, CAPTION_TOP, POSTCARD_GOLD)
        text(canvas, "АБСОЛЮТНЫЙ ПОБЕДИТЕЛЬ", TITLE_SIZE, TITLE_TOP, POSTCARD_GOLD)
        text(canvas, "Максимальный счёт", SCORE_LABEL_SIZE, SCORE_LABEL_TOP, Color.WHITE)
        text(canvas, "999 999", SCORE_SIZE, SCORE_TOP, POSTCARD_GOLD)
        text(canvas, name.trim(), NAME_SIZE, NAME_TOP, Color.WHITE)
        return result
    }

    private fun text(canvas: Canvas, value: String, size: Float, top: Float, color: Int) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(TEXT_SHADOW_RADIUS, 1f, 2f, Color.BLACK)
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, POSTCARD_TEXT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
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
