package ru.itoltec.swypetris

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

private const val QUADRANTS_PER_CELL = 4
private const val QUARTER_TURN_DEGREES = 90f

/** The pinned atlas layout: seven shape columns, four canonical cells, two-pixel extruded gutters. */
internal object ConnectedMaterialLayout {
    const val CELL = 128
    const val PITCH = 132
    const val GUTTER = 2

    fun turns(type: Tetromino, appearance: BlockAppearance): Int =
        if (type == Tetromino.O) 0 else appearance.quarterTurns

    /** A quadrant is numbered in displayed coordinates; invert the cell rotation before cropping. */
    fun source(type: Tetromino, appearance: BlockAppearance, quadrant: Int = -1): IntOffset {
        require(quadrant in -1 until QUADRANTS_PER_CELL)
        var x = 0
        var y = 0
        if (quadrant >= 0) {
            x = quadrant % 2
            y = quadrant / 2
            repeat(turns(type, appearance)) {
                val previousX = x
                x = y
                y = 1 - previousX
            }
        }
        return IntOffset(type.ordinal * PITCH + GUTTER + x * (CELL / 2),
            appearance.cellIndex * PITCH + GUTTER + y * (CELL / 2))
    }
}

/** One decoded connected atlas per composed style, shared by cells and quadrant fragments. */
internal class BlockMaterial(val texture: BlockTexture, val image: ImageBitmap) {
    fun draw(scope: DrawScope, at: Offset, size: Size, type: Tetromino, appearance: BlockAppearance,
        alpha: Float, quadrant: Int = -1) {
        val source = ConnectedMaterialLayout.source(type, appearance, quadrant)
        val side = if (quadrant < 0) ConnectedMaterialLayout.CELL else ConnectedMaterialLayout.CELL / 2
        val turns = ConnectedMaterialLayout.turns(type, appearance)
        with(scope) {
            withTransform({
                translate(at.x.roundToInt().toFloat(), at.y.roundToInt().toFloat())
                // Canvas composes these transforms so square artwork rotates before destination stretching.
                scale(size.width.roundToInt().coerceAtLeast(1) / side.toFloat(),
                    size.height.roundToInt().coerceAtLeast(1) / side.toFloat(), pivot = Offset.Zero)
                rotate(turns * QUARTER_TURN_DEGREES, pivot = Offset(side / 2f, side / 2f))
            }) {
                drawImage(image, srcOffset = source, srcSize = IntSize(side, side), dstOffset = IntOffset.Zero,
                    dstSize = IntSize(side, side), alpha = alpha, filterQuality = FilterQuality.Medium)
            }
        }
    }
}

internal val LocalBlockMaterial = staticCompositionLocalOf<BlockMaterial?> { null }

@Composable
internal fun blockMaterial(palette: GamePalette): BlockMaterial {
    LocalBlockMaterial.current?.takeIf { it.texture == palette.texture }?.let { return it }
    val image = ImageBitmap.imageResource(materialResource(palette.texture))
    return remember(image, palette.texture) { BlockMaterial(palette.texture, image) }
}

/** Crop uniformly into the existing portrait field; the user's photo retains precedence. */
@Composable
internal fun ThemedFieldBackground(palette: GamePalette) {
    Image(painterResource(fieldResource(palette.texture)), contentDescription = null,
        modifier = Modifier.fillMaxSize().testTag("themedBackground"), contentScale = ContentScale.Crop)
}

private fun materialResource(texture: BlockTexture): Int = when (texture) {
    BlockTexture.CLASSIC -> R.drawable.material_connected_classic
    BlockTexture.MONOKAI -> R.drawable.material_connected_monokai
    BlockTexture.GRUVBOX -> R.drawable.material_connected_gruvbox
    BlockTexture.VSCODE -> R.drawable.material_connected_vscode
    BlockTexture.DRACULA -> R.drawable.material_connected_dracula
    BlockTexture.NORD -> R.drawable.material_connected_nord
    BlockTexture.SOLARIZED_LIGHT -> R.drawable.material_connected_solarized_light
    BlockTexture.SOLARIZED_DARK -> R.drawable.material_connected_solarized_dark
    BlockTexture.GITHUB_LIGHT -> R.drawable.material_connected_github_light
    BlockTexture.TOKYO_NIGHT -> R.drawable.material_connected_tokyo_night
    BlockTexture.CATPPUCCIN -> R.drawable.material_connected_catppuccin
    BlockTexture.SYNTHWAVE -> R.drawable.material_connected_synthwave_84
}

private fun fieldResource(texture: BlockTexture): Int = when (texture) {
    BlockTexture.CLASSIC -> R.drawable.field_classic
    BlockTexture.MONOKAI -> R.drawable.field_monokai
    BlockTexture.GRUVBOX -> R.drawable.field_gruvbox
    BlockTexture.VSCODE -> R.drawable.field_vscode
    BlockTexture.DRACULA -> R.drawable.field_dracula
    BlockTexture.NORD -> R.drawable.field_nord
    BlockTexture.SOLARIZED_LIGHT -> R.drawable.field_solarized_light
    BlockTexture.SOLARIZED_DARK -> R.drawable.field_solarized_dark
    BlockTexture.GITHUB_LIGHT -> R.drawable.field_github_light
    BlockTexture.TOKYO_NIGHT -> R.drawable.field_tokyo_night
    BlockTexture.CATPPUCCIN -> R.drawable.field_catppuccin
    BlockTexture.SYNTHWAVE -> R.drawable.field_synthwave_84
}
