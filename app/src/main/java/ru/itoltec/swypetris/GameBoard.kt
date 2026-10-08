package ru.itoltec.swypetris

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Draws the logical board, spawn preview, active piece, and optional landing ghost. */
@Composable
internal fun Board(state: GameState, clearElapsedMillis: Long = 0L, landingHint: Piece? = null,
    clearTime: () -> Long = { clearElapsedMillis }, geometry: GameplayGeometry? = null, drawActive: Boolean = true,
    reducedMotion: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !android.animation.ValueAnimator.areAnimatorsEnabled()) {
    val palette = LocalGamePalette.current
    val shards = remember(state.board, state.clearingRows, state.completedClears) { LineClearAnimation.shards(state) }
    Canvas(Modifier.fillMaxSize()
        .semantics { contentDescription = "Игровое поле, очки ${state.score}, линии ${state.lines}." +
        " Следующая фигура ${state.next.name}" +
        " Запас: ${state.held?.name ?: "пусто"}, ${if (state.holdUsed) "обмен недоступен" else "обмен доступен"}." }
            .testTag("board")) {
        val elapsed = if (state.clearingRows.isNotEmpty()) clearTime() else 0L
        val cell = Size(size.width / BoardGeometry.WIDTH, geometry?.cellHeight ?: (size.height / GAME_GRID_ROWS))
        val origin = Offset(0f, (geometry?.gridTop ?: 0f) + SPAWN_DISPLAY_ROWS * cell.height)
        clipRect(top = geometry?.safeTop ?: 0f) {

            drawSpawnPreview(state, palette, origin, cell)

            drawSettledCells(state, palette, origin, cell, elapsed, reducedMotion)
            if (state.clearingRows.isNotEmpty()) clipRect(top = origin.y) {
                lineClearEffects(shards, state.clearingRows, palette, origin, cell, elapsed, reducedMotion)
            }

            if (!state.gameOver && state.clearingRows.isEmpty()) {
                landingHint?.let { hint ->
                    hint.cells().filter { it.y in 0 until BoardGeometry.VISIBLE_ROWS }.forEach {
                        block(it, palette.piece(hint.type), palette.finish, palette.texture, origin, cell,
                            alpha = palette.ghostAlpha, outline = true)
                    }
                }
                if (drawActive) fallingPiece(state, palette, origin, cell)
            }
        }
    }
}

/** Dedicated falling-piece layer between fruits and the other HUD indicators. */
@Composable
internal fun ActivePiece(state: GameState, geometry: GameplayGeometry) {
    val palette = LocalGamePalette.current
    Canvas(Modifier.fillMaxSize().testTag("activePiece")) {
        val cell = Size(size.width / BoardGeometry.WIDTH, geometry.cellHeight)
        val origin = Offset(0f, geometry.gridTop + SPAWN_DISPLAY_ROWS * cell.height)
        clipRect(top = geometry.safeTop) { fallingPiece(state, palette, origin, cell) }
    }
}

private fun DrawScope.fallingPiece(state: GameState, palette: GamePalette, origin: Offset, cell: Size) {
    if (!state.gameOver && state.clearingRows.isEmpty()) {
        state.active.cells().filter { it.y in -SPAWN_DISPLAY_ROWS until BoardGeometry.VISIBLE_ROWS }.forEach {
            block(it, palette.piece(state.active.type), palette.finish, palette.texture, origin, cell)
        }
    }
}

/** Draws a colored cell with spacing using independent width and height. */
internal fun DrawScope.block(cell: Cell, color: Color, finish: BlockFinish, texture: BlockTexture, origin: Offset,
    step: Size,
    alpha: Float = 1f, outline: Boolean = false) {
    val gap = minOf(step.width, step.height) * 0.07f
    val topLeft = origin + Offset(cell.x * step.width + gap, cell.y * step.height + gap)
    val blockSize = Size(step.width - gap * 2, step.height - gap * 2)
    bevelBlock(topLeft, blockSize, color, finish, texture, alpha, outline)
}

private fun DrawScope.drawSpawnPreview(state: GameState, palette: GamePalette, origin: Offset, cell: Size) {
    val firstVisibleSpawnRow = -SPAWN_DISPLAY_ROWS
    // The outline uses engine spawn cells; its faint flat fill cannot look like an active block.
    spawnPiece(state.next).cells()
        .filter { it.y in firstVisibleSpawnRow until BoardGeometry.VISIBLE_ROWS }
        .forEach {
            val gap = minOf(cell.width, cell.height) * .07f
            val at = origin + Offset(it.x * cell.width + gap, it.y * cell.height + gap)
            val bounds = Size(cell.width - gap * 2, cell.height - gap * 2)
            val previewColor = lerp(palette.piece(state.next), palette.text, if (palette.light) .35f else .1f)
            drawRect(previewColor.copy(alpha = .0275f), at, bounds)
            drawRect(previewColor.copy(alpha = if (palette.light) .35f else .30f), at, bounds,
                style = Stroke(1.dp.toPx()))
        }
}

private fun DrawScope.drawSettledCells(state: GameState, palette: GamePalette, origin: Offset, cell: Size,
    elapsed: Long, reducedMotion: Boolean) {
    val firstVisibleSpawnRow = -SPAWN_DISPLAY_ROWS
    state.board.forEachIndexed { rowIndex, row ->
        val logicalY = rowIndex - BoardGeometry.HIDDEN_ROWS
        val shift = LineClearAnimation.rowShift(rowIndex, state.clearingRows, elapsed, reducedMotion)
        if (logicalY + shift + 1 > firstVisibleSpawnRow &&
            !(rowIndex in state.clearingRows && LineClearAnimation.isRemoved(elapsed))) {
            row.forEachIndexed { x, type ->
                if (type != null) block(Cell(x, logicalY), palette.piece(type), palette.finish,
                    palette.texture, origin + Offset(0f, shift * cell.height), cell)
            }
        }
    }
}
