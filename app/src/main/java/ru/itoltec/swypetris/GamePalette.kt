package ru.itoltec.swypetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Complete UI and seven-piece palette; its stable ID is stored in settings. */
enum class BlockFinish { BEVEL, MATTE, FROST, SATIN, NEON, RETRO }
enum class BlockTexture { CLASSIC, MONOKAI, GRUVBOX, VSCODE, DRACULA, NORD,
    SOLARIZED_LIGHT, SOLARIZED_DARK, GITHUB_LIGHT, TOKYO_NIGHT, CATPPUCCIN, SYNTHWAVE }

data class GamePalette(val id: String, val title: String, val light: Boolean,
    val background: Color, val panel: Color, val text: Color, val muted: Color,
    val grid: Color, val pieces: List<Color>, val accent: Color, val secondary: Color, val gold: Color,
    val finish: BlockFinish, val texture: BlockTexture) {
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
        muted: Long, grid: Long, finish: BlockFinish, texture: BlockTexture, vararg pieces: Long): GamePalette {
        val colors = pieces.map(::c)
        return GamePalette(id, title, light, c(bg), c(panel), c(text), c(muted), c(grid), colors,
            if (light) colors[5] else colors[0], colors[2], if (light) c(0x8A5700) else colors[1], finish, texture)
    }
    val all = listOf(
        p("classic", "Классическая", false, 0x0B1020, 0x131D32, 0xEAF0FF, 0xB8C9E4, 0x283850, BlockFinish.BEVEL,
            BlockTexture.CLASSIC,
            0x00D9EF, 0xFFE040, 0xAF55E8, 0x48D858, 0xEF4655, 0x3979ED, 0xFF982F),
        p("monokai", "Monokai", false, 0x272822, 0x34352F, 0xF8F8F2, 0xC6C6B9, 0x494A41, BlockFinish.MATTE,
            BlockTexture.MONOKAI,
            0x66D9EF, 0xE6DB74, 0xAE81FF, 0xA6E22E, 0xF92672, 0x729CFF, 0xFD971F),
        p("gruvbox", "Gruvbox Dark", false, 0x282828, 0x3C3836, 0xEBDBB2, 0xBDAE93, 0x504945, BlockFinish.RETRO,
            BlockTexture.GRUVBOX,
            0x8EC07C, 0xFABD2F, 0xD3869B, 0xB8BB26, 0xFB4934, 0x83A598, 0xFE8019),
        p("vscode", "VS Code Dark+", false, 0x1E1E1E, 0x252526, 0xD4D4D4, 0xADADAD, 0x414141, BlockFinish.MATTE,
            BlockTexture.VSCODE,
            0x4EC9B0, 0xDCDCAA, 0xC586C0, 0x6A9955, 0xF44747, 0x569CD6, 0xCE9178),
        p("dracula", "Dracula", false, 0x282A36, 0x343746, 0xF8F8F2, 0xB5BDDB, 0x44475A, BlockFinish.NEON,
            BlockTexture.DRACULA,
            0x8BE9FD, 0xF1FA8C, 0xBD93F9, 0x50FA7B, 0xFF5555, 0x809BFF, 0xFFB86C),
        p("nord", "Nord", false, 0x2E3440, 0x3B4252, 0xECEFF4, 0xD8DEE9, 0x4C566A, BlockFinish.FROST, BlockTexture.NORD,
            0x88C0D0, 0xEBCB8B, 0xB48EAD, 0xA3BE8C, 0xBF616A, 0x5E81AC, 0xD08770),
        p("solarized_light", "Solarized Light", true, 0xFDF6E3, 0xEEE8D5, 0x073642, 0x586E75, 0xC6C3B1,
            BlockFinish.RETRO, BlockTexture.SOLARIZED_LIGHT,
            0x2AA198, 0xB58900, 0x6C71C4, 0x859900, 0xDC322F, 0x268BD2, 0xCB4B16),
        p("solarized_dark", "Solarized Dark", false, 0x002B36, 0x073642, 0x93A1A1, 0x839496, 0x1A4A54,
            BlockFinish.RETRO, BlockTexture.SOLARIZED_DARK,
            0x2AA198, 0xB58900, 0x6C71C4, 0x859900, 0xDC322F, 0x268BD2, 0xCB4B16),
        p("github_light", "GitHub Light", true, 0xFFFFFF, 0xF6F8FA, 0x1F2328, 0x59636E, 0xD1D9E0,
            BlockFinish.MATTE, BlockTexture.GITHUB_LIGHT,
            0x0598A4, 0xBF8700, 0x8250DF, 0x1A7F37, 0xCF222E, 0x0969DA, 0xBC4C00),
        p("tokyo_night", "Tokyo Night", false, 0x1A1B26, 0x24283B, 0xC0CAF5, 0xA9B1D6, 0x414868, BlockFinish.NEON,
            BlockTexture.TOKYO_NIGHT,
            0x7DCFFF, 0xE0AF68, 0xBB9AF7, 0x9ECE6A, 0xF7768E, 0x7AA2F7, 0xFF9E64),
        p("catppuccin", "Catppuccin Mocha", false, 0x1E1E2E, 0x313244, 0xCDD6F4, 0xBAC2DE, 0x45475A,
            BlockFinish.SATIN, BlockTexture.CATPPUCCIN,
            0x89DCEB, 0xF9E2AF, 0xCBA6F7, 0xA6E3A1, 0xF38BA8, 0x89B4FA, 0xFAB387),
        p("synthwave_84", "SynthWave '84", false, 0x262335, 0x34294F, 0xFFFFFF, 0xC8BBDD, 0x495495,
            BlockFinish.NEON, BlockTexture.SYNTHWAVE,
            0x03EDF9, 0xFEDE5D, 0xFF7EDB, 0x72F1B8, 0xFE4450, 0x8A9BFF, 0xF97E72)
    )
    /** Maps unknown or legacy IDs to the classic palette. */
    fun find(id: String?): GamePalette = all.firstOrNull { it.id == id } ?: all.first()
}

