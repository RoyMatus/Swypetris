package ru.itoltec.swypetris

import java.lang.management.ManagementFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollisionCheckTest {
    private val engine = GameEngine()

    @Test fun collisionChecksMatchVisibleCellsAcrossRotationsAndBoardEdges() {
        val empty = BoardGeometry.empty()
        val occupied = empty.mapIndexed { y, row -> row.mapIndexed { x, _ ->
            if ((x + y * 3) % 7 == 0) Tetromino.J else null
        } }
        for (board in listOf(empty, occupied)) {
            val state = GameState(board = board, active = Piece(Tetromino.T), next = Tetromino.O)
            for (type in Tetromino.entries) for (rotation in 0..3) {
                assertPositions(state, type, rotation)
            }
        }
    }

    private fun assertPositions(state: GameState, type: Tetromino, rotation: Int) {
        for (x in -4..BoardGeometry.WIDTH) for (y in -24..BoardGeometry.VISIBLE_ROWS) {
            val piece = Piece(type, x, y, rotation)
            val cells = referenceCells(piece)
            assertEquals(cells, piece.cells())
            val expected = cells.all { cell ->
                cell.x in 0 until BoardGeometry.WIDTH && BoardGeometry.row(cell.y) in state.board.indices &&
                    state.board[BoardGeometry.row(cell.y)][cell.x] == null
            }
            assertEquals("$piece", expected, engine.fits(state, piece))
        }
    }

    /** Original object-based projection is an independent oracle for the primitive path. */
    private fun referenceCells(piece: Piece): List<Cell> = piece.type.shape.map { original ->
        var cell = original
        if (piece.type != Tetromino.O) repeat(piece.rotation) {
            cell = Cell(piece.type.box - 1 - cell.y, cell.x)
        }
        Cell(cell.x + piece.x, cell.y + piece.y)
    }

    @Test fun repeatedCollisionChecksAvoidPerCellAllocation() {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        assertTrue("The local JDK must expose allocation counters", bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        val state = GameState(active = Piece(Tetromino.T), next = Tetromino.O)
        val pieces = Tetromino.entries.flatMap { type -> (0..3).map { Piece(type, rotation = it) } }
        val checks = 100_000
        var accepted = 0
        repeat(checks) { if (engine.fits(state, pieces[it % pieces.size])) accepted++ }
        val thread = Thread.currentThread().threadId()
        val before = bean.getThreadAllocatedBytes(thread)
        repeat(checks) { if (engine.fits(state, pieces[it % pieces.size])) accepted++ }
        val after = bean.getThreadAllocatedBytes(thread)
        assertTrue("Allocation counters must be available for this platform thread", before >= 0 && after >= before)
        val bytes = after - before
        assertEquals(checks * 2, accepted)
        val bytesPerCheck = bytes.toDouble() / checks
        println("Collision checks: $bytes bytes / $checks calls = $bytesPerCheck bytes/call")
        // Allows a small iterator; rejects allocating a list and four transformed cells per check.
        assertTrue("Collision checks allocate $bytesPerCheck bytes/call", bytesPerCheck <= 64.0)
    }
}
