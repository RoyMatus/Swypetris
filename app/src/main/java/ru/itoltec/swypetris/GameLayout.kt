package ru.itoltec.swypetris

import androidx.compose.ui.geometry.Rect

internal const val FRUIT_SIZE = 20f
internal const val FRUIT_GAP = 4f
private const val PREVIEW_GAP = 8f

/** The same cell coordinates as Board; no device-specific pixel positions. */
internal fun pieceBounds(piece: Piece, width: Float, height: Float): Rect {
    val cells = piece.cells()
    return Rect(cells.minOf { it.x } * width / 10, cells.minOf { it.y } * height / 20,
        (cells.maxOf { it.x } + 1) * width / 10, (cells.maxOf { it.y } + 1) * height / 20)
}

internal data class FruitPlacement(val left: Float, val top: Float, val columns: Int)

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
    // Extremely narrow windows have no room to the right: use the space below both obstacles.
    return FruitPlacement((width - FRUIT_SIZE).coerceAtLeast(0f) / 2,
        maxOf(scoreHeight + 3, spawn.maxOf { it.bottom }, nextBounds.bottom) + PREVIEW_GAP, 1)
}
