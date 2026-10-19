package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for retiring the former terminal score. */
class AbsoluteVictoryRulesTest {
    private val engine = GameEngine()

    @Test fun hardDropCrossesFormerCapAndSpawnsNormally() {
        val state = GameState(active = Piece(Tetromino.O), next = Tetromino.T,
            score = 999_998, completedRounds = 12)
        val dropped = engine.apply(state, GameCommand.HARD_DROP)
        assertTrue(dropped.score > 999_999)
        assertEquals(1, dropped.generation)
        assertFalse(dropped.victoryPending)
        assertFalse(dropped.gameOver)
    }

    @Test fun numericBoundaryNeverWrapsAndNeverOverridesOrdinaryLoss() {
        assertEquals(Int.MAX_VALUE.toLong() + 2, GameRules.addScore(Int.MAX_VALUE.toLong(), 2))
        assertEquals(Long.MAX_VALUE, GameRules.addScore(Long.MAX_VALUE - 1, 2))
        assertEquals(40L, GameRules.addScore(40, -2))
        val lost = GameState(active = Piece(Tetromino.O), next = Tetromino.T,
            score = Long.MAX_VALUE, completedRounds = Long.MAX_VALUE / GameRules.ROUND_SCORE,
            gameOver = true, topOut = TopOut.BLOCK_OUT)
        assertEquals(lost, engine.apply(lost, GameCommand.SOFT_DROP))
        assertEquals(lost, engine.checkVictory(lost))
        assertTrue(lost.fruitCounts.all { it >= 0 })
    }
}
