package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
import androidx.compose.ui.test.top
import androidx.compose.ui.test.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
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

    /** Записывает фанфары без обращения к динамикам. */
    private class Music : MusicPlayback {
        val modes = mutableListOf<MusicMode>()
        /** Преобразует разрешение музыки в режим. */
        override fun setPlaying(enabled: Boolean) = setMode(if (enabled) MusicMode.GAME else MusicMode.SILENT)
        /** Сохраняет запрос для проверки однократного запуска. */
        override fun setMode(next: MusicMode) { modes += next }
        /** Тестовая реализация не владеет аудиоресурсами. */
        override fun release() = Unit
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
        compose.onNodeWithTag("victoryPage").performScrollToNode(hasTestTag("nextRound"))
        compose.onNodeWithTag("nextRound").assertIsDisplayed()
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

    /** Portrait, narrow and landscape viewports keep the celebration content reachable. */
    @Test fun victoryContentFitsMultipleViewportShapes() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 79999, lines = 100),
            { 1000L }, false)
        var viewport by mutableStateOf(320.dp to 640.dp)
        compose.setContent {
            CompositionLocalProvider(LocalVictoryAnimations provides false,
                LocalDensity provides Density(1f, 1.5f)) {
                Box(Modifier.size(viewport.first, viewport.second)) { SwypetrisApp(model) {} }
            }
        }
        compose.runOnIdle { model.input.command(GameCommand.SOFT_DROP) }
        for ((width, height) in listOf(320.dp to 640.dp, 393.dp to 873.dp,
            600.dp to 400.dp, 800.dp to 480.dp)) {
            compose.runOnIdle { viewport = width to height }
            val scene = compose.onNodeWithTag("victoryScene").fetchSemanticsNode().boundsInRoot
            val artwork = compose.onNodeWithTag("victoryArtwork").fetchSemanticsNode().boundsInRoot
            assertEquals(width.value, scene.width, 1f)
            assertEquals(height.value, scene.height, 1f)
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

    /** Поздравление не тратит время, не пишет историю и не повторяет награды при возврате. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun victoryAndContinuationPreserveSession() {
        var now = 1000L
        val music = Music()
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 79999, lines = 100),
            { now }, false, musicPlayback = music)
        val initialGravity = model.game!!.gravityMillis
        compose.mainClock.autoAdvance = false
        compose.setContent { SwypetrisApp(model) {} }
        compose.runOnIdle { now += 100; model.input.command(GameCommand.SOFT_DROP) }
        compose.mainClock.advanceTimeBy(2200)
        compose.onNodeWithTag("victoryPage").assertIsDisplayed()
        compose.onNodeWithTag("board").assertDoesNotExist()
        screenshot("victory-classic.png")
        compose.runOnIdle {
            assertEquals(80000, model.game!!.score)
            assertEquals(1, music.modes.count { it == MusicMode.RECORD })
            assertTrue(model.results.isEmpty())
            now += 30000
            model.simulation.advanceFrame(now)
            model.navigation.menu()
            model.resume()
            assertEquals(GameScreen.VICTORY, model.screen)
            assertEquals(1, music.modes.count { it == MusicMode.RECORD })
        }
        compose.mainClock.advanceTimeBy(32)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("victoryPage").performScrollToNode(hasTestTag("nextRound"))
        compose.onNodeWithTag("nextRound").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            assertEquals(GameScreen.PLAYING, model.screen)
            assertEquals(80000, model.game!!.score)
            assertEquals(100, model.game!!.lines)
            assertEquals(initialGravity, model.game!!.gravityMillis)
            assertEquals(11, model.game!!.level)
            assertEquals(1, model.game!!.completedRounds)
            assertEquals(0, model.game!!.roundFruits)
            assertTrue(model.game!!.board.flatten().all { it == null })
            val state = model.game
            model.nextRound()
            assertEquals(state, model.game)
            // Заканчиваем эту же партию на тестовом поле, не записывая время поздравления.
            repeat(30) { model.input.command(GameCommand.HARD_DROP) }
            assertEquals(1, model.results.size)
            assertEquals(1, model.results.single().completedRounds)
            assertEquals(100L, model.results.single().durationMillis)
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
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) { SwypetrisApp(model) {} }
            }
        }
        GamePalettes.all.forEach { item -> compose.onNodeWithTag("palette_${item.id}_preview").assertExists() }
        compose.onNodeWithTag("palettePicker").assertDoesNotExist()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .assertCountEquals(0)
        compose.onNodeWithTag("palette_synthwave_84_preview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("palette_catppuccin_preview").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("catppuccin", model.paletteId) }
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
    private fun screenshot(name: String) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = java.io.File(app.getExternalFilesDir(null), name)
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
