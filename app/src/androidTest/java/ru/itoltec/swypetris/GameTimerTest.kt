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
        assertEquals(now + 1000, timer.deadline)
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
        assertEquals(2000L, timer.deadline)
        now += 217
        model.command(GameCommand.RIGHT)
        assertEquals(1, timer.scheduled)
        assertEquals(2000L, timer.deadline)
        model.pause()
        assertNull(timer.action)
        now += 10000
        model.resume()
        assertEquals(now + 783, timer.deadline)
        timer.fire()
        assertEquals(1, model.game!!.active.y)
        assertEquals(1000L, SessionStore(GameStorage.sessionPreferences(app)).read()!!.playedMillis)
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

    @Test fun dropPointsDoNotChangeLevelOrGravityDeadline() = main {
        val model = model(state().copy(score = 999))
        now += 200
        model.command(GameCommand.SOFT_DROP)
        assertEquals(1000L + model.game!!.gravityMillis, timer.deadline)
        assertEquals(2000L, timer.deadline)
        assertEquals(1, model.game!!.level)
        model.pause()
    }

    @Test fun clearingThresholdSchedulesNewLevelAndRestoresIt() = main {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        for (x in 0..7) board[BoardGeometry.row(19)][x] = Tetromino.J
        val model = model(state().copy(board = board, active = Piece(Tetromino.O,x=8,y=18), lines = 9))
        model.command(GameCommand.HARD_DROP)
        repeat(10) { timer.fire() }
        assertEquals(10, model.game!!.lines)
        assertEquals(2, model.game!!.level)
        assertEquals(100, model.game!!.score)
        assertEquals(now + 928L, timer.deadline)
        model.pause()
        val restored = GameViewModel(app, null, { now }, false)
        assertEquals(model.game, restored.game)
        assertEquals(2, restored.game!!.level)
        assertEquals(928L, restored.game!!.gravityMillis)
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

    @Test fun holdReadinessSchedulesOnceWithoutPostponingGravity() = main {
        val model = model(state())
        val initial = model.game
        model.pointerDown(100f, 200f, now)
        assertEquals(now + 300, timer.deadline)
        timer.fire()
        assertEquals(initial, model.game)
        assertEquals(2000L, timer.deadline)
        model.pointerUp(100f, 200f, now)
        assertEquals(initial, model.game)
        model.pause()
    }

    @Test fun highGravityRestoresFractionalLockClockWithoutGroundedGravityCallbacks() = main {
        val original = model(state().copy(active = Piece(Tetromino.O, y = 17), lines = 540))
        assertEquals(now + 1, timer.deadline)
        timer.fire()
        assertEquals(18, original.game!!.active.y)
        assertEquals(now + 500, timer.deadline)
        now += 217
        original.pause()
        val snapshot = SessionStore(GameStorage.sessionPreferences(app)).read()!!
        assertEquals(283L, snapshot.state.lockRemaining)
        assertEquals(166_666L, snapshot.lockFractionNanos)
        now += 10000
        val restored = model()
        assertEquals(original.game, restored.game)
        restored.resume()
        assertEquals(now + 283, timer.deadline)
        timer.fire()
        assertEquals(1, restored.game!!.generation)
        assertEquals(now + 1, timer.deadline)
        val spawned = SessionStore(GameStorage.sessionPreferences(app)).read()!!
        assertEquals(666_668L, spawned.gravityRemainingNanos)
        restored.pause()
    }

    @Test fun lateCallbackRecordsOnlyTimeBeforeGameOver() = main {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        board[BoardGeometry.row(0)][4] = Tetromino.Z
        val model = model(state().copy(board = board, active = Piece(Tetromino.O, x = 0, y = 18), score = 50))
        now += 1000
        model.advanceFrame(now)
        assertTrue(model.game!!.gameOver)
        assertEquals(500L, model.latestResult!!.durationMillis)
    }
}
