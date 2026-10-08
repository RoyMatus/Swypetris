package ru.itoltec.swypetris

private const val MIN_SPIN_CORNERS = 3


enum class Spin { NONE, MINI, FULL }

/** Immutable event retained through the clear animation and next-piece spawn. */
data class PlacementResult(
    val lines: Int,
    val spin: Spin,
    val backToBack: Boolean,
    val combo: Int,
    val perfectClear: Boolean = false,
    val softDropCells: Int = 0,
    val hardDropCells: Int = 0,
    val level: Int = 1
) {
    val difficult: Boolean get() = lines > 0 && (lines == GameRules.MAX_CLEAR_LINES || spin != Spin.NONE)
}

internal object SpinRecognition {
    fun classify(state: GameState): Spin {
        val piece = state.active
        if (piece.type != Tetromino.T || state.lastRotationKick < 0) return Spin.NONE
        fun occupied(x: Int, y: Int): Boolean = x !in 0 until BoardGeometry.WIDTH ||
            BoardGeometry.row(y) !in state.board.indices || state.board[BoardGeometry.row(y)][x] != null
        // Clockwise order: upper-left, upper-right, lower-right, lower-left.
        val corners = listOf(occupied(piece.x, piece.y), occupied(piece.x + 2, piece.y),
            occupied(piece.x + 2, piece.y + 2), occupied(piece.x, piece.y + 2))
        return if (corners.count { it } < MIN_SPIN_CORNERS) Spin.NONE else {
            val front = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0)[piece.rotation]
            if ((corners[front.first] && corners[front.second]) || state.lastRotationKick == LAST_ROTATION_KICK)
                Spin.FULL else Spin.MINI
        }
    }
}
