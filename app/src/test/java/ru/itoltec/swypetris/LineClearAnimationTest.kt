package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test

/** Проверяет последовательность удаления и чередование направлений без реальных задержек. */
class LineClearAnimationTest {
    /** Каждые 60 мс исчезает ровно одна дополнительная клетка с нужного края. */
    @Test fun columnsDisappearInBothDirections() {
        for (event in 0..3) for (step in 0..10) {
            val removed = (0..9).filter { LineClearAnimation.isRemoved(it, step * 60L, event) }
            assertEquals(if (event % 2 == 0) (0 until step).toList() else (10 - step..9).toList(), removed)
            if (step > 0) assertEquals(step - 1, (0..9).count { LineClearAnimation.isRemoved(it, step * 60L - 1, event) })
        }
    }

    /** Завершённая очистка переключает направление один раз, новая партия начинает слева. */
    @Test fun completionAlternatesAndNewGameResets() {
        val engine = GameEngine()
        var state = engine.newGame()
        repeat(4) { event ->
            val board = List(20) { y -> List<Tetromino?>(10) { if (y == 19) Tetromino.O else null } }
            state = engine.finishClear(state.copy(board = board, clearingRows = listOf(19)))
            assertEquals(event + 1, state.completedClears)
            assertEquals(state, engine.finishClear(state))
        }
        assertEquals(0, engine.newGame().completedClears)
    }
}

