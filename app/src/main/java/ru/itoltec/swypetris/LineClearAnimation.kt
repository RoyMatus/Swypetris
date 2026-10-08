package ru.itoltec.swypetris

private const val HIGHLIGHT_PEAK_ALPHA = .18f
private const val SMOOTHSTEP_CUBIC_TERM = 3f
private const val SHARDS_PER_CELL = 4
private const val ROW_SEED_MULTIPLIER = 31
private const val COLUMN_SEED_MULTIPLIER = 17
private const val PART_SEED_MULTIPLIER = 11
private const val CLEAR_SEED_MULTIPLIER = 7
private const val HORIZONTAL_SPLAY = .16f
private const val HORIZONTAL_VARIATIONS = 7
private const val HORIZONTAL_VARIATION_CENTER = 3
private const val HORIZONTAL_VARIATION_STEP = .11f
private const val BASE_VERTICAL_VELOCITY = -1.8f
private const val VERTICAL_VARIATIONS = 9
private const val VERTICAL_VARIATION_STEP = .1f
private const val BASE_ROTATION_DEGREES = 18f
private const val ROTATION_VARIATIONS = 19


/** Presentation phases sampled from the canonical clear clock; no gameplay state lives here. */
object LineClearAnimation {
    const val STEP_MILLIS = 16L
    const val TOTAL_MILLIS = 600L
    const val BURST_MILLIS = 80L
    const val SETTLE_MILLIS = 360L

    fun isRemoved(elapsedMillis: Long): Boolean = elapsedMillis >= BURST_MILLIS

    fun highlight(elapsedMillis: Long): Float {
        val progress = (elapsedMillis.toFloat() / BURST_MILLIS).coerceIn(0f, 1f)
        return HIGHLIGHT_PEAK_ALPHA * (1f - kotlin.math.abs(progress * 2f - 1f))
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
        return progress * progress * (SMOOTHSTEP_CUBIC_TERM - 2f * progress)
    }

    /** All rows above a removed row move down, including hidden rows entering the visible board. */
    fun rowShift(row: Int, clearingRows: List<Int>, elapsedMillis: Long, reducedMotion: Boolean): Float =
        if (reducedMotion) 0f else clearingRows.count { it > row } * settleProgress(elapsedMillis)

    /** Four fixed fragments per source cell, at most 160 for a legal four-line clear. */
    internal fun shards(state: GameState): List<ClearShard> = buildList {
        state.clearingRows.sorted().distinct().filter { it >= BoardGeometry.HIDDEN_ROWS }
            .take(GameRules.MAX_CLEAR_LINES).forEach { row ->
            state.board[row].forEachIndexed { column, type ->
                if (type != null) repeat(SHARDS_PER_CELL) { part ->
                    val seed =
                        (row * ROW_SEED_MULTIPLIER + column * COLUMN_SEED_MULTIPLIER +
                            part * PART_SEED_MULTIPLIER +
                            state.completedClears * CLEAR_SEED_MULTIPLIER) and Int.MAX_VALUE
                    add(ClearShard(column, row, type, part,
                        (column - (BoardGeometry.WIDTH - 1) / 2f) * HORIZONTAL_SPLAY + (
                            seed % HORIZONTAL_VARIATIONS - HORIZONTAL_VARIATION_CENTER) * HORIZONTAL_VARIATION_STEP,
                        BASE_VERTICAL_VELOCITY - seed % VERTICAL_VARIATIONS * VERTICAL_VARIATION_STEP,
                            (seed % 2 * 2 - 1) * (BASE_ROTATION_DEGREES + seed % ROTATION_VARIATIONS)))
                }
            }
        }
    }
}

internal data class ClearShard(val column: Int, val row: Int, val type: Tetromino, val part: Int,
    val velocityX: Float, val velocityY: Float, val rotation: Float)
