package ru.itoltec.swypetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CardBorder = Color(0xFF354059)
private val CardRadius = 13.dp
private val BlockGap = 4.dp
private val NormalBlockSize = 32.dp
private val MaxStackedBlockSize = 40.dp
private val StackedCardWidth = 294.dp
private const val STACKED_FONT_SCALE = 1.4f
private const val NORMAL_COLUMNS = 4
private const val FIRST_ROW_COUNT = 4
private const val PALETTE_SIZE = 7

/** Approved full-width theme cards; the settings screen remains the sole scroll owner. */
@Composable
internal fun WidePalettePicker(palette: GamePalette, onSelect: (String) -> Unit) {
    SettingsLabel("Расцветка", "Выбери стиль игры")
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().testTag("paletteGrid").selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GamePalettes.all.forEach { item ->
            WidePaletteCard(item, palette.id == item.id, palette.piece(Tetromino.T)) {
                onSelect(item.id)
            }
        }
    }
}

/** Selection is announced once, on the entire card; the check and artwork are decorative. */
@Composable
private fun WidePaletteCard(item: GamePalette, selected: Boolean, accent: Color, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(CardRadius)
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(shape)
        .background(item.background).border(if (selected) 3.dp else 2.dp,
            if (selected) accent else CardBorder, shape)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
        .testTag("palette_${item.id}_preview")) {
        val stacked = maxWidth < StackedCardWidth || LocalDensity.current.fontScale >= STACKED_FONT_SCALE
        if (stacked) {
            // 2dp border + 12dp inner padding on each side, and six 4dp gaps.
            val blockSize = minOf(MaxStackedBlockSize, ((maxWidth - 28.dp - BlockGap * 6) / PALETTE_SIZE)
                .coerceAtLeast(1.dp))
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PaletteCardTitle(item, true, Modifier.fillMaxWidth().padding(end = 20.dp))
                PaletteCardArtwork(item, blockSize, true)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                PaletteCardTitle(item, false, Modifier.weight(1f))
                PaletteCardArtwork(item, NormalBlockSize, false)
            }
        }
        if (selected) {
            val indicatorSize = if (stacked) 19.dp else 16.dp
            val alignment = if (stacked) Alignment.TopEnd else Alignment.TopStart
            Box(Modifier.align(alignment).padding(horizontal = if (stacked) 7.dp else 5.dp, vertical = 7.dp)
                .size(indicatorSize).background(accent, CircleShape)
                .testTag("palette_${item.id}_check"), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** Unlimited wrapping preserves complete theme names at large accessibility font scales. */
@Composable
private fun PaletteCardTitle(item: GamePalette, stacked: Boolean, modifier: Modifier) {
    Text(item.title, modifier.testTag("palette_${item.id}_label"), color = item.text,
        fontSize = if (stacked) 18.sp else 19.sp, lineHeight = if (stacked) 26.sp else 27.sp,
        fontWeight = FontWeight.SemiBold)
}

/** Uses the production connected atlas, in the existing I/O/T/S/Z/J/L palette order. */
@Composable
private fun PaletteCardArtwork(item: GamePalette, blockSize: Dp, singleRow: Boolean) {
    val material = blockMaterial(item)
    val columns = if (singleRow) PALETTE_SIZE else NORMAL_COLUMNS
    val rows = if (singleRow) 1 else 2
    Canvas(Modifier.size(blockSize * columns + BlockGap * (columns - 1),
        blockSize * rows + BlockGap * (rows - 1)).testTag("palette_${item.id}_art")) {
        val side = blockSize.toPx()
        val pitch = side + BlockGap.toPx()
        Tetromino.entries.forEachIndexed { index, type ->
            val secondRow = !singleRow && index >= FIRST_ROW_COUNT
            val column = if (secondRow) index - FIRST_ROW_COUNT + .5f else index.toFloat()
            bevelBlock(Offset(column * pitch, if (secondRow) pitch else 0f), Size(side, side),
                item.piece(type), item.finish, item.texture, material = material, type = type)
        }
    }
}
