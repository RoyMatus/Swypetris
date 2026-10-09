package ru.itoltec.swypetris

import android.content.ContentResolver
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID

private const val MAX_BACKGROUND_BYTES = 16L * 1024 * 1024
private const val MAX_SAVED_BACKGROUND_BYTES = 32L * 1024 * 1024
private const val MAX_BACKGROUND_EDGE = 2048
private const val MAX_SOURCE_EDGE = 32768
private const val COPY_BUFFER_BYTES = 8192
private const val PNG_QUALITY = 100
private const val QUARTER_TURN = 90f
private const val HALF_TURN = 180f
private const val THREE_QUARTER_TURN = 270f

internal data class BackgroundImage(val file: File, val bitmap: Bitmap)

/** Cleanup is best-effort: a failed deletion must not invalidate a successfully saved background. */
internal fun deleteBackgroundCopy(file: File) {
    if (file.exists() && !file.delete()) android.util.Log.w("CustomBackground", "Cannot delete unused private image")
}

/** App-private normalized copies need neither storage permissions nor persistent provider grants. */
internal class BackgroundStore(private val directory: File, private val preferences: SharedPreferences,
    private val stagingDirectory: File = File(directory, "pending")) {
    val enabled: Boolean get() = preferences.getBoolean("background_enabled", false)
    val crop: BackgroundCrop get() = BackgroundCrop(preferences.getFloat("background_zoom", 1f),
        preferences.getFloat("background_x", .5f), preferences.getFloat("background_y", .5f)).normalized()

    fun setEnabled(value: Boolean) { preferences.edit().putBoolean("background_enabled", value).apply() }

    /** Bounds and pixel sampling limit untrusted input before allocating bitmap memory. */
    fun importImage(resolver: ContentResolver, uri: Uri): BackgroundImage {
        check(stagingDirectory.isDirectory || stagingDirectory.mkdirs()) { "Cannot create background directory" }
        val input = File.createTempFile("import-", ".tmp", stagingDirectory)
        try {
            copyInput(resolver, uri, input)
            val bitmap = decode(input) ?: throw IOException("Unsupported background image")
            val normalized = orient(bitmap, input)
            val saved = File(stagingDirectory, "${UUID.randomUUID()}.png")
            var completed = false
            try {
                saved.outputStream().use { check(normalized.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)) }
                completed = true
                return BackgroundImage(saved, normalized)
            } finally { if (!completed) deleteBackgroundCopy(saved) }
        } finally { deleteBackgroundCopy(input) }
    }

    private fun copyInput(resolver: ContentResolver, uri: Uri, destination: File) {
        resolver.openInputStream(uri)?.use { source ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                var total = 0L
                var count = source.read(buffer)
                while (count != -1) {
                    total += count
                    if (total > MAX_BACKGROUND_BYTES) throw IOException("Background image is too large")
                    output.write(buffer, 0, count)
                    count = source.read(buffer)
                }
            }
        } ?: throw IOException("Background image is unavailable")
    }

    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth !in 1..MAX_SOURCE_EDGE || bounds.outHeight !in 1..MAX_SOURCE_EDGE) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_BACKGROUND_EDGE) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun orient(bitmap: Bitmap, file: File): Bitmap {
        val orientation = try {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: IOException) { ExifInterface.ORIENTATION_NORMAL }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(HALF_TURN)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(QUARTER_TURN); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(QUARTER_TURN)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(THREE_QUARTER_TURN); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(THREE_QUARTER_TURN)
        }
        return if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0,
            bitmap.width, bitmap.height, matrix, true).also { bitmap.recycle() }
    }

    /** Missing backup content or a corrupt copy safely restores the normal background. */
    fun restore(): BackgroundImage? {
        val name = preferences.getString("background_file", null) ?: return null
        val file = File(directory, name)
        return if (name.matches(Regex("[a-f0-9-]{36}\\.png")) && file.isFile &&
            file.length() <= MAX_SAVED_BACKGROUND_BYTES) decode(file)?.let { BackgroundImage(file, it) } else null
    }

    /** Commit references only after the new private image exists; retain the old copy on failure. */
    fun save(image: BackgroundImage, crop: BackgroundCrop): BackgroundImage {
        val old = preferences.getString("background_file", null)
        val normalized = crop.normalized()
        check(directory.isDirectory || directory.mkdirs())
        val saved = File(directory, "${UUID.randomUUID()}.png")
        try {
            image.file.copyTo(saved)
            check(preferences.edit().putString("background_file", saved.name)
                .putFloat("background_zoom", normalized.zoom).putFloat("background_x", normalized.centerX)
                .putFloat("background_y", normalized.centerY).putBoolean("background_enabled", true).commit())
        } catch (error: IOException) { deleteBackgroundCopy(saved); throw error }
        catch (error: IllegalStateException) { deleteBackgroundCopy(saved); throw error }
        if (old?.matches(Regex("[a-f0-9-]{36}\\.png")) == true)
            deleteBackgroundCopy(File(directory, old))
        deleteBackgroundCopy(image.file)
        return image.copy(file = saved)
    }
}
