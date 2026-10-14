package ru.itoltec.swypetris

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/** Packaged-resource assertions; exported mask renders still require human visual acceptance. */
class MenuLauncherTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val exportEnabled get() =
        InstrumentationRegistry.getArguments().getString("menuThemeExport") == "true"

    @Test fun packagedArtworkRetainsApprovedOpaquePixels() {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_artwork)
        assertNotNull(bitmap)
        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
        assertFalse(bitmap.hasAlpha())
        // AAPT can recompress PNG bytes. This independent source-derived RGB digest checks every pixel.
        assertEquals("d1a666f744d5b8dff1a15944bc2cfdb8f01336eea408608cbce6688ad462bacd", pixelDigest(bitmap))
        if (exportEnabled) {
            export(bitmap, "packaged-artwork.png")
            File(exportDirectory(), "artwork-hashes.txt").writeText(
                "approved-source-sha256=$SOURCE_SHA256\n" +
                    "packaged-rgb-sha256=${pixelDigest(bitmap)}\n"
            )
        }
        bitmap.recycle()
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun adaptiveLauncherHasExactlyOneApprovedInsetAndSameArtwork() {
        listOf(R.drawable.ic_launcher, R.drawable.ic_launcher_round).forEach { resource ->
            val icon = context.getDrawable(resource) as AdaptiveIconDrawable
            assertEquals(Color.rgb(0, 21, 129), (icon.background as ColorDrawable).color)
            val inset = icon.foreground as InsetDrawable
            val artwork = inset.drawable as BitmapDrawable // A nested second inset fails this cast.
            assertEquals("d1a666f744d5b8dff1a15944bc2cfdb8f01336eea408608cbce6688ad462bacd",
                pixelDigest(artwork.bitmap))
            inset.setBounds(0, 0, 1080, 1080)
            val bounds = artwork.bounds
            // Fractional bounds are truncated by Android: tolerate at most one physical pixel.
            listOf(bounds.left, bounds.top).forEach { assertTrue(abs(it - 180) <= 1) }
            listOf(bounds.right, bounds.bottom).forEach { assertTrue(abs(it - 900) <= 1) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) assertNotNull(icon.monochrome)
        }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun installedNativeIconAndControlledMasksAreExportableAtSmallAndNormalSizes() {
        val icon = context.packageManager.getApplicationIcon(context.packageName) as AdaptiveIconDrawable
        listOf(48, 108).forEach { dp ->
            val size = (dp * context.resources.displayMetrics.density).roundToInt()
            icon.setBounds(0, 0, size, size)
            val native = render(size) { icon.draw(it) }
            assertEquals(255, Color.alpha(native.getPixel(size / 2, size / 2)))
            export(native, "native-system-mask-${dp}dp.png")
            native.recycle()
            exportControlledMasks(icon, size, dp)
        }
        if (exportEnabled) writeDeviceMetadata()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun exportControlledMasks(icon: AdaptiveIconDrawable, size: Int, dp: Int) {
        listOf("circle", "squircle", "rounded-square").forEach { name ->
            val path = mask(name, size.toFloat())
            // AdaptiveIconDrawable has already applied its public extra-inset contract to child bounds.
            // Reuse those bounds unchanged, replacing only the clipping path for controlled previews.
            val bitmap = render(size) { canvas ->
                canvas.clipPath(path)
                icon.background.draw(canvas)
                icon.foreground.draw(canvas)
            }
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            assertEquals(255, Color.alpha(bitmap.getPixel(size / 2, size / 2)))
            export(bitmap, "controlled-$name-${dp}dp.png")
            bitmap.recycle()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                exportMonochrome(icon, path, size, "$name-${dp}dp")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun exportMonochrome(icon: AdaptiveIconDrawable, mask: Path, size: Int, name: String) {
        val mono = requireNotNull(icon.monochrome).constantState!!.newDrawable().mutate()
        mono.bounds = Rect(icon.foreground.bounds)
        mono.setTint(Color.WHITE)
        val bitmap = render(size) { canvas ->
            canvas.clipPath(mask)
            canvas.drawColor(Color.rgb(0, 21, 129))
            mono.draw(canvas)
        }
        export(bitmap, "controlled-monochrome-white-on-blue-$name.png")
        bitmap.recycle()
    }

    private fun mask(name: String, size: Float) = Path().apply {
        val bounds = RectF(0f, 0f, size, size)
        when (name) {
            "circle" -> addOval(bounds, Path.Direction.CW)
            "rounded-square" -> addRoundRect(bounds, size / 5, size / 5, Path.Direction.CW)
            else -> {
                // Explicit fourth-power superellipse, a controlled squircle rather than an OEM mask.
                for (step in 0..360) {
                    val angle = step * Math.PI / 180
                    val cosine = kotlin.math.cos(angle)
                    val sine = kotlin.math.sin(angle)
                    val x = (size / 2 * (1 + cosine.sign * abs(cosine).pow(.5))).toFloat()
                    val y = (size / 2 * (1 + sine.sign * abs(sine).pow(.5))).toFloat()
                    if (step == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
    }

    private fun render(size: Int, draw: (Canvas) -> Unit): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }

    private fun pixelDigest(bitmap: Bitmap): String {
        val rgb = ByteArray(bitmap.width * bitmap.height * 3)
        var index = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            assertEquals(255, Color.alpha(pixel))
            rgb[index++] = Color.red(pixel).toByte()
            rgb[index++] = Color.green(pixel).toByte()
            rgb[index++] = Color.blue(pixel).toByte()
        }
        return digest(rgb)
    }

    private fun digest(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun exportDirectory() = File(context.getExternalFilesDir(null), "menu-launcher-307").apply { mkdirs() }

    private fun export(bitmap: Bitmap, name: String) {
        if (exportEnabled) File(exportDirectory(), name).outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun writeDeviceMetadata() {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        File(exportDirectory(), "device-and-renderer.txt").writeText(
            "package=${context.packageName}\nversionName=${info.versionName}\n" +
                "sdk=${Build.VERSION.SDK_INT}\nmodel=${Build.MODEL}\n" +
                "density=${context.resources.displayMetrics.density}\n" +
                "native-system-mask: PackageManager application icon rendered by AdaptiveIconDrawable.\n" +
                "controlled masks: child bounds from AdaptiveIconDrawable; explicit clip paths.\n" +
                "controlled monochrome: packaged monochrome, explicit white tint on blue; " +
                "not launcher-selected colors.\n" +
                "PNG export is diagnostic; readability and physical launcher acceptance require separate review.\n"
        )
    }

    private companion object {
        const val SOURCE_SHA256 = "2e1c7d64feb61a109bbf9c5156202c2f29cb7d7b0c0609271365c5b8e7fe3c16"
    }
}
