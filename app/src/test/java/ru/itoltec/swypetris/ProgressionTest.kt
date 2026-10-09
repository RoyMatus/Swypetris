package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
            assertEquals(GameRules.gravityMillis(completed.level), completed.gravityMillis)
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
        assertEquals(1000L, highScore.gravityMillis)
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
        assertEquals(GameRules.MAX_SCORE, completed.score)
        assertEquals(214748365, completed.level)
    }
    @Test fun higherStartsWaitForCumulativeFirstGoalAndKeepPreClearScoring() {
        for (start in 1..15) {
            val initial = engine.newGame(start)
            assertEquals(start, initial.level)
            assertEquals(0, initial.lines)
            val goal = start * 10
            assertEquals(goal.toLong(), GameRules.nextThreshold(0, start))
            assertFalse(GameRules.nearingLevel(goal - 11, start))
            assertTrue(GameRules.nearingLevel(goal - 1, start))
            assertEquals(start, initial.copy(lines = goal - 1).level)
            assertEquals(start + 1, initial.copy(lines = goal).level)
            assertEquals(start + 2, initial.copy(lines = goal + 10).level)
            val pending = engine.apply(clearStart(4, goal - 1).copy(startingLevel = start), GameCommand.HARD_DROP)
            assertEquals(start, pending.placement!!.level)
            val completed = engine.finishClear(pending)
            assertEquals(800 * start, completed.score)
            assertEquals(start + 1, completed.level)
            assertEquals(0.0f, GameRules.progress(0, start), .00001f)
            assertEquals((goal - 1f) / goal, GameRules.progress(goal - 1, start), .00001f)
        }
    }

    @Test fun higherStartSurvivesRoundAndDropPointsDoNotAdvanceIt() {
        val initial = engine.newGame(startingLevel = 15)
        val dropped = engine.apply(initial, GameCommand.HARD_DROP)
        assertEquals(15, dropped.level)
        val won = initial.copy(score = 80000, lines = 149, victoryPending = true)
        val next = engine.nextRound(won)
        assertEquals(15, next.startingLevel)
        assertEquals(15, next.level)
        assertEquals(149, next.lines)
        assertEquals(won.gravityMillis, next.gravityMillis)
    }
    @Test fun cumulativeFruitCountsFollowEveryTenThousandPointAward() {
        val base = GameState(active = Piece(Tetromino.O), next = Tetromino.T)
        assertEquals(List(Fruit.entries.size) { 0 }, base.fruitCounts)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0, 0), base.copy(score = 10000).fruitCounts)
        assertEquals(List(Fruit.entries.size) { 1 }, base.copy(score = 80000).fruitCounts)
        assertEquals(listOf(2, 1, 1, 1, 1, 1, 1, 1), base.copy(score = 90000).fruitCounts)
        assertEquals(List(Fruit.entries.size) { 2 }, base.copy(score = 160000).fruitCounts)
        assertEquals(listOf(3, 2, 2, 2, 2, 2, 2, 2), base.copy(score = 170000).fruitCounts)
        assertEquals(1, base.copy(score = 179999, completedRounds = 2).roundFruits)
        assertEquals(2, base.copy(score = 180000, completedRounds = 2).roundFruits)
    }

}
