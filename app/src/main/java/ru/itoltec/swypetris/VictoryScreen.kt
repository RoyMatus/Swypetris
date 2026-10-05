package ru.itoltec.swypetris

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.cos
import kotlin.math.sin

/** Complete fruit set with cumulative quantities; unearned silhouettes are dimmed. */
@Composable
internal fun RoundFruitCollection(counts: List<Int>, iconSize: Dp = 28.dp) {
    Row(Modifier.testTag("fruitCollection"), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Fruit.entries.forEachIndexed { index, fruit ->
            val count = counts.getOrElse(index) { 0 }
            Row(Modifier.clearAndSetSemantics {
                contentDescription = "${fruit.title}: ${if (count == 0) "ещё не получен" else "собрано $count"}"
            }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                if (count > 1) Text("$count ×", fontSize = 10.sp, color = LocalGamePalette.current.text)
                FruitIcon(fruit, Modifier.size(iconSize).alpha(if (count > 0) 1f else .22f))
            }
        }
    }
}

/** Victory pauses the game until the player explicitly starts the next round. */
@Composable
internal fun VictoryScreen(model: GameViewModel) {
    val state = model.game ?: return
    val palette = LocalGamePalette.current
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val animate = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    LaunchedEffect(model, owner, animate) {
        if (animate) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withFrameNanos { it }
            while (model.victoryAnimationMillis < 8000) {
                val now = withFrameNanos { it }
                model.advanceVictoryAnimation((now - last) / 1_000_000)
                last = now
            }
        }
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        LazyColumn(Modifier.fillMaxSize().testTag("victoryPage"), contentPadding = PaddingValues(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val font = minOf(32f, maxWidth.value / 8.2f / LocalDensity.current.fontScale).sp
                    Text("ПОЗДРАВЛЯЕМ!\nПОБЕДА!", fontSize = font, lineHeight = font * 1.3f,
                        fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = palette.gold)
                }
            }
            item {
                Image(painterResource(R.drawable.victory_trophy), "Кубок из блоков и восемь фруктов",
                    contentScale = ContentScale.Fit, modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().aspectRatio(1.5f))
            }
            item { RoundFruitCollection(state.fruitCounts) }
            item {
                Text("Круг ${state.completedRounds + 1} пройден", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text("${state.score} очков", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            item {
                Text("Все фрукты собраны! Следующий круг начнётся на пустом поле. Счёт и скорость сохранятся.",
                    Modifier.widthIn(max = 560.dp), color = palette.muted, textAlign = TextAlign.Center)
            }
            item {
                AppActionButton("Следующий круг", ActionStyle.PRIMARY,
                    Modifier.widthIn(max = 440.dp).fillMaxWidth().testTag("nextRound"),
                    palette.piece(Tetromino.S), model::nextRound)
            }
        }
        VictoryFireworks { if (animate) model.victoryAnimationMillis else 2400L }
    }
}

/** Pixel fireworks do not intercept touches and disappear after eight seconds. */
@Composable
private fun VictoryFireworks(time: () -> Long) {
    val colors = LocalGamePalette.current.pieces
    Canvas(Modifier.fillMaxSize().testTag("victoryFireworks")) {
        val elapsed = time()
        if (elapsed >= 8000) return@Canvas
        repeat(9) { burst ->
            val age = (elapsed - burst * 650) / 1800f
            if (age !in 0f..1f) return@repeat
            val center = Offset(size.width * (.10f + (burst * 37 % 80) / 100f), size.height * (.08f + (burst % 3) * .12f))
            repeat(28) { particle ->
                val angle = particle * 2.0 * Math.PI / 28
                val radius = size.width * .26f * age * (if (particle % 2 == 0) 1f else .65f)
                val point = center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius + age * age * 65.dp.toPx())
                drawRect(colors[(particle + burst) % 7].copy(alpha = (1f - age) * .85f), point, Size(3.dp.toPx(), 3.dp.toPx()))
            }
        }
    }
}
