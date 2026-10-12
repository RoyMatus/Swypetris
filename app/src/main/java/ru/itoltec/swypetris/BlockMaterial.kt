package ru.itoltec.swypetris

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** One decoded seven-pigment atlas per composed style, shared by every filled cell. */
internal class BlockMaterial(val texture: BlockTexture, val image: ImageBitmap, private val colors: List<Color>) {
    fun draw(scope: DrawScope, at: Offset, size: Size, color: Color, alpha: Float) {
        val piece = colors.indexOf(color)
        require(piece >= 0) { "Material pigment must belong to its palette" }
        val side = image.height
        with(scope) {
            drawImage(image, srcOffset = IntOffset(piece * side, 0), srcSize = IntSize(side, side),
                dstOffset = IntOffset(at.x.roundToInt(), at.y.roundToInt()),
                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                alpha = alpha, filterQuality = FilterQuality.Medium)
        }
    }
}

internal val LocalBlockMaterial = staticCompositionLocalOf<BlockMaterial?> { null }

@Composable
internal fun blockMaterial(palette: GamePalette): BlockMaterial {
    LocalBlockMaterial.current?.takeIf { it.texture == palette.texture }?.let { return it }
    val image = ImageBitmap.imageResource(materialResource(palette.texture))
    return remember(image, palette) { BlockMaterial(palette.texture, image, palette.pieces) }
}

/** Crop uniformly into the existing portrait field; the user's photo retains precedence. */
@Composable
internal fun ThemedFieldBackground(palette: GamePalette) {
    Image(painterResource(fieldResource(palette.texture)), contentDescription = null,
        modifier = Modifier.fillMaxSize().testTag("themedBackground"), contentScale = ContentScale.Crop)
}

private fun materialResource(texture: BlockTexture): Int = when (texture) {
    BlockTexture.CLASSIC -> R.drawable.material_classic
    BlockTexture.MONOKAI -> R.drawable.material_monokai
    BlockTexture.GRUVBOX -> R.drawable.material_gruvbox
    BlockTexture.VSCODE -> R.drawable.material_vscode
    BlockTexture.DRACULA -> R.drawable.material_dracula
    BlockTexture.NORD -> R.drawable.material_nord
    BlockTexture.SOLARIZED_LIGHT -> R.drawable.material_solarized_light
    BlockTexture.SOLARIZED_DARK -> R.drawable.material_solarized_dark
    BlockTexture.GITHUB_LIGHT -> R.drawable.material_github_light
    BlockTexture.TOKYO_NIGHT -> R.drawable.material_tokyo_night
    BlockTexture.CATPPUCCIN -> R.drawable.material_catppuccin
    BlockTexture.SYNTHWAVE -> R.drawable.material_synthwave_84
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
