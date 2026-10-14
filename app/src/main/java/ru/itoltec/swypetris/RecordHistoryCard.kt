package ru.itoltec.swypetris

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private const val HISTORY_SIDE_GAP_DP = 8f
private const val HISTORY_METRIC_GAP_DP = 8f
private const val SCORE_DIGITS_PER_GROUP = 3

@Composable
internal fun RecordHistoryCard(result: GameResult, isBest: Boolean) {
    val palette = menuUiPalette()
    val quantities = recordFruitQuantities(result)
    val date = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(result.dateMillis))
    val circlesKnown = result.rulesVersion in ROUND_COLLECTION_RULES_VERSION..GameRules.VERSION
    Box(Modifier.fillMaxWidth().then(if (isBest) Modifier.testTag("bestRecordCard") else Modifier)) {
        ThemedCard(palette.secondary, Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .testTag("recordCard-${result.id}")) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isBest) BestRecordHero(result, date, quantities.orEmpty(), palette)
                else CompactRecordIdentity(result, date, palette)
                if (isBest) {
                    Spacer(Modifier.height(10.dp))
                    SettingsDivider(palette.secondary)
                } else Spacer(Modifier.height(4.dp))
                Spacer(Modifier.height(4.dp))
                HistoryMetrics(result, circlesKnown, palette)
                if (!isBest && quantities?.any { it > 0 } == true) {
                    RecordFruitRow(quantities, result.id, Modifier.padding(top = 8.dp))
                }
                RecordHistoryNote(result.id, circlesKnown, quantities == null, palette)
                result.difficulty?.let { Text(it.title, color = palette.muted, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun BestRecordHero(result: GameResult, date: String, quantities: List<Int>, palette: GamePalette) {
    val contract = rememberRecordArtworkContract()
    val artworkWidthNeeded = recordArtworkMinimumWidth(quantities, contract)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val score = groupedRecordScore(result.score)
    val scoreWidth = with(density) {
        textMeasurer.measure(score, style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
            constraints = Constraints()).size.width.toDp()
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val historyWidth = maxWidth.value
        val sideWidth = (historyWidth * contract.artworkFraction)
            .coerceIn(contract.artworkMinWidthDp, contract.artworkMaxWidthDp)
        val textColumnWidth = historyWidth - sideWidth - HISTORY_SIDE_GAP_DP
        val stacked = density.fontScale >= 1.3f || textColumnWidth < contract.minimumTextWidthDp ||
            artworkWidthNeeded > sideWidth || scoreWidth > textColumnWidth.dp
        if (stacked) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                BestRecordIdentity(result, score, date, palette, historyWidth)
                Spacer(Modifier.height(HISTORY_SIDE_GAP_DP.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RecordArtwork(quantities, result.id, max(contract.stackedArtworkWidthDp, artworkWidthNeeded))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 188.dp),
                horizontalArrangement = Arrangement.spacedBy(HISTORY_SIDE_GAP_DP.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BestRecordIdentity(result, score, date, palette, textColumnWidth, Modifier.weight(1f))
                RecordArtwork(quantities, result.id, sideWidth)
            }
        }
    }
}

@Composable
private fun CompactRecordIdentity(result: GameResult, date: String, palette: GamePalette) {
    val score = groupedRecordScore(result.score)
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scoreWidth = with(density) {
            textMeasurer.measure(score, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                constraints = Constraints()).size.width.toDp()
        }
        val stacked = scoreWidth > maxWidth * .42f
        val fontScaleFactor = if (scoreWidth > maxWidth) maxWidth / scoreWidth else 1f
        val scoreSize = (24f * fontScaleFactor).coerceAtLeast(14f).sp
        if (stacked) {
            Column(Modifier.fillMaxWidth()) {
                Text(result.name, Modifier.fillMaxWidth().testTag("recordName-${result.id}"),
                    fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(score, Modifier.fillMaxWidth().testTag("recordScore-${result.id}"),
                    fontSize = scoreSize, lineHeight = 30.sp, fontWeight = FontWeight.Bold,
                    color = palette.accent, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top) {
            Text(result.name, Modifier.weight(1f).testTag("recordName-${result.id}"),
                fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
            Text(score, Modifier.width(scoreWidth).testTag("recordScore-${result.id}"),
                fontSize = scoreSize, lineHeight = 30.sp, fontWeight = FontWeight.Bold,
                color = palette.accent, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
        }
    }
    Text(date, Modifier.fillMaxWidth().testTag("recordDate-${result.id}"),
        color = palette.muted, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
private fun RecordHistoryNote(id: String, circlesKnown: Boolean, awardsUnknown: Boolean, palette: GamePalette) {
    val message = when {
        awardsUnknown -> "Награды и круги для этой записи неизвестны"
        !circlesKnown -> "Круги в этой записи не сохранены"
        else -> return
    }
    Text(message, color = palette.muted, fontSize = 12.sp, lineHeight = 16.sp,
        modifier = Modifier.testTag("recordHistoricalNote-$id"))
}

@Composable
private fun BestRecordIdentity(
    result: GameResult,
    groupedScore: String,
    date: String,
    palette: GamePalette,
    availableWidthDp: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().heightIn(min = 188.dp), verticalArrangement = Arrangement.Center) {
        Text("Лучший результат", fontSize = 12.sp, lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold, color = palette.secondary,
            modifier = Modifier.testTag("bestRecordEyebrow"))
        Text(groupedScore, Modifier.width(availableWidthDp.dp).testTag("recordScore-${result.id}"),
            fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold,
            color = palette.accent, maxLines = 1, softWrap = false)
        Text("очков", color = palette.muted, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(result.name, Modifier.fillMaxWidth().testTag("recordName-${result.id}"),
            fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
        Text(date, Modifier.fillMaxWidth().testTag("recordDate-${result.id}"),
            color = palette.muted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun HistoryMetrics(result: GameResult, circlesKnown: Boolean, palette: GamePalette) {
    val values = listOf(result.lines.toString(), result.level.toString(), formatDuration(result.durationMillis),
        if (circlesKnown) result.completedRounds.toString() else "—")
    val labels = listOf("Строки", "Уровень", "Время", "Круги")
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fourColumnWidth = (maxWidth.value - HISTORY_METRIC_GAP_DP * 3) / 4
        val needsTwoColumns = values.indices.any { index ->
            val labelWidth = textMeasurer.measure(labels[index], style = TextStyle(fontSize = 12.sp)).size.width
            val valueWidth = textMeasurer.measure(values[index], style = TextStyle(fontSize = 19.sp,
                fontWeight = FontWeight.Medium)).size.width
            with(density) { max(labelWidth, valueWidth).toDp().value > fourColumnWidth }
        }
        val columns = if (needsTwoColumns) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(HISTORY_METRIC_GAP_DP.dp)) {
            values.indices.chunked(columns).forEach { rowIndices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HISTORY_METRIC_GAP_DP.dp)) {
                    rowIndices.forEach { index ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(values[index], color = palette.text, fontSize = 19.sp,
                                lineHeight = 24.sp, fontWeight = FontWeight.Medium)
                            Text(labels[index], color = palette.muted, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                    repeat(columns - rowIndices.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

internal fun groupedRecordScore(score: Int): String {
    val raw = score.toString()
    val start = if (raw.startsWith('-')) 1 else 0
    return buildString {
        if (start == 1) append('-')
        raw.substring(start).forEachIndexed { index, digit ->
            if (index > 0 && (raw.length - start - index) % SCORE_DIGITS_PER_GROUP == 0) append('\u202F')
            append(digit)
        }
    }
}
