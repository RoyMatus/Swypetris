package ru.itoltec.swypetris

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject

/** Semantic menu colors. Gameplay keeps its original palette and piece materials. */
internal data class MenuSkin(
    val id: String, val light: Boolean, val background: Color, val panel: Color,
    val text: Color, val muted: Color, val border: Color, val control: Color,
    val onControl: Color, val primaryFill: Color, val onPrimary: Color,
    val focus: Color, val scrim: Color, val roles: Map<String, Color>,
    val radiusDp: Float, val strokeDp: Float, val disabledContentAlpha: Float,
    val disabledBorderAlpha: Float, val focusStrokeDp: Float,
    val pressedTint: Float, val selectedTint: Float,
) {
    fun role(role: String): Color = roles.getValue(role)

    fun scheme(): ColorScheme {
        val base = if (light) lightColorScheme() else darkColorScheme()
        return base.copy(primary = control, onPrimary = onControl,
            primaryContainer = primaryFill, onPrimaryContainer = onPrimary,
            secondary = control, onSecondary = onControl,
            secondaryContainer = panel, onSecondaryContainer = text,
            tertiary = role("results"), onTertiary = background,
            background = background, onBackground = text, surface = panel, onSurface = text,
            surfaceVariant = panel, onSurfaceVariant = muted, outline = border,
            outlineVariant = border.copy(alpha = disabledBorderAlpha),
            error = role("destructive"), onError = text, errorContainer = panel, onErrorContainer = text,
            tertiaryContainer = panel, onTertiaryContainer = text,
            inverseSurface = panel, inverseOnSurface = text, inversePrimary = control,
            surfaceTint = panel, surfaceBright = panel, surfaceDim = background,
            surfaceContainer = panel, surfaceContainerHigh = panel, surfaceContainerHighest = panel,
            surfaceContainerLow = panel, surfaceContainerLowest = background, scrim = scrim)
    }

    fun palette(original: GamePalette): GamePalette = original.copy(light = light,
        background = background, panel = panel, text = text, muted = muted,
        accent = control, secondary = role("help"), gold = role("results"))
}

internal val LocalMenuSkin = staticCompositionLocalOf<MenuSkin?> { null }

@Composable
internal fun menuUiPalette(): GamePalette {
    val original = LocalGamePalette.current
    return LocalMenuSkin.current?.palette(original) ?: original
}

@Composable
internal fun rememberMenuSkin(palette: GamePalette): MenuSkin {
    val context = LocalContext.current
    val skins = remember(context.applicationContext) {
        val json = context.resources.openRawResource(R.raw.menu_ui_tokens)
            .bufferedReader().use { JSONObject(it.readText()) }
        val states = json.getJSONObject("states")
        val themes = json.getJSONArray("themes")
        buildMap {
            repeat(themes.length()) { index ->
                val theme = themes.getJSONObject(index)
                fun color(key: String) = Color(android.graphics.Color.parseColor(theme.getString(key)))
                val roleJson = theme.getJSONObject("roles")
                val roles = roleJson.keys().asSequence().associateWith {
                    Color(android.graphics.Color.parseColor(roleJson.getString(it)))
                }
                val skin = MenuSkin(theme.getString("id"), theme.getBoolean("light"),
                    color("background"), color("panel"), color("text"), color("muted"),
                    color("border"), color("control"), color("onControl"),
                    color("primaryFill"), color("onPrimary"), color("focus"), color("scrim"),
                    roles, theme.getDouble("radiusDp").toFloat(), theme.getDouble("strokeDp").toFloat(),
                    states.getDouble("disabledContentAlpha").toFloat(),
                    states.getDouble("disabledBorderAlpha").toFloat(),
                    states.getDouble("focusStrokeDp").toFloat(),
                    states.getDouble("pressedTint").toFloat(), states.getDouble("selectedTint").toFloat())
                put(skin.id, skin)
            }
        }
    }
    return skins.getValue(palette.id)
}
