package ru.itoltec.swypetris

/** Presentation phases sampled from the canonical clear clock; no gameplay state lives here. */
object LineClearAnimation {
    const val STEP_MILLIS = 16L
    const val TOTAL_MILLIS = 600L
    const val BURST_MILLIS = 80L
    const val SETTLE_MILLIS = 360L

    fun isRemoved(elapsedMillis: Long): Boolean = elapsedMillis >= BURST_MILLIS

    fun highlight(elapsedMillis: Long): Float {
        val progress = (elapsedMillis.toFloat() / BURST_MILLIS).coerceIn(0f, 1f)
        return .18f * (1f - kotlin.math.abs(progress * 2f - 1f))
    }

    fun burstProgress(elapsedMillis: Long): Float =
        ((elapsedMillis - BURST_MILLIS).toFloat() / (SETTLE_MILLIS - BURST_MILLIS)).coerceIn(0f, 1f)

    fun shardAlpha(elapsedMillis: Long): Float {
        if (elapsedMillis < BURST_MILLIS || elapsedMillis >= SETTLE_MILLIS) return 0f
        val progress = burstProgress(elapsedMillis)
        return 1f - progress * progress
    }

    fun settleProgress(elapsedMillis: Long): Float {
        val progress = ((elapsedMillis - SETTLE_MILLIS).toFloat() /
            (TOTAL_MILLIS - SETTLE_MILLIS)).coerceIn(0f, 1f)
        return progress * progress * (3f - 2f * progress)
    }

    /** All rows above a removed row move down, including hidden rows entering the visible board. */
    fun rowShift(row: Int, clearingRows: List<Int>, elapsedMillis: Long, reducedMotion: Boolean): Float =
        if (reducedMotion) 0f else clearingRows.count { it > row } * settleProgress(elapsedMillis)

    /** Four fixed fragments per source cell, at most 160 for a legal four-line clear. */
    internal fun shards(state: GameState): List<ClearShard> = buildList {
        state.clearingRows.sorted().distinct().filter { it >= BoardGeometry.HIDDEN_ROWS }.take(4).forEach { row ->
            state.board[row].forEachIndexed { column, type ->
                if (type != null) repeat(4) { part ->
                    val seed = (row * 31 + column * 17 + part * 11 + state.completedClears * 7) and Int.MAX_VALUE
                    add(ClearShard(column, row, type, part,
                        (column - 4.5f) * .16f + (seed % 7 - 3) * .11f,
                        -1.8f - seed % 9 * .1f, (seed % 2 * 2 - 1) * (18f + seed % 19)))
                }
            }
        }
    }
}

internal data class ClearShard(val column: Int, val row: Int, val type: Tetromino, val part: Int,
    val velocityX: Float, val velocityY: Float, val rotation: Float)
