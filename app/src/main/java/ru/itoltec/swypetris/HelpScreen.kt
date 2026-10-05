package ru.itoltec.swypetris

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Essential controls fit a quick read; larger fonts can still scroll vertically. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HelpScreen(model: GameViewModel) {
    val controls = listOf(
        Triple("← →", "Свайп влево/вправо — двигать", Tetromino.I),
        Triple("↑ ↗", "Свайп вверх/вправо — поворот по часовой", Tetromino.T),
        Triple("↖", "Свайп вверх-влево — поворот против часовой", Tetromino.Z),
        Triple("●", "Короткий тап — на 1 клетку вниз", Tetromino.S),
        Triple("↓", "Свайп вниз — жёсткий сброс", Tetromino.J),
        Triple("● ↑", "Удержать и свайпнуть вверх — Hold", Tetromino.L)
    )
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding().testTag("helpPage"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Text("Как играть", style = MaterialTheme.typography.headlineLarge) }
        item { Text("Управление", style = MaterialTheme.typography.titleLarge) }
        controls.forEach { (symbol, label, piece) ->
            item {
                val accent = LocalGamePalette.current.piece(piece)
                val indicatorColors = paletteButtonColors(accent, ActionStyle.SECONDARY)
                Surface(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = LocalGamePalette.current.accent.copy(alpha = .08f),
                    border = BorderStroke(1.dp, LocalGamePalette.current.accent.copy(alpha = .4f))
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            Modifier.size(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = indicatorColors.containerColor,
                            contentColor = indicatorColors.contentColor,
                            border = paletteButtonBorder(accent, ActionStyle.SECONDARY)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(symbol, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            HelpSection(
                "Главное",
                "Собирайте полные горизонтальные строки, чтобы получать очки. Не заполняйте верхнюю область появления фигур. Полупрозрачная фигура сверху показывает, какая фигура появится следующей."
            )
        }
        item {
            HelpSection(
                "Hold",
                "Удержите палец примерно 300 мс и проведите вверх, чтобы отложить или вернуть фигуру. Hold находится справа сверху и доступен один раз для каждой текущей фигуры."
            )
        }
        item(key = "fruits") {
            HelpSection(
                "Фрукты",
                "Каждые ${GameRules.FRUIT_STEP} очков вы получаете следующий фрукт. Собранные фрукты сохраняются между раундами; повторные показываются как 2 ×, 3 × и далее. Соберите восемь фруктов и продолжайте в следующий раунд."
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Fruit.entries.forEach { fruit -> FruitIcon(fruit, Modifier.size(32.dp)) }
                }
            }
        }
        item {
            HelpSection(
                "Подсказки",
                "Тень падения можно включить в настройках. Кнопка «Назад» ставит партию на паузу и возвращает в меню; «Продолжить» открывает сохранённую партию."
            )
        }
    }
}

/** A reusable help card with explanatory text and optional content such as the fruit gallery. */
@Composable
private fun HelpSection(title: String, body: String, content: @Composable () -> Unit = {}) {
    Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = LocalGamePalette.current.accent.copy(alpha = .08f),
        border = BorderStroke(1.dp, LocalGamePalette.current.accent.copy(alpha = .4f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}
