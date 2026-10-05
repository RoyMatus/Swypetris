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
        Triple("← →", "Двигайте фигуру", Tetromino.I),
        Triple("↑", "Свайп вверх — один поворот", Tetromino.T),
        Triple("↓", "Длинный жест вниз — бросок", Tetromino.J),
        Triple("●", "Короткий тап — клетка вниз", Tetromino.S)
    )
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("helpPage"),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("Как играть", style = MaterialTheme.typography.headlineLarge) }
        item { Text("Управление", style = MaterialTheme.typography.titleLarge) }
        controls.forEach { (symbol, label, piece) ->
            item {
                val accent = LocalGamePalette.current.piece(piece)
                val indicatorColors = paletteButtonColors(accent, ActionStyle.SECONDARY)
                Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    color = LocalGamePalette.current.accent.copy(alpha = .08f),
                    border = BorderStroke(1.dp, LocalGamePalette.current.accent.copy(alpha = .4f))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(Modifier.size(56.dp), shape = RoundedCornerShape(12.dp),
                            color = indicatorColors.containerColor, contentColor = indicatorColors.contentColor,
                            border = paletteButtonBorder(accent, ActionStyle.SECONDARY)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(symbol, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        item { HelpSection("Повторные жесты", "Для повторного поворота слегка опустите палец и снова проведите вверх либо начните новое касание. После броска новой фигуре нужен новый жест вниз. Палец можно не отрывать; удержание не ускоряет падение.") }
        item { HelpSection("Цель игры", "Заполняйте горизонтальные строки без пробелов: они исчезают и дают очки. Каждая пройденная вниз клетка тоже приносит очко. Над видимым полем есть скрытая область для появления и вращения фигур. Партия заканчивается, если новой фигуре негде появиться или фигура целиком зафиксировалась выше видимого поля.") }
        item {
            HelpSection("Фрукты", "Каждые ${GameRules.FRUIT_STEP} очков вы получаете следующий фрукт коллекции. Отдельных бонусов у видов фруктов нет. Восемь фруктов открывают следующий круг: поле очищается, а счёт и скорость сохраняются.") {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Fruit.entries.forEach { fruit -> FruitIcon(fruit, Modifier.size(32.dp)) }
                }
            }
        }
        item { HelpSection("Ещё важно", "С ростом счёта фигуры падают быстрее. Назад ставит игру на паузу и открывает меню; «Продолжить» возвращает сохранённую партию.") }
        item { HelpSection("Настройки и рекорды", "Подсказки, звук, музыку и вибрацию можно настроить отдельно. Результаты завершённых партий сохраняются, а новый рекорд можно подписать именем.") }
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
