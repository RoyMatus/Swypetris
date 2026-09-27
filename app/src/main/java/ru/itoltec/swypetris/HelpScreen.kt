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
import androidx.compose.ui.unit.dp

/** Essential controls fit a quick read; larger fonts can still scroll vertically. */
@Composable
fun HelpScreen(model: GameViewModel) {
    val controls = listOf(
        "← →" to "Двигайте фигуру",
        "↑" to "Свайп вверх — один поворот",
        "↓" to "Длинный жест вниз — бросок",
        "●" to "Короткий тап — клетка вниз"
    )
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("helpPage"),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("Как играть", style = MaterialTheme.typography.headlineLarge) }
        controls.forEach { (symbol, label) ->
            item {
                Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    color = LocalGamePalette.current.accent.copy(alpha = .08f),
                    border = BorderStroke(1.dp, LocalGamePalette.current.accent.copy(alpha = .4f))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(symbol, color = LocalGamePalette.current.accent, style = MaterialTheme.typography.headlineMedium)
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        item { Text("Для повторного поворота слегка опустите палец и снова проведите вверх либо начните новое касание.") }
        item { Text("Палец можно не отрывать между фигурами. Удержание не ускоряет падение.") }
        item { Text("Заполняйте строки без пробелов. Не перекрывайте появление новых фигур.") }
        item { Text("Каждые ${GameRules.FRUIT_STEP} очков — фрукт. Восемь фруктов — следующий круг.") }
        item { Text("Назад — пауза и меню. «Продолжить» вернёт сохранённую партию.") }
    }
}
