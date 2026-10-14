package ru.itoltec.swypetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun MenuToggleArt(kind: String, enabled: Boolean, modifier: Modifier,
    source: MutableInteractionSource) {
    val skin = checkNotNull(LocalMenuSkin.current)
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val id = menuDrawableId(kind)
    val artModifier = modifier.drawWithContent {
            drawContent()
            val radius = if (kind.startsWith("checkbox")) 4.dp.toPx() else size.height / 2
            if (enabled && pressed) drawRoundRect(skin.control.copy(alpha = skin.pressedTint),
                cornerRadius = CornerRadius(radius))
            if (enabled && focused) drawRoundRect(skin.focus,
                cornerRadius = CornerRadius(radius), style = Stroke(skin.focusStrokeDp.dp.toPx()))
        }
    if (enabled) Image(painterResource(id), contentDescription = null, modifier = artModifier)
    else {
        val context = LocalContext.current
        val disabledArt = remember(context, id, skin) {
            disabledMenuDrawable(context.resources, id, skin.disabledContentAlpha, skin.disabledBorderAlpha)
        }
        Canvas(artModifier) {
            drawIntoCanvas { canvas ->
                disabledArt.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                disabledArt.draw(canvas.nativeCanvas)
            }
        }
    }
}

@Composable
internal fun MenuSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }) {
    if (LocalMenuSkin.current == null) {
        Switch(checked, onCheckedChange, modifier, enabled = enabled, interactionSource = interactionSource)
        return
    }
    val source = interactionSource
    Box(contentAlignment = Alignment.Center) {
        MenuToggleArt(if (checked) "switch_on" else "switch_off", enabled, Modifier.size(52.dp, 32.dp), source)
        MenuNativeControl {
            Switch(checked, onCheckedChange, modifier = modifier, enabled = enabled, interactionSource = source,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Transparent,
                    checkedTrackColor = Color.Transparent, checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color.Transparent, uncheckedTrackColor = Color.Transparent,
                    uncheckedBorderColor = Color.Transparent, disabledCheckedThumbColor = Color.Transparent,
                    disabledCheckedTrackColor = Color.Transparent, disabledCheckedBorderColor = Color.Transparent,
                    disabledUncheckedThumbColor = Color.Transparent, disabledUncheckedTrackColor = Color.Transparent,
                    disabledUncheckedBorderColor = Color.Transparent))
        }
    }
}

@Composable
internal fun MenuCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }) {
    if (LocalMenuSkin.current == null) {
        Checkbox(checked, onCheckedChange, modifier, enabled, interactionSource = interactionSource)
        return
    }
    val source = interactionSource
    Box(contentAlignment = Alignment.Center) {
        MenuToggleArt(if (checked) "checkbox_on" else "checkbox_off", enabled, Modifier.size(24.dp), source)
        MenuNativeControl {
            Checkbox(checked, onCheckedChange, modifier = modifier, enabled = enabled,
                interactionSource = source, colors = CheckboxDefaults.colors(
                checkedColor = Color.Transparent, uncheckedColor = Color.Transparent,
                checkmarkColor = Color.Transparent, disabledCheckedColor = Color.Transparent,
                disabledUncheckedColor = Color.Transparent, disabledIndeterminateColor = Color.Transparent))
        }
    }
}

@Composable
internal fun MenuRadioButton(selected: Boolean, onClick: (() -> Unit)?,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }) {
    if (LocalMenuSkin.current == null) {
        RadioButton(selected, onClick, modifier, enabled, interactionSource = interactionSource)
        return
    }
    val source = interactionSource
    Box(contentAlignment = Alignment.Center) {
        MenuToggleArt(if (selected) "radio_on" else "radio_off", enabled, Modifier.size(24.dp), source)
        MenuNativeControl {
            RadioButton(selected, onClick, modifier = modifier, enabled = enabled,
                interactionSource = source, colors = RadioButtonDefaults.colors(
                selectedColor = Color.Transparent, unselectedColor = Color.Transparent,
                disabledSelectedColor = Color.Transparent, disabledUnselectedColor = Color.Transparent))
        }
    }
}
