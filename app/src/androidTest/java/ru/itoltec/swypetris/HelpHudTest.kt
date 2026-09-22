package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import ru.itoltec.swypetris.ui.theme.SwypetrisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Проверяет справку и панель в узкой компоновке, с крупным шрифтом и управляемыми часами. */
class HelpHudTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    /** Справка не теряет партию, не прокручивается горизонтально и возвращает в меню. */
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
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).assertCountEquals(0)
        screenshot("help-large-font.png")
        compose.runOnIdle {
            now += 10000
            model.advanceFrame(now)
            assertEquals(initial, model.game)
        }
        compose.onNodeWithTag("helpPage").performScrollToNode(hasTestTag("helpBack"))
        compose.onNodeWithTag("helpBack").performClick()
        compose.runOnIdle {
            assertEquals(GameScreen.MENU, model.screen)
            assertEquals(initial, model.game)
        }
    }

    /** Частые изменения не продлевают импульс, а переход уровня сразу меняет итоговую надпись. */
    @Test fun hudBoundariesAndSinglePulse() {
        var score by mutableIntStateOf(799)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.size(320.dp, 480.dp)) {
                    val state = GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = score)
                    Board(state)
                    GameHud(state)
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("score").assertTextEquals("1 | 799")
        compose.runOnIdle { score = 800 }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("score").assertTextEquals("1 | −200")
        val scale = compose.onNodeWithTag("score").fetchSemanticsNode().config[ScorePulseScale]
        assertTrue(scale > 1f && scale <= 1.08f)
        repeat(3) {
            compose.runOnIdle { score++ }
            compose.mainClock.advanceTimeBy(48)
        }
        compose.mainClock.advanceTimeBy(80)
        assertEquals(1f, compose.onNodeWithTag("score").fetchSemanticsNode().config[ScorePulseScale], 0.001f)
        for ((value, text) in listOf(999 to "1 | −1", 1000 to "2 | 1000", 1999 to "2 | 1999",
            2000 to "2 | −250", 2249 to "2 | −1", 2250 to "3 | 2250", 18000 to "10 | 18000")) {
            compose.runOnIdle { score = value }
            compose.mainClock.advanceTimeBy(272)
            compose.onNodeWithTag("score").assertTextEquals(text)
        }
        screenshot("hud-large-font.png")
    }

    /** Сохраняет изображение проверяемой компоновки в файлы тестового приложения. */
    private fun screenshot(name: String) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val file = java.io.File(application.getExternalFilesDir(null), name)
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

