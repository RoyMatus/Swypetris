package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import ru.itoltec.swypetris.ui.theme.SwypetrisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Проверяет справку и панель в узкой компоновке, с крупным шрифтом и управляемыми часами. */
class HelpHudTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    /** Справка не теряет партию, не прокручивается горизонтально и возвращает в меню. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun helpPreservesGameAtLargeFont() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        var now = 1000L
        val initial = GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 2100)
        val model = GameViewModel(application, initial, { now }, false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                SwypetrisTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) { SwypetrisApp(model) {} }
                }
            }
        }
        compose.runOnIdle { model.help() }
        compose.onNodeWithTag("helpPage").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .assertCountEquals(0)
        compose.onNodeWithTag("helpPage").performScrollToIndex(7)
        compose.onNodeWithText("Цель").assertIsDisplayed()
        compose.onNodeWithTag("helpPage").performScrollToIndex(8)
        compose.onNodeWithText("Hold").assertIsDisplayed()
        compose.onNodeWithTag("helpPage").performScrollToIndex(9)
        compose.onNodeWithText("Следующая фигура").assertIsDisplayed()
        compose.onNodeWithText("Back-to-Back", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Perfect Clear", substring = true).assertDoesNotExist()
        compose.onNodeWithText("T-Spin", substring = true).assertDoesNotExist()
        compose.onNodeWithText("0,5 секунды", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("helpPage").performScrollToKey("fruits")
        Fruit.entries.forEach { fruit ->
            compose.onNodeWithContentDescription(fruit.title).assertExists()
        }
        screenshot("help-large-font.png")
        compose.runOnIdle {
            now += 10000
            model.advanceFrame(now)
            assertEquals(initial, model.game)
        }
        compose.onNodeWithTag("helpBack").assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        compose.runOnIdle {
            assertEquals(GameScreen.MENU, model.screen)
            assertEquals(initial, model.game)
        }
    }

    /** Line progress pulses once; frequent score changes cannot restart or extend it. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun hudBoundariesAndSinglePulse() {
        var score by mutableIntStateOf(899)
        var lines by mutableIntStateOf(8)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.size(320.dp, 480.dp)) {
                    val state = GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = score, lines = lines)
                    Board(state)
                    GameHud(state)
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("score").assertTextEquals("899")
        compose.runOnIdle { lines = 9; score = 900 }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("score").assertTextEquals("900")
        val scale = compose.onNodeWithTag("score").fetchSemanticsNode().config[ScorePulseScale]
        assertTrue(scale > 1f && scale <= 1.08f)
        repeat(3) {
            compose.runOnIdle { score++ }
            compose.mainClock.advanceTimeBy(48)
        }
        compose.mainClock.advanceTimeBy(80)
        assertEquals(1f, compose.onNodeWithTag("score").fetchSemanticsNode().config[ScorePulseScale], 0.001f)
        compose.runOnIdle { score++ }
        compose.mainClock.advanceTimeBy(64)
        assertEquals(1f, compose.onNodeWithTag("score").fetchSemanticsNode().config[ScorePulseScale], 0.001f)
        for (value in listOf(10, 19, 20, 90)) {
            compose.runOnIdle { lines = value }
            compose.mainClock.advanceTimeBy(272)
            compose.onNodeWithTag("score").assertTextEquals("904")
        }
        screenshot("hud-large-font.png")
    }

    /** Сохраняет изображение проверяемой компоновки в файлы тестового приложения. */
    @androidx.annotation.RequiresApi(26)
    private fun screenshot(name: String) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val file = java.io.File(application.getExternalFilesDir(null), name)
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
