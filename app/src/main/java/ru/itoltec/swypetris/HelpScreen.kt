package ru.itoltec.swypetris

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Вертикальная справка с теми же правилами и оформлением, что используются в игре. */
@Composable
fun HelpScreen(model: GameViewModel) {
    val sections = listOf(
        "Цель игры" to "Заполняйте горизонтальные строки без пробелов. Они исчезают, освобождая место. Партия заканчивается, когда следующей фигуре негде появиться.",
        "Повороты и движение" to "Тап или свайп вверх поворачивает фигуру по часовой стрелке. Диагональ ↖ поворачивает против часовой, ↗ — по часовой. Двигайте палец влево или вправо: фигура следует за ним. Удержание после бокового движения повторяет сдвиги.",
        "Мягкий спуск" to "Удерживайте палец неподвижно до начала ускоренного спуска. Можно также провести вниз и удерживать. Не отрывая палец, двигайте его вбок: спуск и сдвиг работают одновременно. Отпустите палец, чтобы вернуть обычную скорость. Для следующей фигуры нужно новое касание.",
        "Мгновенный бросок" to "Проведите вниз и сразу отпустите палец: фигура опустится до препятствия и зафиксируется. Во время удержания или движения вбок резко проведите вниз — бросок сработает сразу, без отрыва пальца. Для следующей фигуры нужно новое касание.",
        "Очки" to "Каждая реально пройденная клетка вниз приносит 1 очко при обычном падении, мягком спуске и броске. За 1/2/3/4 строки: ${(1..4).joinToString("/") { GameRules.lineScore(it).toString() }} очков без множителя уровня. Сдвиги, повороты и попытки пройти сквозь препятствие очков не дают.",
        "Уровни и скорость" to "Для перехода с уровня L нужно ещё 1000 + 250 × (L − 1) очков. Пороги: ${(1..7).joinToString("; ") { "$it — ${GameRules.threshold(it)}" }}; 10 — ${GameRules.threshold(10)}. Интервал падения: max(100, 800 × 0,85^(L − 1)) мс. Чем выше уровень, тем быстрее фигуры.",
        "Панель счёта" to "Слева показаны уровень и очки. На последних 20% пути к следующему уровню золотые цифры показывают, сколько осталось: при 2100 очках — «${GameRules.level(2100)} | ${GameRules.displayScore(2100)}». При ${GameRules.threshold(3)} — «3 | ${GameRules.displayScore(2250)}». Это остаток до уровня, а не до рекорда.",
        "Фрукты" to "За каждые ${GameRules.FRUIT_STEP} очков вы получаете фрукт: вишню, банан и следующие награды коллекции. Все восемь фруктов приносят победу: первая на 80 000, следующая на 160 000 очков. На поле показаны только заработанные фрукты: они появляются сверху вниз у правого края. Незаработанные значки не рисуются. На странице поздравления нажмите «Следующий круг»: поле и фрукты очистятся, счёт и скорость сохранятся. Следующая вишня выдаётся на 90 000 очков независимо от превышения победного порога.",
        "Рекорды и настройки" to "Завершённые партии сохраняются в истории. Новый рекорд можно подписать именем. Результаты прежних правил остаются с отдельной пометкой. Звуки, вибрация и подсказки места падения и следующей фигуры включаются независимо. В списке музыки можно выбрать тишину, одну повторяющуюся песню или все восемь без повторов внутри круга: сначала «Коробейники», затем остальные семь в случайном порядке. Музыку можно слушать в настройках; меню и фон ставят её на паузу. Выключение музыки отключает и фанфары.",
        "Пауза и возврат" to "Системный «Назад» открывает меню и сохраняет текущую партию в памяти. Справка и настройки не возобновляют её автоматически: нажмите «Продолжить» в меню. После полного закрытия приложения сохраняются история и настройки, но не поле."
    )
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("helpPage"),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        item { GameTitle() }
        item { Text("Как играть", style = MaterialTheme.typography.headlineLarge) }
        sections.forEachIndexed { index, (title, body) ->
            item {
                val accent = listOf(LocalGamePalette.current.accent, LocalGamePalette.current.secondary, LocalGamePalette.current.gold)[index % 3]
                Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    color = accent.copy(alpha = 0.08f), border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, color = accent, style = MaterialTheme.typography.titleLarge)
                        Text(body, style = MaterialTheme.typography.bodyLarge)
                        if (title == "Фрукты") FruitGuide()
                    }
                }
            }
        }
        item { Button(onClick = model::menu, modifier = Modifier.testTag("helpBack")) { Text("В меню") } }
    }
}

/** Показывает все восемь наград с подписями независимо от заработанных игроком фруктов. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FruitGuide() {
    FlowRow(Modifier.fillMaxWidth().testTag("fruitGuide"),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Fruit.entries.forEach { fruit ->
            Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FruitIcon(fruit, Modifier.size(40.dp))
                Text(fruit.title, style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
