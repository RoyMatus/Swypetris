package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {
    @Test fun allBaseAwardsScaleWithCapturedLevel() {
        val cases = listOf(
            Triple(Spin.NONE, 0, 0), Triple(Spin.NONE, 1, 100), Triple(Spin.NONE, 2, 300),
            Triple(Spin.NONE, 3, 500), Triple(Spin.NONE, 4, 800),
            Triple(Spin.MINI, 0, 100), Triple(Spin.MINI, 1, 200), Triple(Spin.MINI, 2, 400),
            Triple(Spin.FULL, 0, 400), Triple(Spin.FULL, 1, 800), Triple(Spin.FULL, 2, 1200), Triple(Spin.FULL, 3,
                1600))
        for ((spin, lines, base) in cases) for (level in listOf(1, 3, 999)) {
            val event = PlacementResult(lines, spin, false, 0, level = level)
            assertEquals(base * level, GameRules.placementScore(event))
            val expected = if (event.difficult) base * 3 / 2 else base
            assertEquals(expected * level, GameRules.placementScore(event.copy(backToBack = true)))
        }
    }

    @Test fun perfectClearAndComboAreAdditiveAfterBaseBackToBack() {
        for (lines in 1..4) {
            val expected = listOf(0,900,1500,2300,2800)[lines]
            val event = PlacementResult(lines, Spin.NONE, false, 0, true, level = 2)
            assertEquals(expected * 2, GameRules.placementScore(event))
            assertEquals(expected * 2 + 300, GameRules.placementScore(event.copy(combo = 3)))
        }
        assertEquals(9000, GameRules.placementScore(PlacementResult(4, Spin.NONE, true, 2, true, level = 2)))
        assertEquals(6150, GameRules.placementScore(PlacementResult(1, Spin.FULL, true, 1, true, level = 3)))
        assertEquals(400, GameRules.placementScore(PlacementResult(0, Spin.FULL, true, 99)))
    }

    @Test fun dropsAndOverflowAreSafe() {
        assertEquals(0, GameRules.dropScore(GameCommand.TICK, 20))
        assertEquals(20, GameRules.dropScore(GameCommand.SOFT_DROP, 20))
        assertEquals(40, GameRules.dropScore(GameCommand.HARD_DROP, 20))
        assertEquals(0, GameRules.dropScore(GameCommand.HARD_DROP, 0))
        assertEquals(Int.MAX_VALUE, GameRules.dropScore(GameCommand.HARD_DROP, Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, GameRules.placementScore(
            PlacementResult(4, Spin.NONE, true, Int.MAX_VALUE, true, level = Int.MAX_VALUE)))
        assertEquals(Int.MAX_VALUE, GameRules.add(Int.MAX_VALUE - 1, 900))
    }

    private fun fullZero(): GameState {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        for ((x,y) in listOf(3 to 17,5 to 17,3 to 19)) board[BoardGeometry.row(y)][x] = Tetromino.J
        return GameState(board = board, active = Piece(Tetromino.T,x=3,y=17,rotation=3), next = Tetromino.O)
    }
    @Test fun zeroSpinScoresAtLockAndOnlyOnce() {
        val engine = GameEngine()
        val rotated = engine.apply(fullZero(), GameCommand.CLOCKWISE)
        val locked = engine.apply(rotated, GameCommand.HARD_DROP)
        assertEquals(400, locked.score)
        assertEquals(Spin.FULL, locked.placement!!.spin)
        assertEquals(locked, engine.finishClear(locked))
        val tick = engine.apply(locked, GameCommand.TICK)
        assertEquals(400, tick.score)
    }

    @Test fun clearUsesCapturedLevelAndCannotBeAwardedTwice() {
        val engine = GameEngine()
        val board = BoardGeometry.empty().map { it.toMutableList() }
        for (y in 16..19) for (x in 0..9) if (x != 4) board[BoardGeometry.row(y)][x] = Tetromino.J
        board[BoardGeometry.row(10)][0] = Tetromino.L
        val start = GameState(board = board, active = Piece(Tetromino.I,x=2,y=16,rotation=1), next = Tetromino.O)
        val pending = engine.apply(start, GameCommand.HARD_DROP)
        assertEquals(0, pending.score)
        assertEquals(1, pending.placement!!.level)
        val finished = engine.finishClear(pending.copy(score = 10000, lines = 100))
        assertEquals(10800, finished.score)
        assertEquals(finished, engine.finishClear(finished))
    }
}
