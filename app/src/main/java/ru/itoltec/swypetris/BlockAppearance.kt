package ru.itoltec.swypetris

private const val CELLS_PER_FIGURE = 4

/** Identity within a connected figure atlas; independent of the currently selected theme. */
data class BlockAppearance(val cellIndex: Int = 0, val quarterTurns: Int = 0) {
    init { require(cellIndex in 0 until CELLS_PER_FIGURE && quarterTurns in RotationState.entries.indices) }

    companion object {
        /** Type-only legacy boards cannot recover the original figure cell or orientation. */
        val legacy = BlockAppearance()
    }
}

internal fun GameState.blockAppearance(row: Int, column: Int): BlockAppearance =
    blockAppearances.getOrNull(row)?.getOrNull(column) ?: BlockAppearance.legacy

internal fun GameState.lockedAppearances(): List<List<BlockAppearance?>> {
    val result = board.mapIndexed { row, cells ->
        cells.mapIndexed { column, type -> if (type == null) null else blockAppearance(row, column) }.toMutableList()
    }
    active.cells().forEachIndexed { index, cell ->
        result[BoardGeometry.row(cell.y)][cell.x] =
            BlockAppearance(index, if (active.type == Tetromino.O) 0 else active.rotation)
    }
    return result
}

internal fun GameState.clearedAppearances(): List<List<BlockAppearance?>> =
    if (blockAppearances.isEmpty()) emptyList() else
        List(clearingRows.size) { List<BlockAppearance?>(BoardGeometry.WIDTH) { null } } +
            blockAppearances.filterIndexed { index, _ -> index !in clearingRows }
