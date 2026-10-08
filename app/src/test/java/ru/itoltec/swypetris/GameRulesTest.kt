package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверяет общую арифметику и переходы движка на границах новых правил. */
class GameRulesTest {
    @Test fun lineThresholdsAndActualScoreDisplay() {
        for (lines in listOf(0,8,9,10,19,20,99,100,Int.MAX_VALUE)) {
            assertEquals(1 + lines / 10, GameRules.level(lines))
            assertEquals(lines % 10 == 9, GameRules.nearingLevel(lines))
            assertEquals((lines % 10) / 10f, GameRules.progress(lines), .00001f)
        }
        assertEquals(10L, GameRules.threshold(2))
        assertEquals(90L, GameRules.threshold(10))
        assertTrue(GameRules.nextThreshold(Int.MAX_VALUE) > Int.MAX_VALUE.toLong())
        for (score in listOf(899,900,999,1000,2125,Int.MAX_VALUE))
            assertEquals("$score", displayScore(score))
        assertEquals(Int.MAX_VALUE, GameRules.add(Int.MAX_VALUE, 1500))
    }

    /** Разные способы спуска суммарно оплачивают пройденное расстояние ровно один раз. */
    @Test fun mixedDescentAndBlockedLock() {
        val engine = GameEngine()
        var state = GameState(active = Piece(Tetromino.O), next = Tetromino.T)
        state = engine.apply(state, GameCommand.TICK)
        assertEquals(0, state.score)
        assertFalse(state.accelerated)
        state = engine.apply(state, GameCommand.SOFT_DROP)
        assertEquals(1, state.score)
        assertTrue(state.accelerated)
        state = engine.apply(state, GameCommand.HARD_DROP)
        assertEquals(33, state.score)
        assertFalse(state.accelerated)
        val floor = GameState(active = Piece(Tetromino.O, y = 18), next = Tetromino.T, score = 18)
        for (command in listOf(GameCommand.TICK, GameCommand.SOFT_DROP, GameCommand.HARD_DROP)) {
            assertEquals(18, engine.apply(floor, command).score)
        }
        assertFalse(engine.newGame().accelerated)
    }

    /** Ускорение сохраняется до гравитационной фиксации, но очистка имеет приоритет. */
    @Test fun acceleratedLandingAndClearPriority() {
        val engine = GameEngine()
        val start = GameState(active = Piece(Tetromino.O, y = 17), next = Tetromino.T)
        val soft = engine.apply(start, GameCommand.SOFT_DROP)
        assertNull(feedbackEvent(start, soft, GameCommand.SOFT_DROP))
        val landed = engine.advanceLock(soft, 500)
        assertEquals(FeedbackEvent.DROP, feedbackEvent(soft, landed, GameCommand.TICK))
        assertFalse(landed.accelerated)
        val board = soft.board.map { it.toMutableList() }
        for (x in 0..9) if (x !in 4..5) board[BoardGeometry.row(19)][x] = Tetromino.J
        val before = soft.copy(board = board)
        val clearing = engine.advanceLock(before, 500)
        assertEquals(FeedbackEvent.CLEAR, feedbackEvent(before, clearing, GameCommand.TICK))
        val finished = engine.finishClear(clearing)
        assertFalse(finished.accelerated)
        assertNull(feedbackEvent(clearing, finished, GameCommand.TICK))
        assertEquals(101, finished.score)
        assertEquals(finished, engine.finishClear(finished))
    }
}
