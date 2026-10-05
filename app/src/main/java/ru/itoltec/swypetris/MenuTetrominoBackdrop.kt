package ru.itoltec.swypetris

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/** Lightweight decorative tetromino layer used only while the main menu is in composition. */
@Composable
internal fun MenuTetrominoBackdrop(
    logoBounds: Rect,
    modifier: Modifier = Modifier
) {
    val palette = LocalGamePalette.current
    val animationsEnabled = remember {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    }
    if (!animationsEnabled) return

    val transition = rememberInfiniteTransition(label = "menuTetrominoes")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing)
        ),
        label = "menuTetrominoFall"
    )

    Canvas(modifier) {
        val cell = (size.minDimension * .035f).coerceIn(11.dp.toPx(), 22.dp.toPx())
        val protected = if (logoBounds == Rect.Zero) Rect.Zero else Rect(
            left = logoBounds.left - 18.dp.toPx(),
            top = logoBounds.top - 18.dp.toPx(),
            right = logoBounds.right + 18.dp.toPx(),
            bottom = logoBounds.bottom + 18.dp.toPx()
        )
        MenuPiece.entries.forEachIndexed { index, piece ->
            val phase = (progress * piece.speed + piece.phase) % 1f
            val top = -cell * 4f + phase * (size.height + cell * 8f)
            val left = piece.xFraction * (size.width - cell * 4f)
            val cells = piece.cells
            val minX = cells.minOf { it.first }
            val maxX = cells.maxOf { it.first }
            val minY = cells.minOf { it.second }
            val maxY = cells.maxOf { it.second }
            val bounds = Rect(
                left + minX * cell,
                top + minY * cell,
                left + (maxX + 1) * cell,
                top + (maxY + 1) * cell
            )
            if (protected != Rect.Zero && bounds.overlaps(protected)) return@forEachIndexed

            val color = palette.piece(Tetromino.entries[index % Tetromino.entries.size])
            cells.forEach { (column, row) ->
                bevelBlock(
                    at = Offset(left + column * cell, top + row * cell),
                    size = Size(cell * .92f, cell * .92f),
                    color = color,
                    finish = palette.finish,
                    texture = palette.texture,
                    alpha = if (palette.light) .16f else .20f
                )
            }
        }
    }
}

private enum class MenuPiece(
    val xFraction: Float,
    val phase: Float,
    val speed: Float,
    val cells: List<Pair<Int, Int>>
) {
    I(.04f, .02f, .72f, listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1)),
    O(.72f, .19f, .84f, listOf(1 to 0, 2 to 0, 1 to 1, 2 to 1)),
    T(.18f, .38f, .78f, listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1)),
    S(.83f, .55f, .91f, listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1)),
    Z(.34f, .70f, .76f, listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1)),
    J(.61f, .84f, .88f, listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1)),
    L(.92f, .31f, .69f, listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1))
}
