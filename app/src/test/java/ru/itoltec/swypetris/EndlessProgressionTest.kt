package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndlessProgressionTest {
    @Test fun formerTerminalScoreAllowsContinuedMovementAndScoring() {
        val engine = GameEngine()
        var state = GameState(active = Piece(Tetromino.O), next = Tetromino.T,
            score = 999_998, completedRounds = 12)
        repeat(4) { state = engine.apply(state, GameCommand.SOFT_DROP) }
        assertEquals(1_000_002L, state.score)
        assertFalse(state.victoryPending)
        assertFalse(state.gameOver)
        assertEquals(4, state.active.y)
    }

    @Test fun ordinaryRoundsStillContinueBeyondFormerCapWithSameSpeed() {
        val engine = GameEngine()
        val state = GameState(active = Piece(Tetromino.O), next = Tetromino.T,
            score = 1_040_000, lines = 900, completedRounds = 12)
        val victory = engine.checkVictory(state)
        assertTrue(victory.victoryPending)
        val next = engine.nextRound(victory)
        assertEquals(state.score, next.score)
        assertEquals(13L, next.completedRounds)
        assertEquals(state.gravityNanos, next.gravityNanos)
        assertFalse(next.victoryPending)
    }
}
