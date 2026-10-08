package ru.itoltec.swypetris

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

private const val CLEAR_ROW_CENTER = .5f


/** Drawn inside the board's clip; trajectories are cached once and sampled without randomization. */
internal fun DrawScope.lineClearEffects(shards: List<ClearShard>, rows: List<Int>, palette: GamePalette,
    origin: Offset, cell: Size, elapsed: Long, reducedMotion: Boolean) {
    val highlight = LineClearAnimation.highlight(elapsed)
    if (highlight > 0f) {
        val gap = minOf(cell.width, cell.height) * .07f
        rows.forEach { row ->
            repeat(BoardGeometry.WIDTH) { column ->
                drawRect(Color.White.copy(alpha = highlight),
                    origin + Offset(column * cell.width + gap, (row - BoardGeometry.HIDDEN_ROWS) * cell.height + gap),
                    Size(cell.width - gap * 2, cell.height - gap * 2))
            }
        }
    }
    val alpha = LineClearAnimation.shardAlpha(elapsed)
    if (reducedMotion || alpha == 0f) return
    val progress = LineClearAnimation.burstProgress(elapsed)
    val wave = (progress * 2f).coerceAtMost(1f)
    if (wave < 1f) rows.forEach { row ->
        val width = size.width * (.7f + .28f * wave)
        val height = cell.height * (1f + wave)
        drawOval(palette.text.copy(alpha = .10f * (1f - wave)),
            Offset((size.width - width) / 2,
                origin.y + (row - BoardGeometry.HIDDEN_ROWS + CLEAR_ROW_CENTER) * cell.height - height / 2),
            Size(width, height), style = Stroke(1.dp.toPx()))
    }
    val side = minOf(cell.width, cell.height) * .38f
    shards.forEach { shard ->
        val center = origin + Offset(
            (shard.column + .25f + shard.part % 2 * .5f + shard.velocityX * progress) * cell.width,
            (shard.row - BoardGeometry.HIDDEN_ROWS + .25f + shard.part / 2 * .5f +
                shard.velocityY * progress + 3.2f * progress * progress) * cell.height)
        rotate(shard.rotation * progress, center) {
            bevelBlock(center - Offset(side / 2, side / 2), Size(side, side),
                palette.piece(shard.type), palette.finish, palette.texture, alpha)
        }
    }
}
