package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test

/** Проверяет выбор событий без динамика и вибромотора. */
class GameFeedbackTest {
    /** Обычное приземление тихое, бросок вызывает удар, очистка заменяет удар одним эффектом. */
    @Test fun feedbackOnlyForDropAndClearStart() {
        val engine = GameEngine()
        val state = GameState(active = Piece(Tetromino.O, y = 18), next = Tetromino.T)
        assertNull(feedbackEvent(state, engine.apply(state, GameCommand.TICK), GameCommand.TICK))
        assertNull(feedbackEvent(state, engine.apply(state, GameCommand.SOFT_DROP), GameCommand.SOFT_DROP))
        assertEquals(FeedbackEvent.DROP, feedbackEvent(state, engine.apply(state, GameCommand.HARD_DROP), GameCommand.HARD_DROP))
        val board = state.board.map { it.toMutableList() }
        for (x in 0..9) if (x !in 4..5) board[19][x] = Tetromino.J
        val before = state.copy(board = board)
        val clearing = engine.apply(before, GameCommand.HARD_DROP)
        assertEquals(FeedbackEvent.CLEAR, feedbackEvent(before, clearing, GameCommand.HARD_DROP))
        assertNull(feedbackEvent(clearing, engine.apply(clearing, GameCommand.HARD_DROP), GameCommand.HARD_DROP))
        assertNull(feedbackEvent(clearing, engine.finishClear(clearing), GameCommand.TICK))
    }
}
