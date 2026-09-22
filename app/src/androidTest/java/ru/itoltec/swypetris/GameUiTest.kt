package ru.itoltec.swypetris

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.test.espresso.Espresso
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Проверяет реальные Compose-экраны, касания и сохранение партии в модели Activity. */
@RunWith(AndroidJUnit4::class)
class GameUiTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    /** Возвращает ту же модель, которой владеет проверяемая Activity. */
    private fun model() = ViewModelProvider(compose.activity)[GameViewModel::class.java]

    /** Пересоздание контактов сохраняет экран, портретную ориентацию и приостановленную партию. */
    @Test fun contactsSurviveRecreation() {
        compose.runOnIdle { model().newGame(); model().contacts() }
        val saved = model().game
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("contactsPage").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(GameScreen.CONTACTS, model().screen)
            assertEquals(saved, model().game)
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, compose.activity.requestedOrientation)
        }
        Espresso.pressBack()
        compose.runOnIdle {
            assertEquals(GameScreen.MENU, model().screen)
            assertEquals(saved, model().game)
        }
    }

    /** Пересоздание справки сохраняет экран и не возобновляет партию автоматически. */
    @Test fun helpSurvivesRecreation() {
        compose.runOnIdle { model().newGame(); model().menu() }
        compose.onNodeWithTag("help").performClick()
        val saved = model().game
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("helpPage").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(GameScreen.HELP, model().screen)
            assertEquals(saved, model().game)
        }
        compose.onNodeWithTag("helpPage").performScrollToNode(hasTestTag("helpBack"))
        compose.onNodeWithTag("helpBack").performClick()
        compose.runOnIdle { assertEquals(GameScreen.MENU, model().screen) }
    }
    /** Настройки доступны из меню; независимые переключатели сохраняются и не возобновляют игру. */
    @Test fun settingsPersistAndReturnToMenu() {
        var oldSound = true
        var oldVibration = true
        var oldHints = false
        compose.runOnIdle {
            oldSound = model().soundEnabled
            oldVibration = model().vibrationEnabled
            oldHints = model().hintsEnabled
        }
        try {
            compose.onNodeWithTag("settings").performClick()
            compose.onNodeWithTag("sound").performClick()
            compose.onNodeWithTag("vibration").performClick()
            compose.onNodeWithTag("hints").performClick()
            compose.runOnIdle {
                val store = ViewModelStore()
                try {
                    val factory = ViewModelProvider.AndroidViewModelFactory(ApplicationProvider.getApplicationContext<Application>())
                    val fresh = ViewModelProvider(store, factory)[GameViewModel::class.java]
                    assertEquals(!oldSound, fresh.soundEnabled)
                    assertEquals(!oldVibration, fresh.vibrationEnabled)
                    assertEquals(!oldHints, fresh.hintsEnabled)
                    assertEquals(GameScreen.SETTINGS, model().screen)
                } finally { store.clear() }
            }
            compose.onNodeWithText("Назад").performClick()
            compose.onNodeWithContentDescription("SWYPETRIS").assertIsDisplayed()
            compose.onNodeWithTag("sound").assertDoesNotExist()
        } finally {
            compose.runOnIdle {
                model().setSound(oldSound)
                model().setVibration(oldVibration)
                model().setHints(oldHints)
            }
        }
    }

    /** Два тапа вращают дважды; системный Back открывает меню и сохраняет партию. */
    @Test fun twoTapsRotateAndBackPreservesGame() {
        compose.onNodeWithTag("newGame").performClick()
        var rotation = -1
        var square = false
        compose.runOnIdle {
            rotation = model().game!!.active.rotation
            square = model().game!!.active.type == Tetromino.O
        }
        compose.onNodeWithTag("gameArea").performTouchInput { doubleClick(center) }
        compose.runOnIdle {
            assertEquals(if (square) rotation else (rotation + 2) % 4, model().game!!.active.rotation)
            assertEquals(GameScreen.PLAYING, model().screen)
        }
        Espresso.pressBack()
        compose.onNodeWithContentDescription("SWYPETRIS").assertIsDisplayed()
        var saved: GameState? = null
        compose.runOnIdle { saved = model().game }
        compose.onNodeWithText("Продолжить").performClick()
        compose.runOnIdle {
            assertEquals(GameScreen.PLAYING, model().screen)
            assertEquals(saved, model().game)
        }
        compose.onNodeWithTag("board").assertIsDisplayed()
    }

    /** Короткий свайп вниз фиксирует фигуру, перезапуск обнуляет поле и очки. */
    @Test fun dropRestartAndMenuNavigation() {
        compose.onNodeWithTag("newGame").performClick()
        compose.onNodeWithTag("score").assertTextEquals("1 | 0")
        compose.onNodeWithTag("gameArea").performTouchInput {
            swipe(center, center + Offset(0f, 150f), 100)
        }
        var score = 0
        compose.runOnIdle {
            assertEquals(1, model().game!!.generation)
            assertTrue(model().game!!.score > 0)
            score = model().game!!.score
        }
        compose.onNodeWithTag("score").assertTextEquals("1 | $score")
        compose.runOnIdle {
            model().pause()
        }
        compose.onNodeWithText("Новая игра").performClick()
        compose.runOnIdle {
            assertEquals(0, model().game!!.score)
            assertEquals(0, model().game!!.generation)
            model().pause()
        }
        compose.onNodeWithText("Главное меню").performClick()
        compose.onNodeWithContentDescription("SWYPETRIS").assertIsDisplayed()
        compose.onNodeWithText("Продолжить").performClick()
        compose.onNodeWithTag("board").assertIsDisplayed()
    }

    /** Пересоздание Activity сохраняет поле и ставит игру на паузу. */
    @Test fun activityRecreationKeepsGamePaused() {
        compose.onNodeWithTag("newGame").performClick()
        var previous: GameState? = null
        compose.runOnIdle {
            model().command(GameCommand.HARD_DROP)
            model().pause()
            previous = model().game
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Пауза").assertIsDisplayed()
        compose.runOnIdle { assertEquals(previous, model().game) }
    }

    /** Рекорд доступен новой модели, а игровые команды во время паузы игнорируются. */
    @Test fun recordPersistsAndPauseIgnoresCommands() {
        compose.runOnIdle {
            val current = model()
            current.newGame()
            current.command(GameCommand.HARD_DROP)
            val originalHints = current.hintsEnabled
            current.setHints(!originalHints)
            val store = ViewModelStore()
            try {
                val factory = ViewModelProvider.AndroidViewModelFactory(ApplicationProvider.getApplicationContext<Application>())
                val fresh = ViewModelProvider(store, factory)[GameViewModel::class.java]
                assertEquals(current.record, fresh.record)
                assertEquals(!originalHints, fresh.hintsEnabled)
            } finally {
                current.setHints(originalHints)
                store.clear()
            }
            current.pause()
            val paused = current.game
            current.command(GameCommand.TICK)
            assertEquals(paused, current.game)
        }
    }

    /** Поле занимает всю область, а Activity запрашивает фиксированную портретную ориентацию. */
    @Test fun boardFillsPortraitScreen() {
        compose.onNodeWithTag("newGame").performClick()
        compose.runOnIdle {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, compose.activity.requestedOrientation)
            assertEquals(Configuration.ORIENTATION_PORTRAIT, compose.activity.resources.configuration.orientation)
        }
        val board = compose.onNodeWithTag("board").fetchSemanticsNode().boundsInRoot
        val area = compose.onNodeWithTag("gameArea").fetchSemanticsNode().boundsInRoot
        assertEquals(area, board)
        compose.onNodeWithTag("score").assertIsDisplayed()
    }
}