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
        Triple("↑ ↗", "Вверх или вверх-вправо — поворот по часовой", Tetromino.T),
        Triple("↖", "Вверх-влево — поворот против часовой", Tetromino.Z),
        Triple("● ↑", "Подержите палец 300 мс и проведите вверх — запас", Tetromino.L),
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
        item { HelpSection("Запас (Hold)", "Держите один палец почти неподвижно около 300 мс, затем проведите вверх. Короткая вибрация сообщает о готовности, если она включена. При пустом запасе текущая фигура откладывается и появляется следующая. Тем же жестом верните фигуру из запаса: она поменяется с текущей и появится в исходном положении. Обмен доступен один раз до фиксации фигуры. После обмена отпустите палец. Если передумали — отпустите его без свайпа: фигура не сдвинется. Небольшой полупрозрачный рисунок слева показывает запас; более тусклый означает, что обмен пока недоступен.") }
        item { HelpSection("Цель игры", "Заполняйте горизонтальные строки без пробелов: они исчезают и дают очки. Автоматическое падение очков не даёт. Короткий тап приносит 1 очко за клетку, бросок — 2 очка за клетку, без множителя уровня. Над видимым полем есть скрытая область для появления и вращения фигур. На опоре есть 0,5 секунды для коррекции; успешный сдвиг или поворот сбрасывает задержку не более 15 раз. Бросок фиксирует сразу. Партия заканчивается, если новой фигуре негде появиться или фигура целиком зафиксировалась выше видимого поля.") }
        item { HelpSection("Очки", "За 1 / 2 / 3 / 4 линии: 100 / 300 / 500 / 800 × уровень. T-Spin — фиксация T после поворота, когда заняты три угла вокруг центра. Mini: 100 / 200 / 400 за 0 / 1 / 2 линии; полный T-Spin: 400 / 800 / 1200 / 1600 за 0 / 1 / 2 / 3 линии. Эти очки умножаются на уровень до удаления линий, даже если очистка открывает следующий уровень.") }
        item { HelpSection("Цепочки и пустое поле", "Back-to-Back: повторная очистка четырёх линий или T-Spin с линиями даёт ×1,5 к основной награде. Обычная очистка 1–3 линий обрывает цепочку; фиксация без очистки — нет. Комбо за последовательные очистки: 50 × номер комбо × уровень, первая очистка — номер 0. Фиксация без очистки обнуляет комбо. Perfect Clear — поле полностью пусто после удаления линий: дополнительно 800 / 1200 / 1800 / 2000 × уровень за 1 / 2 / 3 / 4 линии, либо 3200 × уровень за Back-to-Back с четырьмя линиями.") }
        item(key = "fruits") {
            HelpSection("Фрукты", "Каждые ${GameRules.FRUIT_STEP} очков вы получаете следующий фрукт коллекции. Отдельных бонусов у видов фруктов нет. Восемь фруктов открывают следующий круг: поле очищается, а счёт и скорость сохраняются.") {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Fruit.entries.forEach { fruit -> FruitIcon(fruit, Modifier.size(32.dp)) }
                }
            }
        }
        item { HelpSection("Ещё важно", "Начальный уровень 1–15 выбирается в настройках для новой партии отдельно от сложности; по умолчанию — 1. Первый переход требует 10 × начальный уровень линий: старт с 1 — уровень 2 после 10 линий; старт с 5 — уровень 6 после 50; старт с 15 — уровень 16 после 150. Затем переход каждые 10 линий, без ограничения на 15. Продолжение сохраняет уровень выбранной партии. Очки за спуск и фрукты не повышают уровень. С ростом уровня фигуры падают быстрее. Назад ставит игру на паузу и открывает меню; «Продолжить» возвращает сохранённую партию.") }
        item { HelpSection("Настройки и рекорды", "Следующая фигура всегда видна над полем в исходном положении. Настройка «Тень падения» включает только контур места приземления. Звук, музыку и вибрацию можно настроить отдельно. Результаты завершённых партий сохраняются, а новый рекорд можно подписать именем.") }
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
