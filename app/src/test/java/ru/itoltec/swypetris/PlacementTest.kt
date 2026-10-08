package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementTest {
    private val engine = GameEngine()
    private fun state(piece: Piece, cells: List<Cell>) = GameState(
        board = BoardGeometry.empty().mapIndexed { row, values -> values.mapIndexed { x, _ ->
            if (Cell(x, row - BoardGeometry.HIDDEN_ROWS) in cells) Tetromino.J else null
        } }, active = piece, next = Tetromino.O)
    private fun spinStart(mini: Boolean = false) = state(Piece(Tetromino.T, x = 3, y = 17, rotation = 3),
        if (mini) listOf(Cell(3,17), Cell(3,19), Cell(5,19))
        else listOf(Cell(3,17), Cell(5,17), Cell(3,19)))

    @Test fun finalRotationRequiredAndFailedMovementPreservesIt() {
        val start = spinStart()
        val rotated = engine.apply(start, GameCommand.CLOCKWISE)
        assertEquals(0, rotated.lastRotationKick)
        assertEquals(Spin.FULL, engine.apply(rotated, GameCommand.HARD_DROP).placement!!.spin)
        assertEquals(rotated, engine.apply(rotated, GameCommand.LEFT))
        assertEquals(Spin.FULL, engine.apply(engine.apply(rotated, GameCommand.LEFT),
            GameCommand.HARD_DROP).placement!!.spin)
        assertEquals(Spin.NONE, engine.apply(rotated.copy(lastRotationKick = -1),
            GameCommand.HARD_DROP).placement!!.spin)
        val open = GameState(active = Piece(Tetromino.T, y = 5), next = Tetromino.O)
        val qualified = engine.apply(open, GameCommand.CLOCKWISE)
        for (command in listOf(GameCommand.LEFT, GameCommand.RIGHT, GameCommand.SOFT_DROP, GameCommand.TICK))
            assertEquals(-1, engine.apply(qualified, command).lastRotationKick)
        assertEquals(Spin.NONE, engine.apply(qualified, GameCommand.HARD_DROP).placement!!.spin)
    }

    @Test fun miniZeroAndFifthKickPromotion() {
        val mini = engine.apply(engine.apply(spinStart(true), GameCommand.CLOCKWISE), GameCommand.HARD_DROP)
        assertEquals(Spin.MINI, mini.placement!!.spin)
        assertEquals(0, mini.placement.lines)
        val kick = state(Piece(Tetromino.T, x = 3, y = 14), listOf(
            Cell(4,16), Cell(3,14), Cell(3,13), Cell(2,16), Cell(2,18), Cell(3,19)))
        assertTrue(engine.fits(kick, kick.active))
        val rotated = engine.apply(kick, GameCommand.CLOCKWISE)
        assertEquals(Piece(Tetromino.T, x = 2, y = 16, rotation = 1), rotated.active)
        assertEquals(4, rotated.lastRotationKick)
        assertEquals(Spin.FULL, engine.apply(rotated, GameCommand.HARD_DROP).placement!!.spin)
    }

    @Test fun fullSingleDoubleAndTriple() {
        for (count in 1..2) {
            val cells = mutableListOf(Cell(3,17), Cell(5,17), Cell(3,19))
            cells += (0..9).filter { it !in 3..5 }.map { Cell(it,18) }
            if (count == 2) cells += (0..9).filter { it != 4 }.map { Cell(it,17) }
            val start = state(Piece(Tetromino.T, x = 3, y = 17, rotation = 3), cells)
            val result = engine.apply(engine.apply(start, GameCommand.CLOCKWISE), GameCommand.HARD_DROP)
            assertEquals(count, result.placement!!.lines)
            assertEquals(Spin.FULL, result.placement.spin)
        }
        val cells = (16..18).flatMap { y -> (0..9).filter { x -> x != 4 && !(y == 17 && x == 5) }.map { Cell(it,
            y) } } + Cell(4,19)
        val triple = state(Piece(Tetromino.T, x = 3, y = 16, rotation = 1), cells).copy(lastRotationKick = 0)
        val result = engine.apply(triple, GameCommand.HARD_DROP)
        assertEquals(3, result.placement!!.lines)
        assertEquals(Spin.FULL, result.placement.spin)
    }

    private fun tetris() = state(Piece(Tetromino.I, x = 2, y = 16, rotation = 1),
        (16..19).flatMap { y -> (0..9).filter { it != 4 }.map { Cell(it,y) } } + Cell(0,10))
    @Test fun chainsAreIndependentAndNonClearRetainsBackToBack() {
        val first = engine.finishClear(engine.apply(tetris(), GameCommand.HARD_DROP))
        assertTrue(first.backToBack)
        assertFalse(first.placement!!.backToBack)
        assertEquals(0, first.combo)
        val second = engine.finishClear(engine.apply(tetris().copy(backToBack = first.backToBack,
            combo = first.combo), GameCommand.HARD_DROP))
        assertTrue(second.placement!!.backToBack)
        assertEquals(1, second.combo)
        val noClear = engine.apply(spinStart().copy(backToBack = true, combo = 1), GameCommand.HARD_DROP)
        assertTrue(noClear.backToBack)
        assertEquals(-1, noClear.combo)
        val single = state(Piece(Tetromino.O,x=8,y=18), (0..7).map { Cell(it,19) })
        val broken = engine.apply(single.copy(backToBack = true, combo = 1), GameCommand.HARD_DROP)
        assertFalse(broken.backToBack)
        assertFalse(broken.placement!!.backToBack)
        assertEquals(2, broken.combo)
        val zeroSpin = engine.apply(engine.apply(spinStart().copy(backToBack = true), GameCommand.CLOCKWISE),
            GameCommand.HARD_DROP)
        assertTrue(zeroSpin.backToBack)
    }

    @Test fun perfectClearChecksHiddenRowsAfterRemovalOnly() {
        val single = state(Piece(Tetromino.I,x=3,y=18), (0..9).filter { it !in 3..6 }.map { Cell(it,19) })
        val pending = engine.apply(single, GameCommand.HARD_DROP)
        assertFalse(pending.placement!!.perfectClear)
        assertTrue(engine.finishClear(pending).placement!!.perfectClear)
        val board = single.board.map { it.toMutableList() }
        board[BoardGeometry.row(-1)][0] = Tetromino.J
        val hidden = engine.finishClear(engine.apply(single.copy(board = board), GameCommand.HARD_DROP))
        assertFalse(hidden.placement!!.perfectClear)
    }
}
