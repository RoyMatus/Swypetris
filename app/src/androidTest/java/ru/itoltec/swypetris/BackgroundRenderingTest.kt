package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BackgroundRenderingTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @SdkSuppress(minSdkVersion = 26)
    @Test fun backgroundSettingsRemainReachableAcrossPortraitSizesAndLargeFonts() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = GameViewModel(app, null, { 1000L }, false)
        model.navigation.settings()
        var viewport by mutableStateOf(DpSize(320.dp, 640.dp))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport) then
                DeviceConfigurationOverride.FontScale(2f)) { SwypetrisApp(model) {} }
        }
        compose.waitUntil(10000) { !model.background.busy }
        for (size in listOf(DpSize(320.dp, 640.dp), DpSize(393.dp, 873.dp), DpSize(600.dp, 960.dp))) {
            compose.runOnIdle { viewport = size }
            compose.onNodeWithTag("customBackgroundEnabled").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("chooseBackground").performScrollTo().assertIsDisplayed()
        }
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test fun imageStaysBehindOpaquePiecesAndGeometryAcrossPortraitSizes() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val board = BoardGeometry.empty().mapIndexed { row, cells ->
            if (row == BoardGeometry.row(18)) cells.mapIndexed { column, piece ->
                if (column == 5) Tetromino.O else piece } else cells
        }
        val state = GameState(board = board, active = Piece(Tetromino.T, x = 3, y = 8), next = Tetromino.I,
            held = Tetromino.L, score = 123456)
        val model = GameViewModel(app, state, { 1000L }, false)
        model.options.setHints(true)
        val input = imageFixture(app)
        var viewport by mutableStateOf(DpSize(320.dp, 640.dp))
        var palette by mutableStateOf(GamePalettes.find("classic"))
        var density = 1f
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport) then
                DeviceConfigurationOverride.FontScale(1.5f)) {
                density = LocalDensity.current.density
                CompositionLocalProvider(LocalGamePalette provides palette) {
                    Box(Modifier.fillMaxSize().testTag("backgroundViewport")) { GameContent(model, state, 24.dp) }
                }
            }
        }
        try {
            compose.waitUntil(10000) { !model.background.busy }
            compose.runOnIdle { model.background.choose(app.contentResolver, Uri.fromFile(input)) }
            compose.waitUntil(10000) { model.background.draft != null && !model.background.busy }
            compose.runOnIdle { model.background.save() }
            compose.waitUntil(10000) { model.background.draft == null && !model.background.busy }
            for (theme in listOf(GamePalettes.find("classic"), GamePalettes.all.first { it.light })) {
                for (size in listOf(DpSize(320.dp, 640.dp), DpSize(393.dp, 873.dp), DpSize(600.dp, 960.dp))) {
                    compose.runOnIdle { viewport = size; palette = theme; model.background.changeEnabled(false) }
                    val bounds = compose.onNodeWithTag("gameArea").fetchSemanticsNode().boundsInRoot
                    val scoreBounds = compose.onNodeWithTag("score").fetchSemanticsNode().boundsInRoot
                    val before = compose.onNodeWithTag("backgroundViewport").captureToImage().toPixelMap()
                    compose.runOnIdle { model.background.changeEnabled(true) }
                    compose.onNodeWithTag("customBackground").assertIsDisplayed()
                    assertEquals(bounds, compose.onNodeWithTag("gameArea").fetchSemanticsNode().boundsInRoot)
                    assertEquals(scoreBounds, compose.onNodeWithTag("score").fetchSemanticsNode().boundsInRoot)
                    assertTrue(compose.onNodeWithTag("score").captureToImage().toPixelMap()[0, 0].luminance() < .2f)
                    val image = compose.onNodeWithTag("backgroundViewport").captureToImage()
                    val after = image.toPixelMap()
                    val geometry = gameplayGeometry(after.height.toFloat(), 24f * density)
                    val cellX = (after.width * .55f).toInt()
                    val cellY = (geometry.gridTop + geometry.cellHeight * 20.5f).toInt()
                    assertEquals(before[cellX, cellY], after[cellX, cellY])
                    assertNotEquals(before[after.width / 20, after.height / 2],
                        after[after.width / 20, after.height / 2])
                    assertSame(state, model.game)
                    saveScreenshot(app, "${theme.id}-${size.width.value}-${size.height.value}.png",
                        image.asAndroidBitmap())
                    compose.runOnIdle { model.background.changeEnabled(false) }
                    compose.onNodeWithTag("customBackground").assertDoesNotExist()
                }
            }
        } finally {
            input.delete()
            model.background.image?.file?.delete()
        }
    }

    private fun imageFixture(app: Application): File {
        val input = File(app.cacheDir, "background-render-${UUID.randomUUID()}.png")
        val fixture = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        fixture.eraseColor(android.graphics.Color.RED)
        input.outputStream().use { check(fixture.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        fixture.recycle()
        return input
    }

    private fun saveScreenshot(app: Application, name: String, bitmap: Bitmap) {
        val directory = File(app.getExternalFilesDir(null), "background-screenshots").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
