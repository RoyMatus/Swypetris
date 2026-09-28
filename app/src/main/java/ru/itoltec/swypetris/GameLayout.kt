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

/** Top-right position of the vertical fruit collection in board-local coordinates. */
internal data class FruitPlacement(val left: Float, val top: Float)

/**
 * Anchors collected fruits to the top-right. On narrow boards, moves the whole column below
 * any score, spawn shape, or next-piece preview that occupies the same horizontal space.
 *
 * @param width Width of the board drawing area in pixels.
 * @param height Height of the board drawing area in pixels.
 * @param count Number of fruit icons that need space.
 * @param next Tetromino shown in the preview when [showNext] is true.
 * @param scoreWidth Width reserved by the score indicator in board-local pixels.
 * @param scoreHeight Height reserved by the score indicator in board-local pixels.
 * @param rightInset Space reserved for Android's side gesture at the right edge.
 */
internal fun fruitPlacement(width: Float, height: Float, count: Int, next: Tetromino,
    showNext: Boolean, scoreWidth: Float, scoreHeight: Float, rightInset: Float = 0f): FruitPlacement {
    val left = (width - rightInset - FRUIT_SIZE - FRUIT_GAP).coerceAtLeast(0f)
    val right = left + FRUIT_SIZE
    val score = Rect(4f, 3f, scoreWidth + 4f, scoreHeight + 3f)
    val obstacles = Tetromino.entries.map { pieceBounds(Piece(it), width, height) } +
        (if (showNext) listOf(pieceBounds(Piece(next), width, height)) else emptyList()) + score
    val obstacleBottom = obstacles.filter { left < it.right && right > it.left }
        .maxOfOrNull { it.bottom } ?: 0f
    val columnHeight = count * FRUIT_SIZE + (count - 1).coerceAtLeast(0) * FRUIT_GAP
    val top = (if (obstacleBottom > 0f) obstacleBottom + PREVIEW_GAP else FRUIT_GAP)
        .coerceAtMost((height - columnHeight).coerceAtLeast(FRUIT_GAP))
    return FruitPlacement(left, top)
}
