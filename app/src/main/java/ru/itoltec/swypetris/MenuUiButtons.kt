package ru.itoltec.swypetris

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/** Changes only menu decoration; the native action retains layout, interaction and semantics. */
@Composable
internal fun MenuOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    role: String = "link", selected: Boolean = false,
    content: @Composable RowScope.() -> Unit) {
    val skin = LocalMenuSkin.current
    MenuNativeControl { OutlinedButton(onClick, modifier.menuActionFrame(role, interactionSource, enabled, selected),
        enabled = enabled, shape = shape,
        colors = if (skin == null) colors else ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent, contentColor = menuActionTextColor(role),
            disabledContainerColor = Color.Transparent, disabledContentColor = menuActionTextColor(role, false)),
        border = if (skin == null) border else null, contentPadding = contentPadding,
        interactionSource = interactionSource, content = content) }
}

@Composable
internal fun MenuTextButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, role: String = "back", content: @Composable RowScope.() -> Unit) {
    val skin = LocalMenuSkin.current
    val interaction = remember { MutableInteractionSource() }
    MenuNativeControl { TextButton(onClick, modifier.menuActionFrame(role, interaction, enabled), enabled = enabled,
        interactionSource = interaction,
        colors = if (skin == null) ButtonDefaults.textButtonColors() else
            ButtonDefaults.textButtonColors(contentColor = menuActionTextColor(role),
                disabledContentColor = menuActionTextColor(role, false)), content = content) }
}