val LocalGamePalette = staticCompositionLocalOf { GamePalettes.all.first() }

/** Every supported palette is directly selectable from one non-scrolling grid. */
@Composable
internal fun PalettePicker(model: GameViewModel) {
    val palette = LocalGamePalette.current
    val accent = palette.piece(Tetromino.T)
    SettingsLabel("Расцветка", "Выбери стиль игры")
    Spacer(Modifier.height(8.dp))
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("paletteGrid")) {
        val fontScale = LocalDensity.current.fontScale
        val columns = when {
            fontScale >= 1.4f && maxWidth < 480.dp -> 1
            fontScale >= 1.4f -> 2
            maxWidth >= 600.dp -> 4
            maxWidth >= 480.dp -> 3
            maxWidth >= 260.dp -> 2
            else -> 1
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GamePalettes.all.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { item ->
                        val selected = palette.id == item.id
                        val shape = RoundedCornerShape(9.dp)
                        Column(Modifier.weight(1f).heightIn(min = 96.dp)
                            .background(item.background, shape)
                            .border(if (selected) 3.dp else 1.dp, if (selected) accent else palette.grid, shape)
                            .clickable { model.setPalette(item.id) }
                            .semantics { this.selected = selected }
                            .testTag("palette_${item.id}_preview").padding(5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Canvas(Modifier.fillMaxWidth().height(34.dp).testTag("palette_${item.id}_art")) {
                                val side = minOf(size.width / 4.6f, size.height / 2.4f)
                                val positions = listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1, 3 to 1, 1 to 2)
                                positions.forEachIndexed { index, (column, row) ->
                                    bevelBlock(Offset(column * side + side * .15f, row * side + side * .05f),
                                        Size(side * .89f, side * .89f), item.pieces[index], item.finish, item.texture)
                                }
                            }
                            Text(item.title, Modifier.fillMaxWidth().testTag("palette_${item.id}_label"),
                                color = item.text, fontSize = 14.sp, lineHeight = 18.sp,
                                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold
                                    else androidx.compose.ui.text.font.FontWeight.Normal,
                                textAlign = TextAlign.Center, minLines = 2, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Draws a block with a flat center, narrow beveled edges, and a thin outline. */
internal fun DrawScope.bevelBlock(at: Offset, size: Size, color: Color, finish: BlockFinish, texture: BlockTexture,
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
    } else if (finish == BlockFinish.SATIN || finish == BlockFinish.FROST) {
        drawRect(brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f * alpha),
            Color.Transparent, Color.Black.copy(alpha = .15f * alpha)), startY = y, endY = y + h), topLeft = at,
                size = size)
        drawRect(lerp(color, if (finish == BlockFinish.FROST) Color.White else Color.Black, .36f)
            .copy(alpha = alpha), at, size, style = Stroke(1.dp.toPx()))
    } else {
        /** Draws a quadrilateral bevel with the block's effective transparency. */
        fun face(shade: Color, a: Offset, b: Offset, c: Offset, d: Offset) {
            val points = listOf(a, b, c, d)
            val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x,
                it.y) }; close() }
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
    drawBlockTexture(at, size, color, texture, alpha)
}

