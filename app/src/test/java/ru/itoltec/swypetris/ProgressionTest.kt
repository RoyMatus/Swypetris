package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test

class ProgressionTest {
    private val engine = GameEngine()
    private fun clearStart(count: Int, lines: Int): GameState {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        for (y in 20-count..19) for (x in 0..9) if (x != 4) board[BoardGeometry.row(y)][x] = Tetromino.J
        board[BoardGeometry.row(10)][0] = Tetromino.L // Prevent Perfect Clear bonuses.
        return GameState(board = board, active = Piece(Tetromino.I,x=2,y=16,rotation=1),
            next = Tetromino.O, lines = lines)
    }

    @Test fun thresholdCrossingsUsePreClearLevelThenUpdateGravity() {
        for ((count, lines) in listOf(1 to 9,2 to 8,3 to 9,4 to 9,4 to 19)) {
            val start = clearStart(count,lines)
            val pending = engine.apply(start, GameCommand.HARD_DROP)
            assertEquals(start.level, pending.placement!!.level)
            assertEquals(start.level, pending.level)
            val completed = engine.finishClear(pending)
            assertEquals(GameRules.lineScore(count) * start.level, completed.score)
            assertEquals(1 + (lines + count) / 10, completed.level)
            assertEquals(start.difficulty.gravityMillis(completed.level), completed.gravityMillis)
            assertTrue(completed.gravityMillis < start.gravityMillis)
        }
    }

    @Test fun perfectClearBonusAlsoUsesPreClearLevel() {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        for (x in 0..9) if (x !in 3..6) board[BoardGeometry.row(19)][x] = Tetromino.J
        val start = GameState(board = board, active = Piece(Tetromino.I,x=3,y=18), next = Tetromino.O, lines = 9)
        val completed = engine.finishClear(engine.apply(start, GameCommand.HARD_DROP))
        assertTrue(completed.placement!!.perfectClear)
        assertEquals(900, completed.score)
        assertEquals(2, completed.level)
    }

    @Test fun dropPointsAndFruitsCannotAdvanceLevel() {
        val start = GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 9999, lines = 9)
        val soft = engine.apply(start, GameCommand.SOFT_DROP)
        assertEquals(10000, soft.score)
        assertEquals(1, soft.roundFruits)
        assertEquals(1, soft.level)
        assertEquals(start.gravityMillis, soft.gravityMillis)
        val hard = engine.apply(soft, GameCommand.HARD_DROP)
        assertEquals(1, hard.level)
        val highScore = start.copy(score = 1000000, lines = 0)
        assertEquals(1, highScore.level)
        assertEquals(800L, highScore.gravityMillis)
        assertEquals(2, start.copy(score = 0, lines = 10).level)
    }

    @Test fun roundRetainsLineProgressAndExtremeLinesCannotOverflow() {
        val won = GameState(active = Piece(Tetromino.O), next = Tetromino.T,
            score = 80000, lines = 37, victoryPending = true)
        val next = engine.nextRound(won)
        assertEquals(37, next.lines)
        assertEquals(4, next.level)
        assertEquals(won.gravityMillis, next.gravityMillis)
        val completed = engine.finishClear(engine.apply(clearStart(4,Int.MAX_VALUE-1), GameCommand.HARD_DROP))
        assertEquals(Int.MAX_VALUE, completed.lines)
        assertEquals(Int.MAX_VALUE, completed.score)
        assertEquals(214748365, completed.level)
    }
}
