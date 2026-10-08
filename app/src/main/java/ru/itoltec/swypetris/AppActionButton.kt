package ru.itoltec.swypetris

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Visual emphasis for a palette-colored action, independent of its size or destination. */

/** A palette tint with a readable label, shared by menu tiles and all other buttons. */
@Composable
internal fun paletteButtonColors(accent: Color, style: ActionStyle): ButtonColors {
    val palette = LocalGamePalette.current
    val alpha = when (style) {
        ActionStyle.PRIMARY -> .18f
        ActionStyle.SECONDARY -> .13f
        ActionStyle.TEXT -> .08f
    }
    val background = lerp(palette.background, accent, alpha)
    val contrast = (maxOf(accent.luminance(), background.luminance()) + .05f) /
        (minOf(accent.luminance(), background.luminance()) + .05f)
    val label = if (contrast >= 4.5f) accent else palette.text
    return ButtonDefaults.outlinedButtonColors(containerColor = background, contentColor = label,
        disabledContainerColor = background, disabledContentColor = label)
}

/** Keeps the selected palette color visible even when the button fill is subtle. */
internal fun paletteButtonBorder(accent: Color, style: ActionStyle) =
    BorderStroke(if (style == ActionStyle.PRIMARY) 2.5.dp else 2.dp, accent.copy(alpha = .85f))

/** Shared palette treatment; priority changes emphasis without changing the outlined style. */
@Composable
internal fun AppActionButton(label: String, style: ActionStyle, modifier: Modifier = Modifier,
    accent: Color = LocalGamePalette.current.accent,
    onClick: () -> Unit) {
    val height = if (style == ActionStyle.TEXT) 48.dp else 56.dp
    val shape = RoundedCornerShape(12.dp)
    val contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    OutlinedButton(onClick, modifier.heightIn(min = height), shape = shape,
        colors = paletteButtonColors(accent, style), border = paletteButtonBorder(accent, style),
        contentPadding = contentPadding) { Text(label) }
}
