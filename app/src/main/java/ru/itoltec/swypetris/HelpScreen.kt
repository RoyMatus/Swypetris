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

/** Quick visual controls and the essential rules needed to start playing. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HelpScreen(model: GameViewModel) {
    val controls = listOf(
        Triple("← →", "Двигать фигуру", Tetromino.I),
        Triple("↖ ↗", "Поворачивать", Tetromino.T),
        Triple("●", "Тап — на клетку вниз", Tetromino.S),
        Triple("↓", "Свайп вниз — бросок", Tetromino.J),
        Triple("● ↑", "Удержать и вверх — Hold", Tetromino.L)
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
                "Цель",
                "Заполняйте горизонтальные строки без пробелов: они исчезают и дают очки. " +
                    "Не допускайте, чтобы новым фигурам не осталось места сверху."
            )
        }
        item {
            HelpSection(
                "Hold",
                "Hold справа сверху хранит одну фигуру. Удержите палец и проведите вверх, чтобы отложить текущую фигуру " +
                    "или поменять её с сохранённой. Обмен доступен один раз для каждой падающей фигуры."
            )
        }
        item {
            HelpSection(
                "Следующая фигура",
                "Следующая фигура полупрозрачно показана сверху прямо в месте появления. " +
                    "Когда текущая фигура зафиксируется, следующая войдёт в поле из этой позиции."
            )
        }
        item(key = "fruits") {
            HelpSection(
                "Фрукты",
                "Каждые ${GameRules.FRUIT_STEP} очков вы получаете следующий фрукт. Коллекция сохраняется между кругами; " +
                    "число рядом с фруктом показывает, сколько таких фруктов собрано. Восемь фруктов открывают следующий круг: " +
                    "поле очищается, а счёт и скорость сохраняются."
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
                "Тень падения в настройках показывает место приземления. Кнопка Назад ставит игру на паузу; " +
                    "«Продолжить» возвращает сохранённую партию."
            )
        }
    }
}

/** A reusable help card with explanatory text and optional content such as the fruit gallery. */
@Composable
private fun HelpSection(title: String, body: String, content: @Composable () -> Unit = {}) {
    Surface(
        Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = LocalGamePalette.current.accent.copy(alpha = .08f),
        border = BorderStroke(1.dp, LocalGamePalette.current.accent.copy(alpha = .4f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}
