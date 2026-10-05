package ru.itoltec.swypetris

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** The four panels follow the approved settings reference while keeping real settings. */
@Composable
internal fun SettingsScreen(model: GameViewModel, onCheckUpdates: () -> Unit = {},
    automaticUpdates: Boolean = false, onAutomaticUpdatesChange: (Boolean) -> Unit = {}) {
    val palette = LocalGamePalette.current
    var confirmReset by remember { mutableStateOf(false) }
    val gameplay = palette.piece(Tetromino.I)
    val assistance = palette.piece(Tetromino.T)
    val audio = palette.piece(Tetromino.I)
    val appearance = lerp(palette.piece(Tetromino.T), palette.piece(Tetromino.Z), .35f)
    Column(Modifier.fillMaxSize().background(palette.background).navigationBarsPadding()
        .verticalScroll(rememberScrollState())) {
        ScreenArtHeader("Настройки", "Настрой игру под себя", model::menu)
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsPanel("ИГРОВОЙ ПРОЦЕСС", "Основные параметры игры", Icons.Outlined.SportsEsports, gameplay) {
                SettingsDivider(gameplay)
                StartingLevelSetting(model)
            }
            SettingsPanel("ПОМОЩЬ В ИГРЕ", "Подсказки и дополнительная информация", Icons.Outlined.Lightbulb, assistance) {
                SettingsToggle("Тень падения", "Показывать место приземления", "hints",
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
            SettingsPanel("О ПРИЛОЖЕНИИ", "Версия и обновления", Icons.Outlined.Lightbulb, assistance) {
                SettingsToggle("Автоматические обновления", "Загрузка и установка из меню после согласия",
                    "automaticUpdates", automaticUpdates, onAutomaticUpdatesChange)
                SettingsDivider(assistance)
                OutlinedButton(onClick = onCheckUpdates, modifier = Modifier.fillMaxWidth()
                    .testTag("checkUpdates")) { Text("Проверить обновления") }
            }
            SettingsPanel("ДАННЫЕ", "История игр и рекорды", Icons.Outlined.DeleteOutline,
                MaterialTheme.colorScheme.error) {
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()
                    .testTag("resetStatistics")) {
                    Text("Сбросить статистику", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Сбросить статистику?") },
        text = { Text("Будут удалены вся история результатов и рекорды, включая старые. Настройки и текущая партия сохранятся.") },
        confirmButton = {
            TextButton(onClick = { model.resetStatistics(); confirmReset = false },
                modifier = Modifier.testTag("confirmResetStatistics")) {
                Text("Сбросить статистику", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmReset = false }, modifier = Modifier.testTag("cancelResetStatistics")) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun SettingsPanel(title: String, subtitle: String, icon: ImageVector, accent: Color,
    content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalGamePalette.current
    ThemedCard(accent, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
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
}

@Composable
internal fun SettingsDivider(accent: Color) {
    HorizontalDivider(color = accent.copy(alpha = .22f), thickness = 1.dp)
}

@Composable
private fun StartingLevelSetting(model: GameViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SettingsLabel("Начальный уровень: ${model.startingLevel}", "Для новой партии. Скорость Marathon, максимум 20G.")
        Slider(value = model.startingLevel.toFloat(),
            onValueChange = { model.chooseStartingLevel(it.roundToInt()) },
            valueRange = GameRules.MIN_STARTING_LEVEL.toFloat()..GameRules.MAX_STARTING_LEVEL.toFloat(),
            steps = GameRules.MAX_STARTING_LEVEL - GameRules.MIN_STARTING_LEVEL - 1,
            modifier = Modifier.fillMaxWidth().testTag("startingLevel"))
        Text("Следующий уровень после ${model.startingLevel * GameRules.LINES_PER_LEVEL} линий",
            color = LocalGamePalette.current.muted, fontSize = 11.sp)
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
