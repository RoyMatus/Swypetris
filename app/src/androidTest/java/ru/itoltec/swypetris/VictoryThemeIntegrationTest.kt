package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.bottom
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.then
import androidx.compose.ui.test.top
import androidx.compose.ui.test.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Проверяет новый цикл, темы и подсказки в изолированной партии; сохраняет реальные рендеры. */
class VictoryThemeIntegrationTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun randomThemeChangesOnlyOnNewRoundAndSurvivesRestoration() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = GameViewModel(app,
            GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 79999),
            { 1000L }, false, paletteRandom = kotlin.random.Random(183))
        val initialPalette = model.paletteId
        model.options.setRandomTheme(true)
        assertEquals(initialPalette, model.paletteId)
        model.input.command(GameCommand.SOFT_DROP)
        assertEquals(GameScreen.VICTORY, model.screen)
        val beforeRound = model.game!!
        model.onBackground()
        model.nextRound()
        assertEquals(initialPalette, model.paletteId)
        model.onForeground()
        model.resume()
        model.nextRound()
        assertEquals(beforeRound.score, model.game!!.score)
        assertEquals(beforeRound.lines, model.game!!.lines)
        assertEquals(beforeRound.completedRounds + 1, model.game!!.completedRounds)
        assertEquals(beforeRound.startingLevel, model.game!!.startingLevel)
        assertEquals(GameScreen.PLAYING, model.screen)
        assertFalse(initialPalette == model.paletteId)
        val roundPalette = model.paletteId
        model.pause()
        model.resume()
        assertEquals(roundPalette, model.paletteId)
        val restored = GameViewModel(app, null, { 1000L }, false)
        assertTrue(restored.randomThemeEnabled)
        assertEquals(roundPalette, restored.paletteId)
        restored.resume()
        assertEquals(roundPalette, restored.paletteId)
        restored.newGame()
        assertFalse(roundPalette == restored.paletteId)
    }

    @Test fun everyManualThemeIncludingCurrentDisablesRandomizationPersistently() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = GameViewModel(app, null, { 1000L }, false)
        for (palette in GamePalettes.all) {
            model.options.setRandomTheme(true)
            model.options.setPalette(palette.id)
            assertFalse(model.randomThemeEnabled)
            model.newGame()
            assertEquals(palette.id, model.paletteId)
            val restored = GameViewModel(app, null, { 1000L }, false)
            assertFalse(restored.randomThemeEnabled)
            assertEquals(palette.id, restored.paletteId)
        }
        model.options.setRandomTheme(true)
        model.options.setPalette(model.paletteId)
        assertFalse(model.randomThemeEnabled)
    }

    /** Celebration continues past the former end time and stops while the app is hidden. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun victoryArtworkAndFireworksFillScreenAndPauseInBackground() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 79999, lines = 100),
            { 1000L }, false)
        compose.mainClock.autoAdvance = false
        compose.setContent { SwypetrisApp(model) {} }
        compose.runOnIdle { model.input.command(GameCommand.SOFT_DROP) }
        compose.mainClock.advanceTimeBy(100)
        val artwork = compose.onNodeWithTag("victoryArtwork").fetchSemanticsNode().boundsInRoot
        val scene = compose.onNodeWithTag("victoryScene").fetchSemanticsNode().boundsInRoot
        assertEquals(scene.width, artwork.width, 1f)
        assertEquals(scene.height, artwork.height, 1f)
        compose.mainClock.advanceTimeBy(9000)
        compose.runOnIdle { assertTrue(model.victoryAnimationMillis in 1 until VictoryMotion.PERIOD_MILLIS) }
        screenshot("victory-loop.png")
        var paused = 0L
        compose.runOnIdle { model.onBackground(); paused = model.victoryAnimationMillis }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle {
            assertEquals(paused, model.victoryAnimationMillis)
            model.onForeground()
            model.resume()
        }
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle { assertTrue(model.victoryAnimationMillis != paused) }
        // Scroll gestures need clock progression when the action is outside a short viewport.
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("victoryPage").performScrollToNode(hasTestTag("nextRound"))
        compose.onNodeWithTag("nextRound").assertIsDisplayed()
    }

    /** Disabled animations leave a still celebration while the next round stays operable. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun launchBurstAndFadeKeepTrophyAndControlsFixed() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 79999, lines = 100),
            { 1000L }, false)
        compose.mainClock.autoAdvance = false
        compose.setContent { SwypetrisApp(model) {} }
        compose.runOnIdle { model.input.command(GameCommand.SOFT_DROP) }
        val frames = listOf(300L to "victory-launch.png", 2300L to "victory-burst.png",
            4000L to "victory-fade.png").map { (time, name) ->
            compose.mainClock.advanceTimeBy(time - compose.mainClock.currentTime)
            compose.waitForIdle()
            screenshot(name)
        }
        for (frame in frames.drop(1)) {
            assertTrue("Fireworks must visibly change between storyboard stages",
                changedPixels(frames.first(), frame, .03f, .38f, .02f, .25f, 4) > 20)
            assertEquals("Trophy must stay fixed over the animated background", 0,
                changedPixels(frames.first(), frame, .42f, .58f, .25f, .34f, 4))
            assertEquals("Victory controls must stay fixed", 0,
                changedPixels(frames.first(), frame, .08f, .92f, .58f, .95f, 0))
        }
        compose.runOnIdle { assertEquals(GameScreen.VICTORY, model.screen) }
        compose.onNodeWithTag("victoryTitle").assertIsDisplayed()
    }

    private fun changedPixels(first: Bitmap, second: Bitmap, left: Float, right: Float,
        top: Float, bottom: Float, tolerance: Int): Int {
        var changed = 0
        for (y in (first.height * top).toInt() until (first.height * bottom).toInt() step 3) {
            for (x in (first.width * left).toInt() until (first.width * right).toInt() step 3) {
                val before = first.getPixel(x, y)
                val after = second.getPixel(x, y)
                if (listOf(0, 8, 16).any { shift ->
                    kotlin.math.abs(((before shr shift) and 255) - ((after shr shift) and 255)) > tolerance
                }) changed++
            }
        }
        return changed
    }

    /** Disabled animations leave a still celebration while the next round stays operable. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun reducedMotionKeepsStaticCelebrationAndAction() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 79999, lines = 100),
            { 1000L }, false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalVictoryAnimations provides false) { SwypetrisApp(model) {} }
        }
        compose.runOnIdle { model.input.command(GameCommand.SOFT_DROP) }
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle { assertEquals(0L, model.victoryAnimationMillis) }
        compose.onNodeWithTag("victoryFireworks").assertExists()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("victoryPage").performScrollToNode(hasTestTag("nextRound"))
        compose.onNodeWithTag("nextRound").performClick()
        compose.runOnIdle { assertEquals(GameScreen.PLAYING, model.screen) }
    }

    /** Narrow, tall and tablet portrait viewports keep the celebration content reachable. */
    @Test fun victoryContentFitsMultipleViewportShapes() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 79999, lines = 100),
            { 1000L }, false)
        var viewport by mutableStateOf(DpSize(320.dp, 640.dp))
        var renderedDensity = 1f
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport) then
                DeviceConfigurationOverride.FontScale(1.5f)) {
                CompositionLocalProvider(LocalVictoryAnimations provides false) {
                    val density = LocalDensity.current
                    SideEffect { renderedDensity = density.density }
                    SwypetrisApp(model) {}
                }
            }
        }
        compose.runOnIdle { model.input.command(GameCommand.SOFT_DROP) }
        for ((width, height) in listOf(320.dp to 640.dp, 393.dp to 873.dp,
            600.dp to 960.dp, 800.dp to 1280.dp)) {
            viewport = DpSize(width, height)
            compose.waitForIdle()
            val scene = compose.onNodeWithTag("victoryScene").fetchSemanticsNode().boundsInRoot
            val artwork = compose.onNodeWithTag("victoryArtwork").fetchSemanticsNode().boundsInRoot
            assertEquals(width.value, scene.width / renderedDensity, 1f)
            assertEquals(height.value, scene.height / renderedDensity, 1f)
            assertEquals(scene, artwork)
            compose.onNodeWithTag("victoryPage").performScrollToNode(hasTestTag("victoryTitle"))
            compose.onNodeWithTag("victoryTitle").assertIsDisplayed()
            compose.onNodeWithTag("victoryPage").performScrollToNode(hasTestTag("nextRound"))
            val button = compose.onNodeWithTag("nextRound").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue("Button exceeds $width x $height", button.left >= scene.left &&
                button.right <= scene.right && button.bottom <= scene.bottom)
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
                .assertCountEquals(0)
        }
    }

    /** Все темы сохраняются; Next доступен экранному диктору независимо от тени падения. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun palettesAndHintsPreserveBoard() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = GameViewModel(app, GameState(active = Piece(Tetromino.O), next = Tetromino.T), { 1000L }, false)
        compose.setContent { SwypetrisApp(model) {} }
        assertEquals(12, GamePalettes.all.size)
        assertEquals(2, GamePalettes.all.count { it.light })
        for (palette in GamePalettes.all) {
            compose.runOnIdle { model.options.setPalette(palette.id); model.options.setHints(true) }
            compose.onNodeWithTag("board").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,
                listOf("Игровое поле, очки 0, линии 0. Следующая фигура T Запас: пусто, обмен доступен.")))
            screenshot("board-${palette.id}.png")
            compose.runOnIdle {
                val state = model.game
                val fresh = GameViewModel(app, null, { 1000L }, false)
                assertEquals(palette.id, fresh.paletteId)
                model.options.setHints(false)
                assertEquals(state, model.game)
            }
            compose.onNodeWithTag("board").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,
                listOf("Игровое поле, очки 0, линии 0. Следующая фигура T Запас: пусто, обмен доступен.")))
            compose.onNodeWithTag("nextPreview").assertIsDisplayed()
        }
        compose.runOnIdle { model.options.setPalette("unknown"); assertEquals("classic", model.paletteId);
            model.navigation.settings() }
        compose.onNodeWithTag("palette_github_light_preview").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("github_light", model.paletteId) }
        screenshot("settings-github-light.png")
        compose.runOnIdle { model.navigation.menu() }
        screenshot("menu-github-light.png")
        compose.runOnIdle { model.options.setPalette("solarized_dark") }
        screenshot("menu-solarized-dark.png")
        compose.runOnIdle { model.options.setPalette("synthwave_84") }
        screenshot("menu-synthwave-84.png")
    }

    /** Поздравление и кнопки доступны при ширине 320 dp и двойном шрифте. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun victoryAtLargeFontAndLightTheme() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 79999), { 1000L }, false)
        model.options.setPalette("solarized_light")
        model.input.command(GameCommand.SOFT_DROP)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) { SwypetrisApp(model) {} }
            }
        }
        compose.mainClock.advanceTimeBy(2000)
        screenshot("victory-light-large-font.png")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("victoryPage").performScrollToNode(hasTestTag("nextRound"))
        compose.onNodeWithTag("nextRound").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .assertCountEquals(0)
        screenshot("victory-light-buttons.png")
    }

    /** Все темы доступны без горизонтальной прокрутки при ширине 320 dp и двойном шрифте. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun palettePickerAtLargeFont() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(), null, { 1000L }, false)
        model.options.setPalette("solarized_light")
        model.navigation.settings()
        var viewport by mutableStateOf(DpSize(320.dp, 640.dp))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport) then
                DeviceConfigurationOverride.FontScale(2f)) { SwypetrisApp(model) {} }
        }
        for (size in listOf(DpSize(320.dp, 640.dp), DpSize(393.dp, 873.dp), DpSize(600.dp, 400.dp))) {
            viewport = size
            compose.waitForIdle()
            val control = compose.onNodeWithTag("randomTheme").performScrollTo().assertIsDisplayed()
            val bounds = control.fetchSemanticsNode().boundsInRoot
            val checkbox = compose.onNodeWithTag("randomThemeBox", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue(checkbox.left > bounds.center.x)
            screenshot("random-theme-${size.width.value}-${size.height.value}.png")
        }
        compose.onNodeWithTag("randomTheme").performClick()
        compose.runOnIdle { assertTrue(model.randomThemeEnabled) }
        GamePalettes.all.forEach { item -> compose.onNodeWithTag("palette_${item.id}_preview").assertExists() }
        compose.onNodeWithTag("palettePicker").assertDoesNotExist()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .assertCountEquals(0)
        compose.onNodeWithTag("palette_synthwave_84_preview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("palette_catppuccin_preview").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("catppuccin", model.paletteId)
            assertFalse(model.randomThemeEnabled)
        }
        compose.runOnIdle {
            val restored = GameViewModel(ApplicationProvider.getApplicationContext(), null, { 1000L }, false)
            assertEquals("catppuccin", restored.paletteId)
        }
        compose.onNodeWithTag("palette_catppuccin_preview").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithTag("paletteGrid").assertExists()
        screenshot("settings-large-font.png")
    }

    /** Названия тем полностью видны и отделены от превью на узком экране при обычном и двойном шрифте. */
    @Test fun paletteNamesFitWithoutOverlappingPreviews() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(), null, { 1000L }, false)
        model.navigation.settings()
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) { SwypetrisApp(model) {} }
            }
        }
        for (scale in listOf(1f, 2f)) {
            compose.runOnIdle { fontScale = scale }
            GamePalettes.all.forEach { item ->
                val card = compose.onNodeWithTag("palette_${item.id}_preview")
                card.performScrollTo().assertIsDisplayed()
                val art = compose.onNodeWithTag("palette_${item.id}_art", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val label = compose.onNodeWithTag("palette_${item.id}_label", useUnmergedTree = true)
                val labelNode = label.assertTextEquals(item.title).fetchSemanticsNode()
                val labelBounds = labelNode.boundsInRoot
                assertTrue("${item.title}: label overlaps preview at $scale", labelBounds.top >= art.bottom)
                assertTrue("${item.title}: label exceeds card at $scale",
                    labelBounds.bottom <= card.fetchSemanticsNode().boundsInRoot.bottom)
                val layout = mutableListOf<TextLayoutResult>()
                assertTrue(labelNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout) == true)
                assertFalse("${item.title}: text is clipped at $scale", layout.single().hasVisualOverflow)
            }
        }
    }

    /** Сохраняет снимок интерфейса только в файлы тестового эмулятора. */
    @androidx.annotation.RequiresApi(26)
    private fun screenshot(name: String): Bitmap {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = java.io.File(app.getExternalFilesDir(null), name)
        return compose.onRoot().captureToImage().asAndroidBitmap().also { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
