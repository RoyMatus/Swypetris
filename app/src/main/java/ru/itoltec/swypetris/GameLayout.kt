package ru.itoltec.swypetris

import androidx.compose.ui.geometry.Rect

internal const val FRUIT_SIZE = 20f
internal const val FRUIT_GAP = 4f
private const val PREVIEW_GAP = 8f

/**
 * Returns the occupied rectangle of [piece] in board-local pixels.
 * The board has ten columns and twenty rows, so the result uses the same cell geometry as
 * `Board` and remains independent of any device-specific screen coordinates.
 *
 * @param width Width of the board drawing area in pixels.
 * @param height Height of the board drawing area in pixels.
 */
internal fun pieceBounds(piece: Piece, width: Float, height: Float): Rect {
    val cells = piece.cells()
    return Rect(cells.minOf { it.x } * width / 10, cells.minOf { it.y } * height / 20,
        (cells.maxOf { it.x } + 1) * width / 10, (cells.maxOf { it.y } + 1) * height / 20)
}

/**
 * Layout of the fruit collection overlay in board-local pixels.
 * [left] and [top] locate its first icon; [columns] controls wrapping into further rows.
 */
internal data class FruitPlacement(val left: Float, val top: Float, val columns: Int)

/**
 * Places collected fruits without covering the score, any possible spawn shape, or the next-piece
 * preview. Fits as many columns as the free space permits; extremely narrow boards place a single
 * column below those obstacles. All coordinates use the board's own pixel dimensions.
 *
 * @param width Width of the board drawing area in pixels.
 * @param height Height of the board drawing area in pixels.
 * @param count Number of fruit icons that need space.
 * @param next Tetromino shown in the preview when [showNext] is true.
 * @param scoreWidth Width reserved by the score indicator in board-local pixels.
 * @param scoreHeight Height reserved by the score indicator in board-local pixels.
 */
internal fun fruitPlacement(width: Float, height: Float, count: Int, next: Tetromino,
    showNext: Boolean, scoreWidth: Float, scoreHeight: Float): FruitPlacement {
    val spawn = Tetromino.entries.map { pieceBounds(Piece(it), width, height) }
    val nextBounds = if (showNext) pieceBounds(Piece(next), width, height) else Rect.Zero
    val protectedRight = maxOf(spawn.maxOf { it.right }, nextBounds.right)
    val minimumLeft = protectedRight + PREVIEW_GAP
    val available = width - minimumLeft - FRUIT_GAP
    val columns = ((available + FRUIT_GAP) / (FRUIT_SIZE + FRUIT_GAP)).toInt().coerceIn(1, count.coerceAtLeast(1))
    val rowWidth = columns * (FRUIT_SIZE + FRUIT_GAP) - FRUIT_GAP
    val left = maxOf((width - rowWidth) / 2, minimumLeft)
    val scoreBottom = if (left < scoreWidth + 4 + PREVIEW_GAP) scoreHeight + 3 + PREVIEW_GAP else FRUIT_GAP
    if (available >= FRUIT_SIZE) return FruitPlacement(left, scoreBottom, columns)
    // Extremely narrow windows use the space below both obstacles.
    return FruitPlacement((width - FRUIT_SIZE).coerceAtLeast(0f) / 2,
        maxOf(scoreHeight + 3, spawn.maxOf { it.bottom }, nextBounds.bottom) + PREVIEW_GAP, 1)
}
