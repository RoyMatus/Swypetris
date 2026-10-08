package ru.itoltec.swypetris

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_RECORD_NAME_LENGTH = 40
private const val CONFETTI_DURATION_MILLIS = 2600
private const val CONFETTI_PARTICLES = 24
private const val CONFETTI_OPACITY = .5f
private const val SECONDS_PER_MINUTE = 60
private const val ROUND_COLLECTION_RULES_VERSION = 4


private val Ice: Color @Composable get() = LocalGamePalette.current.accent
private val Lavender: Color @Composable get() = LocalGamePalette.current.secondary
private val Gold: Color @Composable get() = LocalGamePalette.current.gold
private val Muted: Color @Composable get() = LocalGamePalette.current.muted

/** Results and history use menu-style cards that fit the width and scroll only vertically. */
@Composable
fun ResultsScreen(model: GameViewModel) {
    val latest = model.latestResult
    val records = model.recordResults
    val palette = LocalGamePalette.current
    Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("resultsPage"),
            contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ScreenArtHeader(if (model.screen == GameScreen.GAME_OVER) "Игра окончена" else "Результаты",
                if (model.screen == GameScreen.GAME_OVER) "Итоги партии" else "История рекордов") }
            if (model.screen == GameScreen.GAME_OVER && latest != null) item {
                AccentPanel(Ice, Modifier.padding(horizontal = 12.dp)) {
                    Text("РЕЗУЛЬТАТ ПАРТИИ", color = Ice, style = MaterialTheme.typography.labelLarge)
                    Text("${latest.score}", Modifier.fillMaxWidth(), fontSize = 46.sp,
                        fontWeight = FontWeight.Black, color = palette.text, textAlign = TextAlign.Center)
                    Text("очков", Modifier.fillMaxWidth(), color = Muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Metric("Строки", "${latest.lines}", Modifier.weight(1f))
                        Metric("Уровень", "${latest.level}", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Metric("Время", formatDuration(latest.durationMillis), Modifier.weight(1f))
                        Metric("Рекорд", "${model.record}", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { FruitCollection(latest) }
                }
            }
            item {
                AccentPanel(Lavender, Modifier.padding(horizontal = 12.dp)) {
                    Text("ИСТОРИЯ РЕКОРДОВ", color = Lavender, style = MaterialTheme.typography.labelLarge)
                    SettingsDivider(Lavender)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Правила ${GameRules.VERSION}", color = palette.text)
                        Text("${model.record}", color = Ice, fontWeight = FontWeight.Bold)
                    }
                    if (model.legacyRecord > 0) Text("Прежние правила: ${model.legacyRecord}", color = Muted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (records.isEmpty()) item {
                AccentPanel(Lavender, Modifier.padding(horizontal = 12.dp)) {
                    Text("Здесь появятся ваши новые рекорды.", color = Muted)
                }
            }
            items(records, key = { it.id }) { result -> ResultCard(result) }
        }
    }
}

/** Separate record celebration that asks for a name before showing game results. */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun RecordScreen(model: GameViewModel) {
    var name by rememberSaveable(model.currentResultId) { mutableStateOf(model.playerName) }
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current
    val nameAutofill = remember(model.currentResultId) {
        AutofillNode(autofillTypes = listOf(AutofillType.PersonFullName),
            onFill = { name = it.take(MAX_RECORD_NAME_LENGTH) })
    }
    DisposableEffect(autofillTree, nameAutofill) {
        autofillTree += nameAutofill
        onDispose { autofillTree.children.remove(nameAutofill.id) }
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val result = model.results.firstOrNull { it.id == model.currentResultId }
    Box(Modifier.fillMaxSize().background(LocalGamePalette.current.background).imePadding(),
        contentAlignment = Alignment.TopCenter) {
        CelebrationBlocks(model.currentResultId)
        LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize().testTag("recordPage"),
            contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ScreenArtHeader("Новый рекорд", "Поздравляем с результатом!") }
            item {
                AccentPanel(Gold, Modifier.padding(horizontal = 12.dp)) {
                    Text("НОВЫЙ РЕКОРД", color = Gold, style = MaterialTheme.typography.labelLarge)
                    Text("${result?.score ?: model.record}", Modifier.fillMaxWidth(), fontSize = 48.sp,
                        fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Text("очков", Modifier.fillMaxWidth(), color = Muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    result?.let { Box(Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center) { FruitCollection(it) } }
                }
            }
            item {
                AccentPanel(Ice, Modifier.padding(horizontal = 12.dp)) {
                    Text("ИМЯ ИГРОКА", color = Ice, style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(value = name, onValueChange = { name = it.take(MAX_RECORD_NAME_LENGTH) },
                        label = { Text("Имя игрока") }, supportingText = { Text("Можно оставить пустым") },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().onGloballyPositioned {
                            nameAutofill.boundingBox = it.boundsInWindow()
                        }.onFocusChanged {
                            if (it.isFocused) autofill?.requestAutofillForNode(nameAutofill)
                            else autofill?.cancelAutofillForNode(nameAutofill)
                        }.testTag("recordName"))
                    RecordNameActions(onSave = {
                        focus.clearFocus(); keyboard?.hide(); model.statistics.saveRecordName(name)
                    }, onSkip = {
                        focus.clearFocus(); keyboard?.hide(); model.statistics.saveRecordName("")
                    })
                }
            }
        }
    }
}

/** A Results card sharing the Settings border and gradient. */
@Composable
private fun AccentPanel(accent: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ThemedCard(accent, modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

/** Labeled metric that wraps a long value within its half of the row. */
@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** History card wraps its metrics and awards within the available width. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ResultCard(result: GameResult) {
    AccentPanel(Lavender, Modifier.padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(result.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("${result.score}", Modifier.weight(1f), color = Ice, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End)
        }
        Text(SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(result.dateMillis)),
            color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Строки: ${result.lines}", color = Muted)
            Text("Уровень: ${result.level}", color = Muted)
            Text(formatDuration(result.durationMillis), color = Muted)
        }
        Text(result.difficulty?.let { "${it.title} · Правила ${result.rulesVersion}" } ?:
            if (result.rulesVersion == GameRules.VERSION) "Правила ${result.rulesVersion}"
                else "Прежние правила ${result.rulesVersion}", color =
            Muted)
        Box(Modifier.fillMaxWidth()) { FruitCollection(result) }
    }
}

/** A brief colored-block celebration fades without blocking name entry. */
@Composable
private fun CelebrationBlocks(key: String?) {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(CONFETTI_DURATION_MILLIS)) }
    val colors = listOf(Ice, Lavender, Gold)
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        if (p < 1f) repeat(CONFETTI_PARTICLES) { index ->
            val x = size.width * ((index * 37 % 101) / 100f)
            val y = size.height * ((index % 6) / 12f + p * .45f)
            val side = (5 + index % 5).dp.toPx()
            drawRect(colors[index % colors.size].copy(alpha = (1 - p) * CONFETTI_OPACITY),
                Offset(x, y), Size(side, side))
        }
    }
}
/** Formats elapsed duration independently of the time zone. */
internal fun formatDuration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

/** Shows the current round's fruit collection without multipliers, preserving legacy results. */
@Composable
private fun FruitCollection(result: GameResult) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (result.rulesVersion >= ROUND_COLLECTION_RULES_VERSION) {
            Text("Пройдено кругов: ${result.completedRounds}", color = Muted)
            val current = (fruitCount(result.score) - result.completedRounds * Fruit.entries.size)
                .coerceIn(0, Fruit.entries.size)
            RoundFruitCollection(List(Fruit.entries.size) { index -> if (index < current) 1 else 0 })
        } else {
            val current = fruitCount(result.score).coerceAtMost(Fruit.entries.size)
            RoundFruitCollection(List(Fruit.entries.size) { index -> if (index < current) 1 else 0 })
        }
    }
}

@Composable
private fun RecordNameActions(onSave: () -> Unit, onSkip: () -> Unit) {
    val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f
    if (stacked) {
        AppActionButton("Сохранить", ActionStyle.PRIMARY,
            Modifier.fillMaxWidth().testTag("saveRecord"),
                LocalGamePalette.current.piece(Tetromino.S)) {
            onSave()
        }
        AppActionButton("Пропустить", ActionStyle.SECONDARY,
            Modifier.fillMaxWidth().testTag("skipRecord"),
                LocalGamePalette.current.piece(Tetromino.Z)) {
            onSkip()
        }
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { AppActionButton("Сохранить", ActionStyle.PRIMARY,
            Modifier.fillMaxWidth().testTag("saveRecord"),
                LocalGamePalette.current.piece(Tetromino.S)) {
            onSave()
        } }
        Box(Modifier.weight(1f)) { AppActionButton("Пропустить", ActionStyle.SECONDARY,
            Modifier.fillMaxWidth().testTag("skipRecord"),
                LocalGamePalette.current.piece(Tetromino.Z)) {
            onSkip()
        } }
    }
}
