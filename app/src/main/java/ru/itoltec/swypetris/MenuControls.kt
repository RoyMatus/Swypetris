package ru.itoltec.swypetris

import android.graphics.drawable.Drawable
import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderState
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Authored frames own menu feedback; native components still own interactions. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MenuNativeControl(content: @Composable () -> Unit) {
    if (LocalMenuSkin.current == null) content()
    else CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
}

@Composable
@SuppressLint("DiscouragedApi")
internal fun menuDrawableId(kind: String): Int {
    val context = LocalContext.current
    val skin = checkNotNull(LocalMenuSkin.current)
    return remember(context, skin.id, kind) {
        context.resources.getIdentifier("menu_${skin.id}_$kind", "drawable", context.packageName)
            .also { check(it != 0) { "Missing menu artwork: ${skin.id}/$kind" } }
    }
}

@Composable
@SuppressLint("DiscouragedApi")
internal fun menuActionTextColor(role: String, enabled: Boolean = true): Color {
    val skin = LocalMenuSkin.current ?: return LocalGamePalette.current.text
    val context = LocalContext.current
    val colors = remember(context, skin.id, role) {
        val id = context.resources.getIdentifier("menu_${skin.id}_${role}_text", "color", context.packageName)
        checkNotNull(ContextCompat.getColorStateList(context, id))
    }
    val state = if (enabled) intArrayOf(android.R.attr.state_enabled) else intArrayOf(-android.R.attr.state_enabled)
    return Color(colors.getColorForState(state, colors.defaultColor))
}

/** Native controls still own focus, click, semantics, text and measured bounds. */
@Composable
internal fun Modifier.menuActionFrame(role: String, interactionSource: MutableInteractionSource,
    enabled: Boolean = true, selected: Boolean = false): Modifier {
    if (LocalMenuSkin.current == null) return this
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val state = menuControlState(enabled, pressed, focused, selected).name.lowercase()
    return menuSurfaceArt("${role}_$state")
}

@Composable
internal fun Modifier.menuInputFrame(source: MutableInteractionSource, enabled: Boolean = true,
    error: Boolean = false): Modifier {
    val skin = LocalMenuSkin.current ?: return this
    val focused by source.collectIsFocusedAsState()
    return menuSurfaceArt("input").drawWithContent {
        drawContent()
        if (error || focused) drawRoundRect(
            color = if (error) skin.role("destructive") else skin.focus,
            cornerRadius = CornerRadius(skin.radiusDp.dp.toPx()),
            style = Stroke((if (error) 2f else skin.focusStrokeDp).dp.toPx()))
        if (!enabled) drawRoundRect(skin.panel.copy(alpha = 1f - skin.disabledBorderAlpha),
            cornerRadius = CornerRadius(skin.radiusDp.dp.toPx()))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MenuSliderThumb(modifier: Modifier = Modifier, enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }) {
    if (LocalMenuSkin.current == null) {
        SliderDefaults.Thumb(interactionSource, modifier, enabled = enabled)
        return
    }
    MenuToggleArt("slider_thumb", enabled, modifier.size(24.dp), interactionSource)
}

@Composable
internal fun menuSliderColors(): SliderColors {
    val skin = LocalMenuSkin.current ?: return SliderDefaults.colors()
    return SliderDefaults.colors(thumbColor = skin.control, activeTrackColor = skin.control,
        inactiveTrackColor = skin.border.copy(alpha = skin.disabledBorderAlpha),
        activeTickColor = skin.onControl, inactiveTickColor = skin.muted,
        disabledThumbColor = skin.control.copy(alpha = skin.disabledContentAlpha),
        disabledActiveTrackColor = skin.control.copy(alpha = skin.disabledBorderAlpha),
        disabledInactiveTrackColor = skin.border.copy(alpha = skin.disabledBorderAlpha))
}

/** Supplied track container, with its sample segment replaced by the live value. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MenuSliderTrack(state: SliderState, modifier: Modifier = Modifier,
    enabled: Boolean = true) {
    val skin = LocalMenuSkin.current
    if (skin == null) {
        SliderDefaults.Track(state, modifier, enabled = enabled)
        return
    }
    val context = LocalContext.current
    val id = menuDrawableId("slider_track")
    val base = remember(context, id, enabled, skin) {
        disabledMenuDrawable(context.resources, id, if (enabled) 1f else skin.disabledContentAlpha,
            if (enabled) 1f else skin.disabledBorderAlpha, hideActiveSegment = true)
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sourceDensity = context.resources.displayMetrics.density
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        drawMenuSurface(base, sourceDensity)
        val range = state.valueRange.endInclusive - state.valueRange.start
        val fraction = if (range > 0f) ((state.value - state.valueRange.start) / range).coerceIn(0f, 1f) else 0f
        val inset = 6.dp.toPx().coerceAtMost(size.width / 2)
        val available = size.width - 2 * inset
        val start = if (rtl) size.width - inset else inset
        val end = start + available * fraction * if (rtl) -1f else 1f
        if (fraction > 0f) drawLine(skin.control.copy(alpha = if (enabled) 1f else skin.disabledBorderAlpha),
            androidx.compose.ui.geometry.Offset(start, size.height / 2),
            androidx.compose.ui.geometry.Offset(end, size.height / 2), strokeWidth = 4.dp.toPx())
    }
}

@Composable
internal fun menuInputColors(): TextFieldColors {
    val skin = LocalMenuSkin.current ?: return OutlinedTextFieldDefaults.colors()
    return OutlinedTextFieldDefaults.colors(focusedTextColor = skin.text, unfocusedTextColor = skin.text,
        disabledTextColor = skin.text.copy(alpha = skin.disabledContentAlpha), errorTextColor = skin.text,
        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent, errorContainerColor = Color.Transparent,
        cursorColor = skin.control, errorCursorColor = skin.role("destructive"),
        focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent, errorBorderColor = Color.Transparent,
        focusedLabelColor = skin.text, unfocusedLabelColor = skin.muted,
        disabledLabelColor = skin.muted.copy(alpha = skin.disabledContentAlpha), errorLabelColor = skin.text)
}

/** Inflate unchanged vector paths with independent disabled fill/border alpha. */
internal fun disabledMenuDrawable(resources: android.content.res.Resources, id: Int,
    contentAlpha: Float, borderAlpha: Float, hideActiveSegment: Boolean = false): Drawable {
    return readMenuVector(resources, id, contentAlpha, borderAlpha, hideActiveSegment)
}
