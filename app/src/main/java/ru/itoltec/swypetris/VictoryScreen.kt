package ru.itoltec.swypetris

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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

internal const val VICTORY_ANIMATION_MILLIS = 8000L

private const val UNEARNED_FRUIT_OPACITY = .22f
private const val TROPHY_ASPECT_RATIO = 1.5f
private const val STATIC_FIREWORK_MILLIS = 2400L
private const val FIREWORK_BURSTS = 9
private const val PARTICLES_PER_BURST = 28
private const val FIREWORK_OPACITY = .85f


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
                FruitIcon(fruit, Modifier.size(iconSize).alpha(if (count > 0) 1f else UNEARNED_FRUIT_OPACITY))
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
    val animate = remember { Settings.Global.getFloat(context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    LaunchedEffect(model, owner, animate) {
        if (animate) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withFrameNanos { it }
            while (model.victoryAnimationMillis < VICTORY_ANIMATION_MILLIS) {
                val now = withFrameNanos { it }
                model.advanceVictoryAnimation((now - last) / GameRules.NANOS_PER_MILLI)
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
                    contentScale = ContentScale.Fit,
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().aspectRatio(TROPHY_ASPECT_RATIO))
            }
            item { RoundFruitCollection(state.fruitCounts) }
            item {
                Text("Круг ${state.completedRounds + 1} пройден", style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center)
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
        VictoryFireworks { if (animate) model.victoryAnimationMillis else STATIC_FIREWORK_MILLIS }
    }
}

/** Pixel fireworks do not intercept touches and disappear after eight seconds. */
@Composable
private fun VictoryFireworks(time: () -> Long) {
    val colors = LocalGamePalette.current.pieces
    Canvas(Modifier.fillMaxSize().testTag("victoryFireworks")) {
        val elapsed = time()
        if (elapsed >= VICTORY_ANIMATION_MILLIS) return@Canvas
        repeat(FIREWORK_BURSTS) { burst ->
            val age = (elapsed - burst * 650) / 1800f
            if (age !in 0f..1f) return@repeat
            val center = Offset(size.width * (.10f + (burst * 37 % 80) / 100f),
                size.height * (.08f + (burst % 3) * .12f))
            repeat(PARTICLES_PER_BURST) { particle ->
                val angle = particle * 2.0 * Math.PI / PARTICLES_PER_BURST
                val radius = size.width * .26f * age * (if (particle % 2 == 0) 1f else .65f)
                val point = center + Offset(cos(angle).toFloat() * radius,
                    sin(angle).toFloat() * radius + age * age * 65.dp.toPx())
                drawRect(colors[(particle + burst) % colors.size].copy(alpha = (1f - age) * FIREWORK_OPACITY),
                    point, Size(3.dp.toPx(),
                    3.dp.toPx()))
            }
        }
    }
}
