package ru.itoltec.swypetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Complete UI and seven-piece palette; its stable ID is stored in settings. */
enum class BlockFinish { BEVEL, MATTE, FROST, SATIN, NEON, RETRO }

data class GamePalette(val id: String, val title: String, val light: Boolean,
    val background: Color, val panel: Color, val text: Color, val muted: Color,
    val grid: Color, val pieces: List<Color>, val accent: Color, val secondary: Color, val gold: Color,
    val finish: BlockFinish) {
    /** Returns a piece color in I, O, T, S, Z, J, L order. */
    fun piece(type: Tetromino): Color = pieces[type.ordinal]

    val glass: Color get() = lerp(background, panel, if (light) .72f else .45f)
    val artSky: Color get() = lerp(background, piece(Tetromino.T), if (light) .07f else .18f)
    val artSun: Color get() = lerp(gold, background, if (light) .45f else .15f)
    val ghostAlpha: Float get() = if (light) .7f else .55f
    val fruitOutline: Color get() = lerp(background, text, if (light) .62f else .23f)
    val fruitLeaf: Color get() = lerp(piece(Tetromino.S), background, if (light) .12f else .18f)

    /** Builds semantic Material colors for surfaces, dialogs, and switches. */
    fun scheme(): ColorScheme {
        val base = if (light) lightColorScheme() else darkColorScheme()
        return base.copy(primary = accent, onPrimary = background, primaryContainer = panel,
            onPrimaryContainer = text, secondary = secondary, onSecondary = background,
            secondaryContainer = panel, onSecondaryContainer = text, tertiary = gold,
            onTertiary = background, tertiaryContainer = panel, onTertiaryContainer = text,
            background = background, onBackground = text, surface = background, onSurface = text,
            surfaceVariant = panel, onSurfaceVariant = muted, outline = muted, outlineVariant = grid,
            surfaceDim = panel, surfaceBright = background, surfaceContainer = panel,
            surfaceContainerLow = panel, surfaceContainerLowest = background,
            surfaceContainerHigh = panel, surfaceContainerHighest = panel,
            surfaceTint = accent, inverseSurface = text, inverseOnSurface = background,
            inversePrimary = background, error = piece(Tetromino.Z), onError = background,
            errorContainer = panel, onErrorContainer = text)
    }
}

/** Stable palettes, including light-theme shades adjusted for contrast. */
object GamePalettes {
    /** Converts an RGB value to a Compose color without Android Color. */
    private fun c(value: Long) = Color(0xFF000000 or value)
    /** Builds a palette from the published base shades of a theme. */
    private fun p(id: String, title: String, light: Boolean, bg: Long, panel: Long, text: Long,
        muted: Long, grid: Long, finish: BlockFinish, vararg pieces: Long): GamePalette {
        val colors = pieces.map(::c)
        return GamePalette(id, title, light, c(bg), c(panel), c(text), c(muted), c(grid), colors,
            if (light) colors[5] else colors[0], colors[2], if (light) c(0x8A5700) else colors[1], finish)
    }
    val all = listOf(
        p("classic", "Классическая", false, 0x0B1020, 0x131D32, 0xEAF0FF, 0xB8C9E4, 0x283850, BlockFinish.BEVEL,
            0x00D9EF, 0xFFE040, 0xAF55E8, 0x48D858, 0xEF4655, 0x3979ED, 0xFF982F),
        p("monokai", "Monokai", false, 0x272822, 0x34352F, 0xF8F8F2, 0xC6C6B9, 0x494A41, BlockFinish.MATTE,
            0x66D9EF, 0xE6DB74, 0xAE81FF, 0xA6E22E, 0xF92672, 0x729CFF, 0xFD971F),
        p("gruvbox", "Gruvbox Dark", false, 0x282828, 0x3C3836, 0xEBDBB2, 0xBDAE93, 0x504945, BlockFinish.RETRO,
            0x8EC07C, 0xFABD2F, 0xD3869B, 0xB8BB26, 0xFB4934, 0x83A598, 0xFE8019),
        p("vscode", "VS Code Dark+", false, 0x1E1E1E, 0x252526, 0xD4D4D4, 0xADADAD, 0x414141, BlockFinish.MATTE,
            0x4EC9B0, 0xDCDCAA, 0xC586C0, 0x6A9955, 0xF44747, 0x569CD6, 0xCE9178),
        p("dracula", "Dracula", false, 0x282A36, 0x343746, 0xF8F8F2, 0xB5BDDB, 0x44475A, BlockFinish.NEON,
            0x8BE9FD, 0xF1FA8C, 0xBD93F9, 0x50FA7B, 0xFF5555, 0x809BFF, 0xFFB86C),
        p("nord", "Nord", false, 0x2E3440, 0x3B4252, 0xECEFF4, 0xD8DEE9, 0x4C566A, BlockFinish.FROST,
            0x88C0D0, 0xEBCB8B, 0xB48EAD, 0xA3BE8C, 0xBF616A, 0x5E81AC, 0xD08770),
        p("solarized_light", "Solarized Light", true, 0xFDF6E3, 0xEEE8D5, 0x073642, 0x586E75, 0xC6C3B1, BlockFinish.RETRO,
            0x2AA198, 0xB58900, 0x6C71C4, 0x859900, 0xDC322F, 0x268BD2, 0xCB4B16),
        p("solarized_dark", "Solarized Dark", false, 0x002B36, 0x073642, 0x93A1A1, 0x839496, 0x1A4A54, BlockFinish.RETRO,
            0x2AA198, 0xB58900, 0x6C71C4, 0x859900, 0xDC322F, 0x268BD2, 0xCB4B16),
        p("github_light", "GitHub Light", true, 0xFFFFFF, 0xF6F8FA, 0x1F2328, 0x59636E, 0xD1D9E0, BlockFinish.MATTE,
            0x0598A4, 0xBF8700, 0x8250DF, 0x1A7F37, 0xCF222E, 0x0969DA, 0xBC4C00),
        p("tokyo_night", "Tokyo Night", false, 0x1A1B26, 0x24283B, 0xC0CAF5, 0xA9B1D6, 0x414868, BlockFinish.NEON,
            0x7DCFFF, 0xE0AF68, 0xBB9AF7, 0x9ECE6A, 0xF7768E, 0x7AA2F7, 0xFF9E64),
        p("catppuccin", "Catppuccin Mocha", false, 0x1E1E2E, 0x313244, 0xCDD6F4, 0xBAC2DE, 0x45475A, BlockFinish.SATIN,
            0x89DCEB, 0xF9E2AF, 0xCBA6F7, 0xA6E3A1, 0xF38BA8, 0x89B4FA, 0xFAB387),
        p("synthwave_84", "SynthWave '84", false, 0x262335, 0x34294F, 0xFFFFFF, 0xC8BBDD, 0x495495, BlockFinish.NEON,
            0x03EDF9, 0xFEDE5D, 0xFF7EDB, 0x72F1B8, 0xFE4450, 0x8A9BFF, 0xF97E72)
    )
    /** Maps unknown or legacy IDs to the classic palette. */
    fun find(id: String?): GamePalette = all.firstOrNull { it.id == id } ?: all.first()
}

