package ru.itoltec.swypetris

import androidx.compose.ui.geometry.Rect

internal const val FRUIT_SIZE = 20f
internal const val FRUIT_GAP = 4f
internal const val HUD_HORIZONTAL_MARGIN = 4f
private const val PREVIEW_GAP = 8f

/** Shared preview width for rendering and fruit collision avoidance, in dp. */
internal fun nextPreviewWidth(width: Float): Float = (width * .18f).coerceIn(40f, 72f)

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
 * @param scoreWidth Width reserved by the score indicator in board-local pixels.
 * @param scoreHeight Height reserved by the score indicator in board-local pixels.
 */
internal fun fruitPlacement(width: Float, height: Float, count: Int,
    scoreWidth: Float, scoreHeight: Float, headerHeight: Float = 48f): FruitPlacement {
    val left = (width - FRUIT_SIZE - HUD_HORIZONTAL_MARGIN).coerceAtLeast(0f)
    val right = left + FRUIT_SIZE
    val score = Rect(HUD_HORIZONTAL_MARGIN, 3f, scoreWidth + HUD_HORIZONTAL_MARGIN, scoreHeight + 3f)
    val preview = Rect((width - nextPreviewWidth(width) - HUD_HORIZONTAL_MARGIN).coerceAtLeast(0f),
        0f, width, headerHeight)
    val obstacles = Tetromino.entries.map { pieceBounds(Piece(it), width, height) } + preview + score
    val obstacleBottom = obstacles.filter { left < it.right && right > it.left }
        .maxOfOrNull { it.bottom } ?: 0f
    val columnHeight = count * FRUIT_SIZE + (count - 1).coerceAtLeast(0) * FRUIT_GAP
    val top = (if (obstacleBottom > 0f) obstacleBottom + PREVIEW_GAP else FRUIT_GAP)
        .coerceAtMost((height - columnHeight).coerceAtLeast(FRUIT_GAP))
    return FruitPlacement(left, top)
}
