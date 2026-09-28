package ru.itoltec.swypetris

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The four panels follow the approved settings reference while keeping real settings. */
@Composable
internal fun SettingsScreen(model: GameViewModel) {
    val palette = LocalGamePalette.current
    val gameplay = palette.piece(Tetromino.I)
    val assistance = palette.piece(Tetromino.T)
    val audio = palette.piece(Tetromino.I)
    val appearance = lerp(palette.piece(Tetromino.T), palette.piece(Tetromino.Z), .35f)
    Column(Modifier.fillMaxSize().background(palette.background).navigationBarsPadding()
        .verticalScroll(rememberScrollState())) {
        SettingsHeader(palette, model::menu)
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsPanel("ИГРОВОЙ ПРОЦЕСС", "Основные параметры игры", Icons.Outlined.SportsEsports, gameplay) {
                DifficultySetting(model, gameplay)
            }
            SettingsPanel("ПОМОЩЬ В ИГРЕ", "Подсказки и дополнительная информация", Icons.Outlined.Lightbulb, assistance) {
                SettingsToggle("Подсказки", "Тень падения и следующая фигура", "hints",
                    model.hintsEnabled, model::setHints)
            }
            SettingsPanel("ЗВУК И ВИБРАЦИЯ", "Аудио и тактильная обратная связь", Icons.AutoMirrored.Outlined.VolumeUp, audio) {
                MusicPicker(model)
                SettingsDivider(audio)
                SettingsToggle("Звуковые эффекты", "Звуки перемещения и линий", "sound",
                    model.soundEnabled, model::setSound)
                SettingsDivider(audio)
                SettingsToggle("Вибрация", "Тактильная обратная связь", "vibration",
                    model.vibrationEnabled, model::setVibration)
            }
            SettingsPanel("ВНЕШНИЙ ВИД", "Цветовая тема и оформление", Icons.Outlined.Palette, appearance) {
                PalettePicker(model)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsHeader(palette: GamePalette, onBack: () -> Unit) {
    val statusHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val art = ImageBitmap.imageResource(if (palette.light) R.drawable.settings_day_header
        else R.drawable.settings_synthwave_header)
    Box(Modifier.fillMaxWidth().height(90.dp + statusHeight)) {
        Image(art, contentDescription = null, modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
            palette.background.copy(alpha = if (palette.light) .16f else .34f), Color.Transparent))))
        Row(Modifier.fillMaxWidth().align(Alignment.TopStart).padding(start = 14.dp,
            top = statusHeight + 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("settingsBack"),
                shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, palette.accent),
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    containerColor = palette.background.copy(alpha = .75f),
                    contentColor = if (palette.light) palette.text else Color.White)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Вернуться в меню")
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text("Настройки", color = if (palette.light) palette.text else Color.White,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
                    fontSize = 24.sp, maxLines = 1)
                Text("Настрой игру под себя", color = if (palette.light) palette.text else Color(0xFFADB9E3),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SettingsPanel(title: String, subtitle: String, icon: ImageVector, accent: Color,
    content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalGamePalette.current
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().background(
        Brush.linearGradient(listOf(lerp(palette.panel, accent, if (palette.light) .045f else .09f),
            palette.panel)), shape).border(1.dp, accent, shape)
        .padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            Column {
                Text(title, color = accent, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2)
                Text(subtitle, color = palette.muted, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(6.dp))
        SettingsDivider(accent)
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
internal fun SettingsDivider(accent: Color) {
    HorizontalDivider(color = accent.copy(alpha = .22f), thickness = 1.dp)
}

@Composable
private fun DifficultySetting(model: GameViewModel, accent: Color) {
    val stacked = LocalDensity.current.fontScale >= 1.4f
    if (stacked) {
        SettingsLabel("Сложность", "Применяется к новой партии")
        Spacer(Modifier.height(8.dp))
        DifficultySegments(model, accent)
    } else {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(.43f)) { SettingsLabel("Сложность", "Применяется к\nновой партии") }
            Box(Modifier.weight(.57f)) { DifficultySegments(model, accent) }
        }
    }
}

@Composable
private fun DifficultySegments(model: GameViewModel, accent: Color) {
    val palette = LocalGamePalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Difficulty.entries.forEachIndexed { index, difficulty ->
            val selected = model.difficulty == difficulty
            Surface(onClick = { model.chooseDifficulty(difficulty) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("difficulty_${difficulty.id}"),
                shape = RoundedCornerShape(topStart = if (index == 0) 10.dp else 0.dp,
                    bottomStart = if (index == 0) 10.dp else 0.dp,
                    topEnd = if (index == Difficulty.entries.lastIndex) 10.dp else 0.dp,
                    bottomEnd = if (index == Difficulty.entries.lastIndex) 10.dp else 0.dp),
                border = BorderStroke(1.dp, accent),
                color = if (selected) accent else palette.background.copy(alpha = .7f),
                contentColor = if (selected) palette.background else palette.text) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 2.dp)) {
                    Text(difficulty.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, tag: String, checked: Boolean,
    onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("${tag}Row")
        .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { SettingsLabel(title, subtitle) }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

@Composable
internal fun SettingsLabel(title: String, subtitle: String) {
    val palette = LocalGamePalette.current
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = palette.text, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(),
            fontWeight = FontWeight.Medium)
        Text(subtitle, color = palette.muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
