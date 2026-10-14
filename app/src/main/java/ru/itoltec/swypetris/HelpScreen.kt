package ru.itoltec.swypetris

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Quick visual controls and essential rules for starting a game. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HelpScreen() {
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
        item { MenuPageTitle { Text("Как играть", style = MaterialTheme.typography.headlineLarge) } }
        item { Text("Управление", style = MaterialTheme.typography.titleLarge) }
        controls.forEach { (symbol, label, piece) ->
            item {
                HelpControl(symbol, label, piece)
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
                "«Запас» слева сверху хранит одну фигуру. Удержите палец и проведите " +
                    "вверх, чтобы отложить текущую фигуру " +
                    "или поменять её с сохранённой. Обмен доступен один раз для каждой падающей фигуры."
            )
        }
        item {
            HelpSection(
                "Следующая фигура",
                "Следующая фигура показана спокойным контуром под подписью «Далее» прямо в месте появления. " +
                    "Когда текущая фигура зафиксируется, следующая войдёт в поле из этой позиции."
            )
        }
        item(key = "fruits") {
            HelpFruits()
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
    val skin = LocalMenuSkin.current
    Surface(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().menuSurfaceArt("card"),
        shape = if (skin == null) RoundedCornerShape(16.dp) else RectangleShape,
        color = if (skin == null) menuUiPalette().accent.copy(alpha = .08f) else Color.Transparent,
        border = if (skin == null) BorderStroke(1.dp, menuUiPalette().accent.copy(alpha = .4f)) else null
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HelpControl(symbol: String, label: String, piece: Tetromino) {
    val skin = LocalMenuSkin.current
    val accent = menuUiPalette().piece(piece)
    val indicatorColors = paletteButtonColors(accent, ActionStyle.SECONDARY)
    Surface(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().menuSurfaceArt("card"),
        shape = if (skin == null) RoundedCornerShape(16.dp) else RectangleShape,
        color = if (skin == null) menuUiPalette().accent.copy(alpha = .08f) else Color.Transparent,
        border = if (skin == null) BorderStroke(1.dp, menuUiPalette().accent.copy(alpha = .4f)) else null
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                Modifier.size(56.dp).menuSurfaceArt("chip"),
                shape = if (skin == null) RoundedCornerShape(12.dp) else RectangleShape,
                color = if (skin == null) indicatorColors.containerColor else Color.Transparent,
                contentColor = indicatorColors.contentColor,
                border = if (skin == null) paletteButtonBorder(accent, ActionStyle.SECONDARY) else null
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(symbol, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HelpFruits() {
    HelpSection(
        "Фрукты",
        "Каждые ${GameRules.FRUIT_STEP} очков вы получаете следующий фрукт. " +
            "Коллекция сохраняется между кругами; " +
            "число рядом с фруктом показывает, сколько таких фруктов собрано. " +
                "Восемь фруктов открывают следующий круг: " +
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
