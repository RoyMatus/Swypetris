package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockDelayTest {
    private val engine = GameEngine()
    private fun floor() = GameState(active = Piece(Tetromino.O, y = 18), next = Tetromino.T)

    @Test fun landingWaitsExactlyFiveHundredMilliseconds() {
        val landed = engine.apply(floor().copy(active = Piece(Tetromino.O, y = 17)), GameCommand.TICK)
        assertTrue(engine.grounded(landed))
        assertEquals(0, landed.generation)
        assertEquals(landed, engine.apply(landed, GameCommand.TICK))
        val waiting = engine.advanceLock(landed, 499)
        assertEquals(1L, waiting.lockRemaining)
        assertEquals(0, waiting.generation)
        assertEquals(1, engine.advanceLock(waiting, 1).generation)
        assertEquals(1, engine.apply(landed, GameCommand.HARD_DROP).generation)
    }

    @Test fun onlySuccessfulGroundedActionsResetAndTheBudgetStopsAtFifteen() {
        var state = floor()
        repeat(15) { index ->
            state = engine.advanceLock(state, 100)
            state = engine.apply(state, if (index % 2 == 0) GameCommand.LEFT else GameCommand.RIGHT)
            assertEquals(500L, state.lockRemaining)
            assertEquals(index + 1, state.lockResets)
        }
        state = engine.advanceLock(state, 499)
        state = engine.apply(state, GameCommand.CLOCKWISE)
        assertEquals(1L, state.lockRemaining)
        assertEquals(15, state.lockResets)
        assertEquals(1, engine.advanceLock(state, 1).generation)
        val edge = floor().copy(active = Piece(Tetromino.O, x = 0, y = 18), lockRemaining = 200)
        assertEquals(edge, engine.apply(edge, GameCommand.LEFT))
        val turned = engine.apply(edge, GameCommand.CLOCKWISE)
        assertEquals(500L, turned.lockRemaining)
        assertEquals(1, turned.lockResets)
        assertEquals(edge.active.cells(), turned.active.cells())
    }

    @Test fun airborneClockFreezesWithoutRestoringTheBudget() {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        board[BoardGeometry.row(10)][4] = Tetromino.J
        var state = floor().copy(board = board, active = Piece(Tetromino.O, x = 4, y = 8), lockResets = 15)
        state = engine.advanceLock(state, 300)
        state = engine.apply(engine.apply(state, GameCommand.RIGHT), GameCommand.RIGHT)
        assertFalse(engine.grounded(state))
        assertEquals(state, engine.advanceLock(state, 10000))
        repeat(10) { state = engine.apply(state, GameCommand.TICK) }
        assertTrue(engine.grounded(state))
        assertEquals(200L, state.lockRemaining)
        assertEquals(15, state.lockResets)
        assertEquals(1, engine.advanceLock(state, 200).generation)
    }

    @Test fun pauseCheckpointCannotLockOrConsumeAirborneTime() {
        val paused = engine.advanceLock(floor(), 700, allowLock = false)
        assertEquals(0L, paused.lockRemaining)
        assertEquals(0, paused.generation)
        assertEquals(1, engine.advanceLock(paused, 0).generation)
        val air = floor().copy(active = Piece(Tetromino.O, y = 4))
        assertEquals(air, engine.advanceLock(air, 10000))
    }
}
