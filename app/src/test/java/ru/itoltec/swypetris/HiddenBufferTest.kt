package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test

class HiddenBufferTest {
    private val engine = GameEngine()

    @Test fun spawnUsesHiddenAreaAndFullMatrix() {
        val state = engine.newGame()
        assertEquals(40, state.board.size)
        assertTrue(engine.fits(state, state.active))
        assertTrue(state.active.cells().any { it.y < 0 })
        assertEquals(20, state.board.drop(BoardGeometry.HIDDEN_ROWS).size)
    }

    @Test fun collisionAndGhostUseHiddenRows() {
        val state = GameState(active = Piece(Tetromino.O, x = 0, y = -3), next = Tetromino.T)
        val board = state.board.map { it.toMutableList() }
        board[BoardGeometry.row(-1)][0] = Tetromino.J
        val stacked = state.copy(board = board)
        assertEquals(-3, engine.ghost(stacked).y)
        assertFalse(engine.fits(stacked, state.active.copy(y = -2)))
        val lost = engine.apply(stacked, GameCommand.HARD_DROP)
        assertTrue(lost.gameOver)
        assertEquals(TopOut.LOCK_OUT, lost.topOut)
    }

    @Test fun partialHiddenLockIsAllowedButBlockedSpawnIsDistinct() {
        val state = GameState(active = Piece(Tetromino.O, x = 0, y = -1), next = Tetromino.O)
        val board = state.board.map { it.toMutableList() }
        board[BoardGeometry.row(1)][0] = Tetromino.J
        val placed = engine.apply(state.copy(board = board), GameCommand.HARD_DROP)
        assertFalse(placed.gameOver)
        board[BoardGeometry.row(-1)][4] = Tetromino.Z
        val blocked = engine.apply(state.copy(board = board), GameCommand.HARD_DROP)
        assertEquals(TopOut.BLOCK_OUT, blocked.topOut)
    }

    @Test fun clearShiftsHiddenCellsIntoVisibleArea() {
        val state = GameState(active = Piece(Tetromino.I), next = Tetromino.O)
        val board = state.board.map { it.toMutableList() }
        board[BoardGeometry.row(-1)][0] = Tetromino.T
        board[BoardGeometry.row(19)].fill(Tetromino.J)
        val result = engine.finishClear(state.copy(board = board, clearingRows = listOf(BoardGeometry.row(19))))
        assertEquals(40, result.board.size)
        assertEquals(Tetromino.T, result.board[BoardGeometry.row(0)][0])
        assertTrue(result.board.first().all { it == null })
    }
}
