package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Проверяет отрисовку и игровые часы удаления без ожидания реальных 600 мс. */
@RunWith(AndroidJUnit4::class)
class LineClearUiTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    /** Создаёт фигуру, которая следующим шагом заполнит нижнюю строку. */
    private fun almostFull(): GameState {
        val board = List(20) { MutableList<Tetromino?>(10) { null } }
        for (x in 0..7) board[19][x] = Tetromino.J
        return GameState(board = board, active = Piece(Tetromino.O, x = 8, y = 18), next = Tetromino.T)
    }

    /** Проверяет последовательное исчезновение клеток, остановку управления, заморозку времени в меню и завершение ровно на 600 мс. */
    @Test fun clearPausesAndCompletesOnControlledClock() {
        var now = 1000L
        val model = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), almostFull(), { now }, false)
        // Тестовая Activity не скрывает системную навигацию: исключаем её из проверяемого Canvas.
        compose.setContent { Box(Modifier.safeDrawingPadding()) { model.game?.let { Board(it, model.clearElapsedMillis) } } }
        compose.runOnIdle {
            model.command(GameCommand.TICK)
            val locked = model.game
            assertEquals(listOf(19), locked!!.clearingRows)
            now += 30
            model.advanceFrame(now)
            assertEquals(30L, model.clearElapsedMillis)
            model.command(GameCommand.HARD_DROP)
            model.pointerDown(0f, 0f, now)
            model.pointerUp(0f, 0f, now + 1)
            assertEquals(locked, model.game)
            now += 30
            model.advanceFrame(now)
        }
        val white = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        assertEquals(Color(0xFF131D32), white[white.width / 20, white.height * 39 / 40])
        assertNotEquals(Color(0xFF131D32), white[white.width * 19 / 20, white.height * 39 / 40])
        compose.runOnIdle {
            model.pause()
            now += 5000
            model.advanceFrame(now)
            assertEquals(60L, model.clearElapsedMillis)
            model.menu()
            now += 5000
            model.advanceFrame(now)
            assertEquals(60L, model.clearElapsedMillis)
            model.resume()
            now += 539
            model.advanceFrame(now)
            assertEquals(0, model.game!!.generation)
            now += 1
            model.advanceFrame(now)
            assertTrue(model.game!!.clearingRows.isEmpty())
            assertEquals(1, model.game!!.generation)
            assertEquals(100, model.game!!.score)
            assertEquals(Tetromino.T, model.game!!.active.type)
            model.advanceFrame(now)
            assertEquals(100, model.game!!.score)
        }
        val cleared = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        assertEquals(Color(0xFF131D32), cleared[cleared.width / 20, cleared.height * 39 / 40])
    }

    /** Новая партия сбрасывает ещё не завершённую очистку и не получает старые очки. */
    @Test fun newGameCancelsPendingClear() {
        var now = 1000L
        val model = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), almostFull(), { now }, false)
        compose.runOnIdle {
            model.command(GameCommand.TICK)
            now += 100
            model.advanceFrame(now)
            model.newGame()
            assertEquals(0L, model.clearElapsedMillis)
            assertTrue(model.game!!.clearingRows.isEmpty())
            now += 300
            model.advanceFrame(now)
            assertEquals(0, model.game!!.score)
            assertEquals(0, model.game!!.generation)
        }
    }
}





