package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
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

    /** Поздравление не тратит время, не пишет историю и не повторяет награды при возврате. */
    @Test fun victoryAndContinuationPreserveSession() {
        var now = 1000L
        val music = Music()
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 79999, lines = 100),
            { now }, false, musicPlayback = music)
        compose.mainClock.autoAdvance = false
        compose.setContent { SwypetrisApp(model) {} }
        compose.runOnIdle { now += 100; model.command(GameCommand.TICK) }
        compose.mainClock.advanceTimeBy(2200)
        compose.onNodeWithTag("victoryPage").assertIsDisplayed()
        compose.onNodeWithTag("board").assertDoesNotExist()
        screenshot("victory-classic.png")
        compose.runOnIdle {
            assertEquals(80000, model.game!!.score)
            assertEquals(1, music.modes.count { it == MusicMode.RECORD })
            assertTrue(model.results.isEmpty())
            now += 30000
            model.advanceFrame(now)
            model.menu()
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
            assertEquals(120L, model.game!!.gravityMillis)
            assertEquals(1, model.game!!.completedRounds)
            assertEquals(0, model.game!!.roundFruits)
            assertTrue(model.game!!.board.flatten().all { it == null })
            val state = model.game
            model.nextRound()
            assertEquals(state, model.game)
            // Заканчиваем эту же партию на тестовом поле, не записывая время поздравления.
            repeat(30) { model.command(GameCommand.HARD_DROP) }
            assertEquals(1, model.results.size)
            assertEquals(1, model.results.single().completedRounds)
            assertEquals(100L, model.results.single().durationMillis)
        }
    }

    /** Все темы сохраняются; скрытое превью не раскрывается экранному диктору. */
    @Test fun palettesAndHintsPreserveBoard() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = GameViewModel(app, GameState(active = Piece(Tetromino.O), next = Tetromino.T), { 1000L }, false)
        compose.setContent { SwypetrisApp(model) {} }
        assertEquals(12, GamePalettes.all.size)
        assertEquals(2, GamePalettes.all.count { it.light })
        for (palette in GamePalettes.all) {
            compose.runOnIdle { model.setPalette(palette.id); model.setHints(true) }
            compose.onNodeWithTag("board").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,
                listOf("Игровое поле, очки 0, линии 0. Следующая фигура T")))
            screenshot("board-${palette.id}.png")
            compose.runOnIdle {
                val state = model.game
                val fresh = GameViewModel(app, null, { 1000L }, false)
                assertEquals(palette.id, fresh.paletteId)
                model.setHints(false)
                assertEquals(state, model.game)
            }
            compose.onNodeWithTag("board").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,
                listOf("Игровое поле, очки 0, линии 0.")))
        }
        compose.runOnIdle { model.setPalette("unknown"); assertEquals("classic", model.paletteId); model.settings() }
        compose.onNodeWithTag("palette_github_light_preview").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("github_light", model.paletteId) }
        screenshot("settings-github-light.png")
        compose.runOnIdle { model.menu() }
        screenshot("menu-github-light.png")
        compose.runOnIdle { model.setPalette("solarized_dark") }
        screenshot("menu-solarized-dark.png")
        compose.runOnIdle { model.setPalette("synthwave_84") }
        screenshot("menu-synthwave-84.png")
    }

    /** Поздравление и кнопки доступны при ширине 320 dp и двойном шрифте. */
    @Test fun victoryAtLargeFontAndLightTheme() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 79999), { 1000L }, false)
        model.setPalette("solarized_light")
        model.command(GameCommand.TICK)
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
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).assertCountEquals(0)
        screenshot("victory-light-buttons.png")
    }

    /** Все темы доступны без горизонтальной прокрутки при ширине 320 dp и двойном шрифте. */
    @Test fun palettePickerAtLargeFont() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(), null, { 1000L }, false)
        model.setPalette("solarized_light")
        model.settings()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) { SwypetrisApp(model) {} }
            }
        }
        GamePalettes.all.forEach { item -> compose.onNodeWithTag("palette_${item.id}_preview").assertExists() }
        compose.onNodeWithTag("palettePicker").assertDoesNotExist()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).assertCountEquals(0)
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
        model.settings()
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
    private fun screenshot(name: String) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = java.io.File(app.getExternalFilesDir(null), name)
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
