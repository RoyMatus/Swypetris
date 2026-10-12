package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Native portrait evidence also checks transparent score corners and distinct textured pigments. */
@SdkSuppress(minSdkVersion = 26)
class MaterialRenderingTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun allMaterialsAndFieldsAtPortraitSizesPreserveSevenPigmentsAndScore() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val state = materialState()
        val model = GameViewModel(app, state, { 1000L }, false)
        model.options.setHints(true)
        var palette by mutableStateOf(GamePalettes.find("classic"))
        var viewport by mutableStateOf(DpSize(240.dp, 580.dp))
        var font by mutableStateOf(1f)
        var material: BlockMaterial? = null
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport) then
                DeviceConfigurationOverride.FontScale(font)) {
                val prepared = blockMaterial(palette)
                material = prepared
                CompositionLocalProvider(LocalGamePalette provides palette, LocalBlockMaterial provides prepared) {
                    Box(Modifier.fillMaxSize().testTag("materialViewport")) { GameContent(model, state, 24.dp) }
                }
            }
        }
        for ((size, scale) in listOf(DpSize(240.dp, 580.dp) to 2f, DpSize(393.dp, 873.dp) to 1f)) {
            for (theme in GamePalettes.all) {
                compose.runOnIdle { viewport = size; font = scale; palette = theme }
                compose.onNodeWithTag("themedBackground").assertIsDisplayed()
                val image = compose.onNodeWithTag("materialViewport").captureToImage()
                val pixels = image.toPixelMap()
                assertScore(theme, compose.onNodeWithTag("score").captureToImage().toPixelMap())
                val geometry = gameplayGeometry(pixels.height.toFloat(), pixels.height * 24f / size.height.value)
                val y = (geometry.gridTop + geometry.cellHeight * 20.5f).toInt()
                val pigments = (0..6).map { column -> pixels[(pixels.width * (column + .5f) / 10).toInt(), y] }
                assertEquals("${theme.id}: seven pigments remain distinct", 7, pigments.toSet().size)
                assertEquals(state, model.game)
                export("game-${theme.id}-${size.width.value.toInt()}-font$scale", image.asAndroidBitmap())
                measureIfRequested(theme, requireNotNull(material), size.width.value.toInt())
            }
        }
    }

    private fun measureIfRequested(theme: GamePalette, material: BlockMaterial, width: Int) {
        if (width != 393 || InstrumentationRegistry.getArguments().getString("materialBenchmark") != "true") return
        for (side in listOf(74, 24)) {
            val bitmap = Bitmap.createBitmap(side * 10, side * 20, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap.asImageBitmap())
            val scope = CanvasDrawScope()
            val draw = {
                scope.draw(Density(1f), LayoutDirection.Ltr, canvas,
                    Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
                    repeat(200) { index ->
                        bevelBlock(Offset((index % 10 * side).toFloat(), (index / 10 * side).toFloat()),
                            Size(side.toFloat(), side.toFloat()), theme.pieces[index % 7], theme.finish,
                            theme.texture, material = material)
                    }
                }
            }
            repeat(10) { draw() }
            val start = SystemClock.elapsedRealtimeNanos()
            repeat(20) { draw() }
            val nanos = SystemClock.elapsedRealtimeNanos() - start
            val directory = File(ApplicationProvider.getApplicationContext<Application>().getExternalFilesDir(null),
                "materials-189").apply { mkdirs() }
            File(directory, "drawing-times.csv").appendText("${theme.id},$side,20,200,$nanos\n")
            bitmap.recycle()
        }
    }

    private fun materialState(): GameState {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        Tetromino.entries.forEachIndexed { column, piece -> board[BoardGeometry.row(18)][column] = piece }
        return GameState(board = board, active = Piece(Tetromino.T, x = 3, y = 8),
            next = Tetromino.I, held = Tetromino.L, score = 254430)
    }

    private fun assertScore(theme: GamePalette, pixels: PixelMap) {
        var segments = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            if (solidSegment(pixels[x, y], theme.light)) segments++
        }
        assertTrue("${theme.id}: visible contrasting score segments", segments > 20)
        if (theme.light) assertTrue("No dark score backdrop", pixels[0, 0].red > .5f)
    }

    private fun solidSegment(pixel: Color, light: Boolean): Boolean =
        listOf(pixel.red, pixel.green, pixel.blue).all { if (light) it < .01f else it > .99f }

    @Test fun everySettingsCardUsesItsMaterialAtNormalAndLargeFonts() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(), null, { 1000L }, false)
        model.navigation.settings()
        var viewport by mutableStateOf(DpSize(240.dp, 580.dp))
        var font by mutableStateOf(2f)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport) then
                DeviceConfigurationOverride.FontScale(font)) { SwypetrisApp(model) {} }
        }
        for ((size, scale) in listOf(DpSize(240.dp, 580.dp) to 2f, DpSize(393.dp, 873.dp) to 1f)) {
            compose.runOnIdle { viewport = size; font = scale }
            for (theme in GamePalettes.all) {
                val card = compose.onNodeWithTag("palette_${theme.id}_preview")
                card.performScrollTo().assertIsDisplayed()
                export("settings-${theme.id}-${size.width.value.toInt()}-font$scale",
                    card.captureToImage().asAndroidBitmap())
            }
        }

    }

    private fun export(name: String, bitmap: Bitmap) {
        if (InstrumentationRegistry.getArguments().getString("materialCapture") != "true") return
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.getExternalFilesDir(null), "materials-189").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
