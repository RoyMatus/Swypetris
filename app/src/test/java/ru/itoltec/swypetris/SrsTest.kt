package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsTest {
    private val engine = GameEngine()
    private fun state(piece: Piece) = GameState(active = piece, next = Tetromino.O)

    @Test fun everyPieceSupportsAllEightTransitionsWithoutTranslationInOpenSpace() {
        for (type in Tetromino.entries) for (rotation in 0..3) {
            for (command in listOf(GameCommand.CLOCKWISE, GameCommand.COUNTERCLOCKWISE)) {
                val start = state(Piece(type, y = 5, rotation = rotation))
                val result = engine.apply(start, command)
                val expected = (rotation + if (command == GameCommand.CLOCKWISE) 1 else 3) % 4
                assertEquals("$type $rotation $command", start.active.copy(rotation = expected), result.active)
                if (type == Tetromino.O) assertEquals(start.active.cells(), result.active.cells())
            }
        }
    }

    @Test fun clockwiseAndCounterclockwiseWallKicksUseDifferentTransitions() {
        val left = state(Piece(Tetromino.T, x = -1, y = 5, rotation = 1))
        for (command in listOf(GameCommand.CLOCKWISE, GameCommand.COUNTERCLOCKWISE)) {
            assertTrue(engine.fits(left, left.active))
            val result = engine.apply(left, command)
            assertEquals(0, result.active.x)
            assertEquals(5, result.active.y)
            assertEquals(if (command == GameCommand.CLOCKWISE) 2 else 0, result.active.rotation)
            assertTrue(engine.fits(result, result.active))
        }
        val right = state(Piece(Tetromino.T, x = 8, y = 5, rotation = 3))
        assertEquals(7, engine.apply(right, GameCommand.CLOCKWISE).active.x)
        assertEquals(7, engine.apply(right, GameCommand.COUNTERCLOCKWISE).active.x)
    }

    @Test fun floorKicksMoveUpwardAndUseSeparateIAndJlstzOffsets() {
        val t = state(Piece(Tetromino.T, x = 3, y = 18))
        assertEquals(Piece(Tetromino.T, x = 2, y = 17, rotation = 1), engine.apply(t, GameCommand.CLOCKWISE).active)
        assertEquals(Piece(Tetromino.T, x = 4, y = 17, rotation = 3), engine.apply(t,
            GameCommand.COUNTERCLOCKWISE).active)
        val i = state(Piece(Tetromino.I, x = 3, y = 18))
        assertEquals(Piece(Tetromino.I, x = 4, y = 16, rotation = 1), engine.apply(i, GameCommand.CLOCKWISE).active)
        assertEquals(Piece(Tetromino.I, x = 2, y = 16, rotation = 3), engine.apply(i,
            GameCommand.COUNTERCLOCKWISE).active)
    }

    @Test fun rotationWorksInHiddenRowsAndBlockedCandidatesLeaveStateUntouched() {
        val hidden = state(Piece(Tetromino.T, y = -2))
        val rotated = engine.apply(hidden, GameCommand.CLOCKWISE)
        assertEquals(-2, rotated.active.y)
        assertEquals(1, rotated.active.rotation)
        val start = state(Piece(Tetromino.T, y = 5))
        val board = start.board.map { it.toMutableList() }
        for (y in 2..10) for (x in 0..9) board[BoardGeometry.row(y)][x] = Tetromino.J
        start.active.cells().forEach { board[BoardGeometry.row(it.y)][it.x] = null }
        val blocked = start.copy(board = board)
        assertTrue(engine.fits(blocked, blocked.active))
        assertEquals(blocked, engine.apply(blocked, GameCommand.CLOCKWISE))
        assertEquals(blocked, engine.apply(blocked, GameCommand.COUNTERCLOCKWISE))
    }
}
