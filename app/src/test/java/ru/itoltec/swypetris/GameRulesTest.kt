package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test

/** Проверяет общую арифметику и переходы движка на границах новых правил. */
class GameRulesTest {
    /** Граница последних 20% включительна, новый уровень сразу показывает полный счёт. */
    @Test fun thresholdsAndDisplay() {
        val scores = listOf(799, 800, 999, 1000, 1999, 2000, 2249, 2250, 2100)
        val displays = listOf("799", "−200", "−1", "1000", "1999", "−250", "−1", "2250", "−150")
        scores.zip(displays).forEach { (score, display) -> assertEquals(display, GameRules.displayScore(score)) }
        listOf(0L, 1000L, 2250L, 3750L, 5500L, 7500L, 9750L).forEachIndexed { index, score ->
            assertEquals(score, GameRules.threshold(index + 1))
            assertEquals(index + 1, GameRules.level(score.toInt()))
        }
        assertEquals(18000L, GameRules.threshold(10))
        assertEquals(10, GameRules.level(GameRules.add(799, 17201)))
        assertEquals(0.8f, GameRules.progress(2000), 0.00001f)
        assertTrue(GameRules.nextThreshold(Int.MAX_VALUE) > Int.MAX_VALUE.toLong())
        assertEquals(Int.MAX_VALUE, GameRules.add(Int.MAX_VALUE, 1500))
        val clearing = GameState(active = Piece(Tetromino.I), next = Tetromino.T,
            score = 799, clearingRows = listOf(16, 17, 18, 19))
        val crossed = GameEngine().finishClear(clearing)
        assertEquals(2299, crossed.score)
        assertEquals(3, crossed.level)
        assertEquals("2299", GameRules.displayScore(crossed.score))
    }

    /** Разные способы спуска суммарно оплачивают пройденное расстояние ровно один раз. */
    @Test fun mixedDescentAndBlockedLock() {
        val engine = GameEngine()
        var state = GameState(active = Piece(Tetromino.O), next = Tetromino.T)
        state = engine.apply(state, GameCommand.TICK)
        assertEquals(1, state.score)
        assertFalse(state.accelerated)
        state = engine.apply(state, GameCommand.SOFT_DROP)
        assertEquals(2, state.score)
        assertTrue(state.accelerated)
        state = engine.apply(state, GameCommand.HARD_DROP)
        assertEquals(18, state.score)
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
        val landed = engine.apply(soft, GameCommand.TICK)
        assertEquals(FeedbackEvent.DROP, feedbackEvent(soft, landed, GameCommand.TICK))
        assertFalse(landed.accelerated)
        val board = soft.board.map { it.toMutableList() }
        for (x in 0..9) if (x !in 4..5) board[19][x] = Tetromino.J
        val before = soft.copy(board = board)
        val clearing = engine.apply(before, GameCommand.TICK)
        assertEquals(FeedbackEvent.CLEAR, feedbackEvent(before, clearing, GameCommand.TICK))
        val finished = engine.finishClear(clearing)
        assertFalse(finished.accelerated)
        assertNull(feedbackEvent(clearing, finished, GameCommand.TICK))
        assertEquals(101, finished.score)
        assertEquals(finished, engine.finishClear(finished))
    }
}
