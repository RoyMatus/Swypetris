package ru.itoltec.swypetris

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ice: Color @Composable get() = LocalGamePalette.current.accent
private val Lavender: Color @Composable get() = LocalGamePalette.current.secondary
private val Gold: Color @Composable get() = LocalGamePalette.current.gold
private val Muted: Color @Composable get() = LocalGamePalette.current.muted

/** Итоги и история в стиле меню: карточки укладываются в ширину, прокрутка только вертикальная. */
@Composable
fun ResultsScreen(model: GameViewModel) {
    val latest = model.results.firstOrNull { it.id == model.currentResultId }
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("resultsPage"),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            item { GameTitle() }
            item {
                Text(if (model.screen == GameScreen.GAME_OVER) "Игра окончена" else "Ваши результаты",
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center)
            }
            if (model.screen == GameScreen.GAME_OVER && latest != null) item {
                AccentPanel(Ice) {
                    Text("РЕЗУЛЬТАТ ПАРТИИ", color = Ice, style = MaterialTheme.typography.labelLarge)
                    Text("${latest.score}", fontSize = 52.sp, fontWeight = FontWeight.Black, color = LocalGamePalette.current.text)
                    Text("очков", color = Muted)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Metric("Строки", "${latest.lines}", Modifier.weight(1f))
                        Metric("Уровень", "${latest.level}", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Metric("Время", formatDuration(latest.durationMillis), Modifier.weight(1f))
                        Metric("Рекорд", "${model.record}", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    FruitCollection(latest)
                }
            }
            item {
                Column(Modifier.fillMaxWidth()) {
                    Text("История партий", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Лучший результат: ${model.record}", color = Ice)
                    if (model.legacyRecord > 0) Text("Прежние правила: ${model.legacyRecord}", color = Muted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (model.results.isEmpty()) item {
                AccentPanel(Lavender) { Text("Сыграйте первую партию — её результат появится здесь.", color = Muted) }
            }
            items(model.results, key = { it.id }) { result -> ResultCard(result) }
        }
    }
}

/** Отдельное поздравление с рекордом: имя вводится до перехода к итогам партии. */
@Composable
fun RecordScreen(model: GameViewModel) {
    var name by rememberSaveable(model.currentResultId) { mutableStateOf(model.playerName) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val result = model.results.firstOrNull { it.id == model.currentResultId }
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        CelebrationBlocks(model.currentResultId)
        LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize().testTag("recordPage"),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            item { GameTitle() }
            item {
                Text("Поздравляем!", style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = Gold)
                Text("Вы установили новый рекорд", color = Muted, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            }
            item {
                AccentPanel(Gold) {
                    Text("НОВЫЙ РЕКОРД", color = Gold, style = MaterialTheme.typography.labelLarge)
                    Text("${result?.score ?: model.record}", fontSize = 56.sp, fontWeight = FontWeight.Black)
                    Text("очков", color = Muted)
                    Spacer(Modifier.height(12.dp))
                    result?.let { FruitCollection(it) }
                }
            }
            item {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) },
                    label = { Text("Имя игрока") }, supportingText = { Text("Можно оставить пустым") },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("recordName"))
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { MenuTile("Сохранить", Ice, "saveRecord") {
                        focus.clearFocus(); keyboard?.hide(); model.saveRecordName(name)
                    } }
                    Box(Modifier.weight(1f)) { MenuTile("Пропустить", Lavender, "skipRecord") {
                        focus.clearFocus(); keyboard?.hide(); model.saveRecordName("")
                    } }
                }
            }
        }
    }
}

/** Полупрозрачная панель повторяет цветные контуры квадратных кнопок стартового экрана. */
@Composable
private fun AccentPanel(accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = .08f), contentColor = LocalGamePalette.current.text,
        border = BorderStroke(1.dp, accent.copy(alpha = .4f))) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/** Подписанный показатель переносит длинное значение внутри своей половины строки. */
@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Карточка истории переносит все показатели и призы в пределах экрана. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ResultCard(result: GameResult) {
    AccentPanel(Lavender) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(result.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("${result.score}", Modifier.weight(1f), color = Ice, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        }
        Text(SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(result.dateMillis)),
            color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Строки: ${result.lines}", color = Muted)
            Text("Уровень: ${result.level}", color = Muted)
            Text(formatDuration(result.durationMillis), color = Muted)
        }
        Text(if (result.rulesVersion < GameRules.VERSION) "Прежние правила" else "Правила ${result.rulesVersion}", color = Muted)
        Box(Modifier.fillMaxWidth()) { FruitCollection(result) }
    }
}

/** Короткая анимация цветных блоков: плавно исчезает и не мешает вводу имени. */
@Composable
private fun CelebrationBlocks(key: String?) {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(2600)) }
    val colors = listOf(Ice, Lavender, Gold)
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        if (p < 1f) repeat(24) { index ->
            val x = size.width * ((index * 37 % 101) / 100f)
            val y = size.height * ((index % 6) / 12f + p * .45f)
            val side = (5 + index % 5).dp.toPx()
            drawRect(colors[index % 3].copy(alpha = (1 - p) * .5f),
                Offset(x, y), Size(side, side))
        }
    }
}
/** Форматирует длительность без зависимости от часового пояса. */
internal fun formatDuration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** Показывает коллекцию текущего круга без множителей, сохраняя смысл прежних результатов. */
@Composable
private fun FruitCollection(result: GameResult) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (result.rulesVersion >= 4) {
            Text("Пройдено кругов: ${result.completedRounds}", color = Muted)
            RoundFruitCollection((fruitCount(result.score) - result.completedRounds * 8).coerceIn(0, 8))
        } else {
            RoundFruitCollection(fruitCount(result.score).coerceAtMost(8))
        }
    }
}