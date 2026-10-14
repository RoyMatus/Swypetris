package ru.itoltec.swypetris

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import java.io.File

class MenuSkyTest {
    @get:Rule val compose = createComposeRule()
    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val exportDiagnostics: Boolean get() =
        InstrumentationRegistry.getArguments().getString("menuThemeExport") == "true"

    @Test fun authoredThemesHaveExactTracksFinitePosesAndRegisteredAtlasFrames() {
        val counts = listOf(8, 13, 8, 6, 5, 38, 3, 8, 4, 46, 7, 6)
        val ids = listOf("classic", "monokai", "gruvbox", "vscode", "dracula", "nord",
            "solarized_light", "solarized_dark", "github_light", "tokyo_night", "catppuccin", "synthwave_84")
        ids.zip(counts).forEach { (id, count) ->
            val scene = loadMenuScene(resources, id)
            assertEquals(id, count, scene.tracks.size)
            scene.tracks.forEach { artwork ->
                val track = artwork.track
                (listOf(0.0, 3.1, 8.0, 14.2, 26.0, 60.0) +
                    listOf(track.period - track.phase, track.period - track.phase - .00001)).forEach { time ->
                    val pose = sampleMenuMotion(track, time)
                    assertEquals(pose, sampleMenuMotion(track, time))
                    assertTrue(listOf(pose.x, pose.y, pose.scaleX, pose.scaleY, pose.rotation, pose.alpha)
                        .all { it.isFinite() })
                    assertTrue(pose.alpha in 0f..1f)
                    if (track.sprite != null) assertTrue(pose.index in artwork.spriteFrames.indices)
                }
            }
        }
        val cat = loadMenuScene(resources, "catppuccin").tracks.first { it.track.sprite == "cat" }
        assertEquals(listOf(320f, 316f, 319f, 316f), cat.spriteFrames.map { it.anchorX })
        assertEquals(listOf(475f, 475f, 449f, 449f), cat.spriteFrames.map { it.anchorY })
        assertEquals(listOf(MenuFrameEvent(23.0, 3.0, .35, 1), MenuFrameEvent(29.0, 14.0, .55, 2)),
            cat.track.frameEvents)
        assertEquals(1, sampleMenuMotion(cat.track, 3.1).index)
        assertEquals(2, sampleMenuMotion(cat.track, 14.2).index)
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun settledClassicPlateUsesExactSharedCoverAcrossPortraitRatios() {
        var ratio by mutableStateOf(16.0)
        var oracle by mutableStateOf(false)
        val plate = loadMenuPlate(resources, "classic")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(360.dp, (360 * ratio / 9).toFloat().dp).testTag("sky")) {
                    if (oracle) AuthoredPlateOracle(plate, Modifier.matchParentSize())
                    else MenuSkyArtwork(GamePalettes.find("classic"), 1f) { 0.0 }
                }
            }
        }
        for (format in listOf(16.0, 19.5, 20.0)) {
            compose.runOnIdle { ratio = format; oracle = false }
            val actual = capture()
            compose.runOnIdle { oracle = true }
            val gpu = capture()
            val software = if (exportDiagnostics) softwareOracle(plate, actual.width, actual.height) else null
            assertPixelsClose(gpu, actual, "cover-classic-$format", software)
        }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun allThemeHeadersUseStaticAuthoredCropIncludingShortSolarizedPlate() {
        var theme by mutableStateOf("classic")
        var oracle by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val modifier = Modifier.requiredSize(360.dp, 130.dp).testTag("sky")
                if (oracle) AuthoredPlateOracle(loadMenuPlate(resources, theme), modifier, header = true)
                else MenuSceneHeader(GamePalettes.find(theme), modifier)
            }
        }
        GamePalettes.all.forEach { palette ->
            compose.runOnIdle { theme = palette.id; oracle = false }
            val actual = capture()
            compose.mainClock.advanceTimeBy(1000)
            assertTrue("Static header changed: ${palette.id}", actual.sameAs(capture()))
            compose.runOnIdle { oracle = true }
            val gpu = capture()
            val software = if (exportDiagnostics) softwareOracle(loadMenuPlate(resources, palette.id), actual.width,
                actual.height, header = true) else null
            assertPixelsClose(gpu, actual, "header-${palette.id}", software)
        }
    }

    @Test fun clockFreezesWhileBackgroundedResumesWithoutJumpAndResetsOnThemeOrDisabledMotion() {
        compose.mainClock.autoAdvance = false
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        var enabled by mutableStateOf(true)
        var theme by mutableStateOf("classic")
        var visible by mutableStateOf(true)
        lateinit var time: State<Double>
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            if (visible) time = rememberMenuSkyTime(enabled, theme) } }
        compose.mainClock.advanceTimeBy(1000)
        var active = 0.0
        compose.runOnIdle { active = time.value; assertTrue(active > .5)
            owner.registry.currentState = Lifecycle.State.CREATED }
        compose.mainClock.advanceTimeBy(10000)
        compose.runOnIdle { assertEquals(active, time.value, .00001)
            owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertTrue(time.value - active in 0.01..0.15); theme = "dracula" }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertTrue(time.value < .05); enabled = false }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(5000)
        compose.runOnIdle { assertEquals(0.0, time.value, .00001) }
        compose.runOnIdle { enabled = true }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { active = time.value; assertTrue(active > .3); visible = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { active = time.value }
        compose.mainClock.advanceTimeBy(5000)
        compose.runOnIdle { assertEquals(active, time.value, .00001) }
    }

    @androidx.annotation.RequiresApi(26)
    private fun capture(): Bitmap = compose.onNodeWithTag("sky").captureToImage().asAndroidBitmap()

    /** Independent inverse crop, rendered through the same GPU/filter/capture path as production. */
    @Composable
    private fun AuthoredPlateOracle(plate: Bitmap, modifier: Modifier, header: Boolean = false) {
        Canvas(modifier) {
            val matrix = authoredCropMatrix(plate, size.width, size.height, header)
            drawIntoCanvas {
                it.nativeCanvas.drawBitmap(plate, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
        }
    }

    private fun authoredCropMatrix(plate: Bitmap, width: Float, height: Float, header: Boolean): Matrix {
        val aspect = width / height
        val sourceHeight = if (header) 620f else 1672f
        val cropWidth = minOf(941f, sourceHeight * aspect)
        val cropHeight = minOf(sourceHeight, 941f / aspect)
        val left = (941f - cropWidth) / 2f
        val top = (sourceHeight - cropHeight) / 2f + if (header) 40f else 0f
        val source = RectF(left * plate.width / 941f, top * plate.height / 1672f,
            (left + cropWidth) * plate.width / 941f, (top + cropHeight) * plate.height / 1672f)
        return Matrix().apply {
            setRectToRect(source, RectF(0f, 0f, width, height), Matrix.ScaleToFit.FILL)
        }
    }

    private fun softwareOracle(plate: Bitmap, width: Int, height: Int, header: Boolean = false): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            android.graphics.Canvas(it).drawBitmap(plate, authoredCropMatrix(plate, width.toFloat(),
                height.toFloat(), header), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }

    private fun assertPixelsClose(expected: Bitmap, actual: Bitmap, label: String, software: Bitmap?) {
        var mismatches = 0
        var maxError = 0
        var errorSum = 0L
        var softwareMismatches = 0
        var softwareMaxError = 0
        for (y in 0 until actual.height) for (x in 0 until actual.width) {
            val a = actual.getPixel(x, y)
            val b = expected.getPixel(x, y)
            val errors = listOf(0, 8, 16).map { abs((a shr it and 255) - (b shr it and 255)) }
            maxError = maxOf(maxError, errors.max())
            errorSum += errors.sum()
            if (errors.any { it > 3 }) mismatches++
            software?.let {
                val s = it.getPixel(x, y)
                val softwareErrors = listOf(0, 8, 16).map { channel ->
                    abs((a shr channel and 255) - (s shr channel and 255)) }
                softwareMaxError = maxOf(softwareMaxError, softwareErrors.max())
                if (softwareErrors.any { error -> error > 3 }) softwareMismatches++
            }
        }
        if (mismatches >= actual.width * actual.height / 100 || exportDiagnostics) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.getExternalFilesDir(null), "menu-sky-307-failures").apply { mkdirs() }
            val images = mutableListOf("actual" to actual, "expected-gpu" to expected)
            if (software != null) images += "oracle-software" to software
            images.forEach { (name, image) ->
                File(directory, "$label-$name.png").outputStream().use {
                    assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
            File(directory, "$label-pixel-stats.txt").writeText(
                "width=${actual.width}\nheight=${actual.height}\nmismatches=$mismatches\nmaxChannelError=$maxError\n" +
                    "meanChannelError=${errorSum.toDouble() / (actual.width * actual.height * 3)}\n" +
                    "softwareMismatches=$softwareMismatches\nsoftwareMaxChannelError=$softwareMaxError\n")
        }
        assertTrue("$label plate/crop mismatch: $mismatches pixels,maxChannelError=$maxError",
            mismatches < actual.width * actual.height / 100)
    }
}
