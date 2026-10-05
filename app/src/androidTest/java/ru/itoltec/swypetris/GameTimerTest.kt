package ru.itoltec.swypetris

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Executes the real scheduling path with a deterministic main-thread timer. */
class GameTimerTest {
    @get:Rule val storage = IsolatedStorageRule()
    private var now = 1000L
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val timer = FakeTimer()

    private inner class FakeTimer : GameTimer {
        var deadline: Long? = null
        var action: (() -> Unit)? = null
        var scheduled = 0
        override fun schedule(delayMillis: Long, action: () -> Unit) {
            check(this.action == null || deadline != now + delayMillis)
            deadline = now + delayMillis
            this.action = action
            scheduled++
        }
        override fun cancel() { deadline = null; action = null }
        fun fire() {
            now = requireNotNull(deadline)
            val callback = requireNotNull(action)
            cancel()
            callback()
        }
    }

    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun model(state: GameState? = null) = GameViewModel(app, state, { now }, false, timer = timer)
    private fun state() = GameState(active = Piece(Tetromino.T), next = Tetromino.O)

    @Test fun staticScreensAndBackgroundHaveNoScheduledEvents() = main {
        val model = model()
        assertNull(timer.action)
        model.newGame()
        assertEquals(now + 800, timer.deadline)
        model.menu()
        assertNull(timer.action)
        model.help()
        assertNull(timer.action)
        model.resume()
        assertNotNull(timer.action)
        model.onWindowFocusChanged(false)
        assertNull(timer.action)
        model.onBackground()
        model.onWindowFocusChanged(true)
        model.onForeground()
        assertNull(timer.action)
        model.resume()
        assertNotNull(timer.action)
        model.pause()
    }

    @Test fun movementDoesNotPostponeGravityAndPausePreservesItsRemainder() = main {
        val model = model(state())
        assertEquals(1800L, timer.deadline)
        now += 217
        model.command(GameCommand.RIGHT)
        assertEquals(1, timer.scheduled)
        assertEquals(1800L, timer.deadline)
        model.pause()
        assertNull(timer.action)
        now += 10000
        model.resume()
        assertEquals(now + 583, timer.deadline)
        timer.fire()
        assertEquals(1, model.game!!.active.y)
        assertEquals(800L, SessionStore(GameStorage.sessionPreferences(app)).read()!!.playedMillis)
        model.pause()
    }

    @Test fun clearOnlySchedulesVisibleStepsAndRestoresFractionalProgress() = main {
        val board = List(BoardGeometry.TOTAL_ROWS) { y -> List<Tetromino?>(10) { x -> if (y == BoardGeometry.row(19) && x < 8) Tetromino.J else null } }
        val model = model(state().copy(board = board, active = Piece(Tetromino.O, x = 8, y = 18)))
        model.command(GameCommand.HARD_DROP)
        assertEquals(now + 60, timer.deadline)
        repeat(3) { timer.fire() }
        assertEquals(180L, model.clearElapsedMillis)
        now += 37
        model.pause()
        assertEquals(217L, model.clearElapsedMillis)
        now += 10000
        model.resume()
        assertEquals(now + 23, timer.deadline)
        repeat(7) { timer.fire() }
        assertTrue(model.game!!.clearingRows.isEmpty())
        assertEquals(1, model.game!!.generation)
        assertEquals(now + model.game!!.gravityMillis, timer.deadline)
        model.pause()
    }

    @Test fun scoreChangingSpeedReschedulesDeadline() = main {
        val model = model(state().copy(score = 999))
        now += 200
        model.command(GameCommand.SOFT_DROP)
        assertEquals(1000L + model.game!!.gravityMillis, timer.deadline)
        assertTrue(timer.deadline!! < 1800L)
        model.pause()
    }

    @Test fun terminalStatesCancelTimerAndNextRoundSchedulesAgain() = main {
        val model = model(state().copy(score = GameRules.ROUND_SCORE - 1))
        model.command(GameCommand.SOFT_DROP)
        assertEquals(GameScreen.VICTORY, model.screen)
        assertNull(timer.action)
        model.nextRound()
        assertNotNull(timer.action)
        model.pause()
        val board = state().board.map { it.toMutableList() }
        board[BoardGeometry.row(0)][4] = Tetromino.Z
        val losing = model(state().copy(board = board, active = Piece(Tetromino.O, x = 0, y = 18)))
        timer.fire()
        assertTrue(losing.game!!.gameOver)
        assertNull(timer.action)
    }

    @Test fun clearingModelCancelsItsPendingCallback() = main {
        val model = model(state())
        val store = androidx.lifecycle.ViewModelStore()
        store.put("game", model)
        assertNotNull(timer.action)
        store.clear()
        assertNull(timer.action)
    }

    @Test fun lockDeadlineFreezesOnBackgroundAndRestoresItsResetBudget() = main {
        val initial = state().copy(active = Piece(Tetromino.O, y = 18), lockResets = 3)
        val original = model(initial)
        assertEquals(now + 500, timer.deadline)
        now += 217
        original.onBackground()
        assertNull(timer.action)
        assertEquals(283L, original.game!!.lockRemaining)
        now += 10000
        val restored = model()
        assertEquals(283L, restored.game!!.lockRemaining)
        assertEquals(3, restored.game!!.lockResets)
        restored.resume()
        assertEquals(now + 283, timer.deadline)
        timer.fire()
        assertEquals(1, restored.game!!.generation)
        assertEquals(0, restored.game!!.lockResets)
        restored.pause()
    }
}