/** Small procedural marks give every theme its own material without hiding cell edges. */
private fun DrawScope.drawBlockTexture(at: Offset, size: Size, color: Color,
    texture: BlockTexture, alpha: Float) {
    val x = at.x; val y = at.y; val w = size.width; val h = size.height
    val unit = minOf(w, h)
    val bright = lerp(color, Color.White, .66f).copy(alpha = alpha * .47f)
    val dark = lerp(color, Color.Black, .62f).copy(alpha = alpha * .42f)
    val stroke = maxOf(unit * .025f, .55.dp.toPx())
    fun line(c: Color, x1: Float, y1: Float, x2: Float, y2: Float, weight: Float = 1f) =
        drawLine(c, Offset(x + w * x1, y + h * y1), Offset(x + w * x2, y + h * y2), stroke * weight)
    when (texture) {
        BlockTexture.CLASSIC -> {
            line(bright, .20f, .22f, .70f, .22f)
            line(dark, .75f, .72f, .75f, .82f)
        }
        BlockTexture.MONOKAI -> {
            line(bright, .18f, .28f, .53f, .28f)
            line(bright.copy(alpha = bright.alpha * .5f), .18f, .36f, .38f, .36f)
        }
        BlockTexture.GRUVBOX -> {
            line(dark, .17f, .42f, .72f, .42f)
            line(bright.copy(alpha = bright.alpha * .5f), .28f, .62f, .80f, .62f)
        }
        BlockTexture.VSCODE -> {
            line(bright, .20f, .20f, .20f, .65f)
            line(bright, .20f, .65f, .52f, .65f)
        }
        BlockTexture.DRACULA -> {
            line(bright, .25f, .75f, .75f, .25f)
            drawCircle(bright, unit * .045f, Offset(x + w * .72f, y + h * .27f))
        }
        BlockTexture.NORD -> {
            line(bright, .18f, .27f, .75f, .27f)
            line(bright.copy(alpha = bright.alpha * .5f), .32f, .43f, .62f, .43f)
            drawCircle(bright, unit * .025f, Offset(x + w * .25f, y + h * .60f))
        }
        BlockTexture.SOLARIZED_LIGHT -> {
            for (row in 0..2) line(dark.copy(alpha = dark.alpha * .6f), .22f,
                .34f + row * .14f, .78f, .34f + row * .14f)
        }
        BlockTexture.SOLARIZED_DARK -> {
            line(bright, .20f, .68f, .55f, .32f)
            line(dark, .45f, .72f, .78f, .38f)
        }
        BlockTexture.GITHUB_LIGHT -> {
            for (column in 0..2) drawCircle(dark, unit * .028f,
                Offset(x + w * (.32f + column * .18f), y + h * .5f))
        }
        BlockTexture.TOKYO_NIGHT -> {
            line(bright, .20f, .72f, .45f, .34f)
            line(bright.copy(alpha = bright.alpha * .65f), .50f, .72f, .75f, .34f)
        }
        BlockTexture.CATPPUCCIN -> {
            drawCircle(bright.copy(alpha = bright.alpha * .65f), unit * .16f,
                Offset(x + w * .68f, y + h * .32f))
            drawCircle(color.copy(alpha = alpha * .55f), unit * .11f,
                Offset(x + w * .68f, y + h * .32f))
        }
        BlockTexture.SYNTHWAVE -> {
            for (row in 0..2) line(bright, .17f, .30f + row * .19f,
                .83f, .30f + row * .19f, if (row == 1) 1.2f else .7f)
        }
    }
}
