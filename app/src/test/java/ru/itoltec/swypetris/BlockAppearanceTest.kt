package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockAppearanceTest {
    @Test fun everyRotatedFigureRetainsCanonicalIdentityAfterLock() {
        val engine = GameEngine()
        for (type in Tetromino.entries) for (rotation in 0..3) {
            val state = GameState(active = Piece(type, rotation = rotation), next = Tetromino.O)
            val landed = engine.ghost(state)
            val locked = engine.apply(state, GameCommand.HARD_DROP)
            landed.cells().forEachIndexed { index, cell ->
                assertEquals(type, locked.board[BoardGeometry.row(cell.y)][cell.x])
                assertEquals(BlockAppearance(index, if (type == Tetromino.O) 0 else rotation),
                    locked.blockAppearance(BoardGeometry.row(cell.y), cell.x))
            }
            assertEquals(4, locked.blockAppearances.sumOf { row -> row.count { it != null } })
        }
    }

    @Test fun removalMovesSurvivingCellsAndShardsKeepTheirOriginalIdentity() {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        val appearances = List(BoardGeometry.TOTAL_ROWS) { MutableList<BlockAppearance?>(10) { null } }
        val removed = listOf(17, 19).map(BoardGeometry::row)
        removed.forEach { row -> repeat(10) { column ->
            board[row][column] = Tetromino.T
            appearances[row][column] = BlockAppearance(column % 4, column % 4)
        } }
        val survivor = BoardGeometry.row(16)
        board[survivor][2] = Tetromino.J
        appearances[survivor][2] = BlockAppearance(3, 1)
        val state = GameState(board = board, active = Piece(Tetromino.O), next = Tetromino.I,
            clearingRows = removed, blockAppearances = appearances)
        val shards = LineClearAnimation.shards(state)
        assertEquals(80, shards.size)
        shards.forEach { assertEquals(appearances[it.row][it.column], it.appearance) }
        assertEquals(LineClearAnimation.shards(state.copy(blockAppearances = emptyList())),
            shards.map { it.copy(appearance = BlockAppearance.legacy) })
        val cleared = GameEngine().finishClear(state)
        assertEquals(Tetromino.J, cleared.board[survivor + 2][2])
        assertEquals(BlockAppearance(3, 1), cleared.blockAppearance(survivor + 2, 2))
        assertNull(cleared.blockAppearances[survivor][2])
        assertEquals(1, cleared.blockAppearances.sumOf { row -> row.count { it != null } })
    }

    @Test fun legacyCellsKeepFallbackWhileNewPlacementsAcquireIdentity() {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        board[BoardGeometry.row(19)][0] = Tetromino.L
        val state = GameState(board = board, active = Piece(Tetromino.T, rotation = 3), next = Tetromino.I)
        val result = GameEngine().apply(state, GameCommand.HARD_DROP)
        assertEquals(BlockAppearance.legacy, result.blockAppearance(BoardGeometry.row(19), 0))
        assertEquals(5, result.blockAppearances.sumOf { row -> row.count { it != null } })
    }

    @Test fun hiddenLockOutKeepsAllFourCellIdentities() {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        repeat(10) { board[BoardGeometry.row(0)][it] = Tetromino.J }
        val state = GameState(board = board, active = Piece(Tetromino.T, y = -3), next = Tetromino.I)
        val engine = GameEngine()
        val landed = engine.ghost(state)
        val result = engine.apply(state, GameCommand.HARD_DROP)
        assertEquals(TopOut.LOCK_OUT, result.topOut)
        landed.cells().forEachIndexed { index, cell ->
            assertEquals(BlockAppearance(index, 0), result.blockAppearance(BoardGeometry.row(cell.y), cell.x))
        }
    }
}
