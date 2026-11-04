package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exact renderer exports support visual review of outlined digits over contrasting backgrounds. */
class FruitHudEvidenceTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun supportedLargeQuantitiesKeepExactAccessibleValuesAndFitTheirOwnCells() {
        var score by mutableStateOf(1040000L)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.size(240.dp, 640.dp)) {
                    GameHud(GameState(active = Piece(Tetromino.T), next = Tetromino.O, score = score))
                }
            }
        }
        val examples = listOf(1040000L to "13", 96000000L to "1.2K",
            272000000000L to "3.4M", Long.MAX_VALUE to "115T")
        for ((value, expected) in examples) {
            compose.runOnIdle { score = value }
            Fruit.entries.forEach { fruit ->
                val label = compose.onNodeWithTag("earnedFruitCount_${fruit.name}")
                    .assertContentDescriptionEquals("${fruit.title}: ${fruitQuantity(value, fruit)}")
                label.assertTextEquals(expected)
                val layouts = mutableListOf<TextLayoutResult>()
                label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue("Quantity text has a measured layout", layouts.isNotEmpty())
                assertTrue("Quantity text is not clipped: score=$value fruit=$fruit " +
                    layouts.joinToString { "font=${it.layoutInput.style.fontSize} size=${it.size} " +
                        "constraints=${it.layoutInput.constraints} width=${it.didOverflowWidth} " +
                        "height=${it.didOverflowHeight}" }, layouts.none { it.hasVisualOverflow })
                val cell = compose.onNodeWithTag("earnedFruit_${fruit.name}").fetchSemanticsNode().boundsInRoot
                val bounds = label.fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.left >= cell.left && bounds.right <= cell.right + 1f)
                assertTrue(bounds.top >= cell.top && bounds.bottom <= cell.bottom + 1f)
            }
        }
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test fun quantitiesRemainAssociatedAcrossBackgroundsAndFontScales() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        var state by mutableStateOf(GameState(active = Piece(Tetromino.S, y = 8), next = Tetromino.O,
            held = Tetromino.J, score = 254430))
        val model = GameViewModel(app, state, { 1000L }, false)
        var palette by mutableStateOf(GamePalettes.find("classic"))
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalGamePalette provides palette,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                Box(Modifier.size(320.dp, 640.dp).testTag("fruitHudEvidence")) { GameContent(model, state, 0.dp) }
            }
        }
        val fixture = backgroundFixture(app)
        val prefix = InstrumentationRegistry.getArguments().getString("fruitHudEvidencePrefix") ?: "candidate"
        val output = File(app.getExternalFilesDir(null), "fruit-hud-334/$prefix").apply { mkdirs() }
        try {
            compose.waitUntil(10000) { !model.background.busy }
            compose.runOnIdle { model.background.choose(app.contentResolver, Uri.fromFile(fixture)) }
            compose.waitUntil(10000) { model.background.draft != null && !model.background.busy }
            compose.runOnIdle { model.background.save() }
            compose.waitUntil(10000) { model.background.draft == null && !model.background.busy }
            for (scene in evidenceScenes()) {
                compose.runOnIdle {
                    palette = GamePalettes.find(if (scene.background == "photo") "classic" else scene.background)
                    model.background.changeEnabled(scene.background == "photo")
                    fontScale = scene.fontScale
                    state = state.copy(score = scene.score)
                }
                assertQuantityDescriptions(scene.score)
                exportFrame(output, scene)
            }
        } finally {
            fixture.delete()
            model.background.image?.file?.delete()
        }
    }

    private fun backgroundFixture(app: Application): File {
        val fixture = File(app.cacheDir, "fruit-hud-334-background.png")
        val bitmap = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            bitmap.setPixel(x, y, if ((x / 20 + y / 20) % 2 == 0) android.graphics.Color.WHITE
                else android.graphics.Color.BLUE)
        }
        fixture.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return fixture
    }

    private fun assertQuantityDescriptions(score: Long) {
        Fruit.entries.forEach { fruit ->
            compose.onNodeWithTag("earnedFruitCount_${fruit.name}")
                .assertContentDescriptionEquals("${fruit.title}: ${fruitQuantity(score, fruit)}")
        }
    }

    @androidx.annotation.RequiresApi(26)
    private fun exportFrame(output: File, scene: FruitHudScene) {
        val frame = compose.onNodeWithTag("fruitHudEvidence").captureToImage().asAndroidBitmap()
        File(output, "${scene.background}-${scene.fontScale}-${scene.score}.png").outputStream().use {
            assertTrue(frame.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun evidenceScenes() = listOf("classic", "github_light", "photo").flatMap { background ->
        listOf(1f, 2f).flatMap { scale ->
            listOf(254430L, 1040000L).map { FruitHudScene(background, scale, it) }
        }
    }
}

private data class FruitHudScene(val background: String, val fontScale: Float, val score: Long)
