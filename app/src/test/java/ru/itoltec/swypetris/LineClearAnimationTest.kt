package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test

/** Presentation never changes canonical clear timing or the source board. */
class LineClearAnimationTest {
    @Test fun phasesStayWithinSixHundredMilliseconds() {
        assertEquals(600L, LineClearAnimation.TOTAL_MILLIS)
        assertFalse(LineClearAnimation.isRemoved(79))
        assertTrue(LineClearAnimation.isRemoved(80))
        assertTrue(LineClearAnimation.highlight(40) > 0f)
        assertEquals(0f, LineClearAnimation.highlight(80), 0f)
        assertTrue(LineClearAnimation.shardAlpha(80) > 0f)
        assertEquals(0f, LineClearAnimation.shardAlpha(360), 0f)
        for (elapsed in -100L..800L) {
            assertTrue(LineClearAnimation.burstProgress(elapsed) in 0f..1f)
            assertTrue(LineClearAnimation.settleProgress(elapsed) in 0f..1f)
            assertTrue(LineClearAnimation.shardAlpha(elapsed) in 0f..1f)
        }
        assertEquals(0f, LineClearAnimation.settleProgress(360), 0f)
        assertEquals(.5f, LineClearAnimation.settleProgress(480), .001f)
        assertEquals(1f, LineClearAnimation.settleProgress(600), 0f)
    }

    @Test fun shardsAreBoundedDeterministicAndUseOnlySourceCells() {
        for (rows in listOf(listOf(19), listOf(18, 19), listOf(17, 18, 19),
            listOf(16, 17, 18, 19), listOf(3, 17, 19))) {
            val cleared = rows.map(BoardGeometry::row)
            val board = BoardGeometry.empty().mapIndexed { row, cells ->
                if (row in cleared) List(10) { Tetromino.entries[it % 7] } else cells
            }
            val state = GameState(board = board, active = Piece(Tetromino.O), next = Tetromino.T,
                clearingRows = cleared, completedClears = 3)
            val shards = LineClearAnimation.shards(state)
            assertEquals(rows.size * 40, shards.size)
            assertTrue(shards.size <= 160)
            assertEquals(shards, LineClearAnimation.shards(state.copy()))
            shards.forEach {
                assertTrue(it.row in cleared)
                assertEquals(board[it.row][it.column], it.type)
                assertTrue(it.velocityY < 0f)
            }
            assertEquals(board, state.board)
        }
    }

    @Test fun nonAdjacentRowsAndHiddenCellsSettleToTheCanonicalResult() {
        val rows = listOf(3, 17, 19).map(BoardGeometry::row)
        for (row in 0 until BoardGeometry.TOTAL_ROWS) {
            val targetShift = rows.count { it > row }.toFloat()
            assertEquals(targetShift, LineClearAnimation.rowShift(row, rows, 600, false), 0f)
            assertEquals(targetShift / 2, LineClearAnimation.rowShift(row, rows, 480, false), .001f)
            assertEquals(0f, LineClearAnimation.rowShift(row, rows, 599, true), 0f)
        }
    }

    /** Завершённая очистка переключает направление один раз, новая партия начинает слева. */
    @Test fun completionAlternatesAndNewGameResets() {
        val engine = GameEngine()
        var state = engine.newGame()
        repeat(4) { event ->
            val board = List(BoardGeometry.TOTAL_ROWS) { y -> List<Tetromino?>(10) { if (y == BoardGeometry.row(19)) Tetromino.O else null } }
            state = engine.finishClear(state.copy(board = board, clearingRows = listOf(19).map(BoardGeometry::row)))
            assertEquals(event + 1, state.completedClears)
            assertEquals(state, engine.finishClear(state))
        }
        assertEquals(0, engine.newGame().completedClears)
    }
}
