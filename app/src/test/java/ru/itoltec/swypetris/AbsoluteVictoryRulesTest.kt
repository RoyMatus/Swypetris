package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Terminal score must freeze the session without starting another round or drawing another piece. */
class AbsoluteVictoryRulesTest {
    private val engine = GameEngine()
    private fun nearMaximum() = GameState(active = Piece(Tetromino.O), next = Tetromino.T,
        score = GameRules.MAX_SCORE - 2, completedRounds = 12)

    @Test fun exactThresholdFreezesEveryCommandAndRound() {
        val before = engine.apply(nearMaximum(), GameCommand.SOFT_DROP)
        assertFalse(before.absoluteVictory)
        assertFalse(before.victoryPending)
        val won = engine.apply(before, GameCommand.SOFT_DROP)
        assertEquals(GameRules.MAX_SCORE, won.score)
        assertTrue(won.absoluteVictory)
        assertTrue(won.victoryPending)
        assertFalse(won.gameOver)
        assertEquals(3, won.roundFruits)
        GameCommand.entries.forEach { assertEquals(won, engine.apply(won, it)) }
        assertEquals(won, engine.advanceLock(won, Long.MAX_VALUE))
        assertEquals(won, engine.finishClear(won))
        assertEquals(won, engine.nextRound(won))
        assertFalse(engine.newGame().absoluteVictory)
    }

    @Test fun hardDropCapsScoreWithoutSpawningOrDrawing() {
        engine.newGame()
        val bag = engine.remainingBag()
        val won = engine.apply(nearMaximum(), GameCommand.HARD_DROP)
        assertEquals(GameRules.MAX_SCORE, won.score)
        assertTrue(won.absoluteVictory)
        assertEquals(0, won.generation)
        assertEquals(bag, engine.remainingBag())
    }

    @Test fun clearOvershootCapsScoreButPreservesAwardedLines() {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        for (x in 0..9) if (x !in 3..6) board[BoardGeometry.row(19)][x] = Tetromino.J
        val start = nearMaximum().copy(board = board, active = Piece(Tetromino.I, x = 3, y = 18))
        val pending = engine.apply(start, GameCommand.HARD_DROP)
        assertFalse(pending.absoluteVictory)
        assertEquals(listOf(BoardGeometry.row(19)), pending.clearingRows)
        val won = engine.finishClear(pending)
        assertTrue(won.absoluteVictory)
        assertEquals(GameRules.MAX_SCORE, won.score)
        assertEquals(1, won.lines)
        assertEquals(start.generation, won.generation)
        assertEquals(won, engine.finishClear(won))
    }

    @Test fun terminalVictoryWinsOverLossAndNormalizesOlderHighScores() {
        val over = nearMaximum().copy(score = Int.MAX_VALUE, gameOver = true, topOut = TopOut.BLOCK_OUT)
        val won = engine.checkVictory(over)
        assertEquals(GameRules.MAX_SCORE, won.score)
        assertTrue(won.victoryPending)
        assertFalse(won.gameOver)
        assertEquals(null, won.topOut)
    }

    @Test fun scoreCapDoesNotCapLineCountersOrChangeLowerAwards() {
        assertEquals(42, GameRules.addScore(40, 2))
        assertEquals(40, GameRules.addScore(40, -2))
        assertEquals(GameRules.MAX_SCORE, GameRules.addScore(GameRules.MAX_SCORE - 1, Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, GameRules.add(Int.MAX_VALUE - 1, 2))
    }
}