val LocalGamePalette = staticCompositionLocalOf { GamePalettes.all.first() }

/** Shows palette choices with a shared preview of seven beveled blocks. */
@Composable
internal fun PalettePicker(model: GameViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val palette = LocalGamePalette.current
    Text("Расцветка", style = MaterialTheme.typography.titleLarge)
    Box {
        val accent = palette.piece(Tetromino.T)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("palettePicker"),
            colors = paletteButtonColors(accent, ActionStyle.SECONDARY),
            border = paletteButtonBorder(accent, ActionStyle.SECONDARY)) {
            Text(palette.title, Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            GamePalettes.all.forEach { item ->
                DropdownMenuItem(text = { Text(item.title) }, modifier = Modifier.testTag("palette_${item.id}"),
                    onClick = { model.setPalette(item.id); expanded = false })
            }
        }
    }
    Canvas(Modifier.fillMaxWidth().height(42.dp).testTag("palettePreview")) {
        val step = size.width / 7
        Tetromino.entries.forEachIndexed { index, type ->
            bevelBlock(Offset(index * step + 3.dp.toPx(), 3.dp.toPx()),
                Size(step - 6.dp.toPx(), size.height - 6.dp.toPx()), palette.piece(type), palette.finish)
        }
    }
}

/** Draws a block with a flat center, narrow beveled edges, and a thin outline. */
internal fun DrawScope.bevelBlock(at: Offset, size: Size, color: Color, finish: BlockFinish,
    alpha: Float = 1f, outline: Boolean = false) {
    if (outline) {
        drawRect(color.copy(alpha = alpha), at, size, style = Stroke(1.5.dp.toPx()))
        return
    }
    val b = minOf(size.width, size.height) * .08f
    val x = at.x; val y = at.y; val w = size.width; val h = size.height
    drawRect(color.copy(alpha = alpha), at, size)
    if (finish == BlockFinish.MATTE) {
        drawRect(lerp(color, Color.Black, .28f).copy(alpha = alpha), at, size, style = Stroke(1.dp.toPx()))
        return
    }
    if (finish == BlockFinish.SATIN || finish == BlockFinish.FROST) {
        drawRect(brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f * alpha),
            Color.Transparent, Color.Black.copy(alpha = .15f * alpha)), startY = y, endY = y + h), topLeft = at, size = size)
        drawRect(lerp(color, if (finish == BlockFinish.FROST) Color.White else Color.Black, .36f)
            .copy(alpha = alpha), at, size, style = Stroke(1.dp.toPx()))
        return
    }
    /** Draws a quadrilateral bevel with the block's effective transparency. */
    fun face(shade: Color, a: Offset, b: Offset, c: Offset, d: Offset) {
        val points = listOf(a, b, c, d)
        val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
        drawPath(path, shade.copy(alpha = alpha))
    }
    face(lerp(color, Color.White, .50f), Offset(x,y), Offset(x+w,y), Offset(x+w-b,y+b), Offset(x+b,y+b))
    face(lerp(color, Color.White, .28f), Offset(x,y), Offset(x+b,y+b), Offset(x+b,y+h-b), Offset(x,y+h))
    face(lerp(color, Color.Black, .42f), Offset(x,y+h), Offset(x+b,y+h-b), Offset(x+w-b,y+h-b), Offset(x+w,y+h))
    face(lerp(color, Color.Black, .25f), Offset(x+w,y), Offset(x+w,y+h), Offset(x+w-b,y+h-b), Offset(x+w-b,y+b))
    drawRect(lerp(color, Color.Black, .55f).copy(alpha = alpha), at, size, style = Stroke(.65.dp.toPx()))
    if (finish == BlockFinish.NEON) {
        drawRect(lerp(color, Color.White, .65f).copy(alpha = alpha),
            Offset(x + b, y + b), Size(w - 2*b, h - 2*b), style = Stroke(.8.dp.toPx()))
    } else if (finish == BlockFinish.RETRO) {
        for (stripe in 1..2) drawLine(lerp(color, Color.Black, .34f).copy(alpha = alpha),
            Offset(x + b, y + h * stripe / 3), Offset(x + w - b, y + h * stripe / 3), .6.dp.toPx())
    }
}
