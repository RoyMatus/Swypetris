package ru.itoltec.swypetris

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.cos
import kotlin.math.sin

private const val UNEARNED_FRUIT_OPACITY = .22f
private const val STATIC_FIREWORK_MILLIS = 1800L
private const val FIREWORK_BURSTS = 6
private const val PARTICLES_PER_BURST = 28
private const val FIREWORK_OPACITY = .85f
private const val MAX_FRAME_NANOS = 100_000_000L
private const val FRUIT_GAP_DP = 7
private const val OVERLAY_ALPHA = .95f
private const val GRADIENT_CLEAR_END = .32f
private const val GRADIENT_DARK_START = .50f
private const val GRADIENT_DARK = 0xD8001020
private const val GRADIENT_BOTTOM = 0xF8001020
private const val LAUNCH_FRACTION = .18f
private const val TEXT_SHADOW_RADIUS = 4f
private val VICTORY_TITLE_COLOR = Color(0xFFFFD54F)

/** Allows instrumentation to verify the static fallback without changing device settings. */
internal val LocalVictoryAnimations = staticCompositionLocalOf<Boolean?> { null }


/** Complete fruit set with cumulative quantities; unearned silhouettes are dimmed. */
@Composable
internal fun RoundFruitCollection(counts: List<Int>, iconSize: Dp = 28.dp) {
    Row(Modifier.testTag("fruitCollection"), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Fruit.entries.forEachIndexed { index, fruit ->
            val count = counts.getOrElse(index) { 0 }
            RoundFruit(fruit, count, iconSize, LocalGamePalette.current.text)
        }
    }
}

@Composable
private fun RoundFruit(fruit: Fruit, count: Int, iconSize: Dp, quantityColor: Color) {
    Row(Modifier.clearAndSetSemantics {
        contentDescription = "${fruit.title}: ${if (count == 0) "ещё не получен" else "собрано $count"}"
    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        if (count > 1) Text("$count ×", fontSize = 10.sp, color = quantityColor)
        FruitIcon(fruit, Modifier.size(iconSize).alpha(if (count > 0) 1f else UNEARNED_FRUIT_OPACITY))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VictoryFruitCollection(counts: List<Int>) {
    FlowRow(Modifier.fillMaxWidth().testTag("fruitCollection"),
        horizontalArrangement = Arrangement.spacedBy(FRUIT_GAP_DP.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Fruit.entries.forEachIndexed { index, fruit ->
            RoundFruit(fruit, counts.getOrElse(index) { 0 }, 28.dp, Color.White)
        }
    }
}

/** Victory pauses the game until the player explicitly starts the next round. */
@Composable
internal fun VictoryScreen(model: GameViewModel) {
    val state = model.game ?: return
    val palette = LocalGamePalette.current
    VictoryScene(model) { VictoryContent(state, palette, model::nextRound) }
}

/** Shared, lifecycle-aware celebration; terminal victory replaces only the foreground content. */
@Composable
internal fun VictoryScene(model: GameViewModel, content: @Composable () -> Unit) {
    val animate = rememberVictoryAnimation(model)
    Box(Modifier.fillMaxSize().testTag("victoryScene")) {
        Image(painterResource(R.drawable.victory_trophy), "Кубок из блоков и восемь фруктов",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().testTag("victoryArtwork"))
        VictoryFireworks { if (animate) model.victoryAnimationMillis else STATIC_FIREWORK_MILLIS }
        Box(Modifier.fillMaxSize().alpha(OVERLAY_ALPHA).background(Brush.verticalGradient(
            0f to Color.Transparent, GRADIENT_CLEAR_END to Color.Transparent,
            GRADIENT_DARK_START to Color(GRADIENT_DARK), 1f to Color(GRADIENT_BOTTOM))))
        content()
    }
}

@Composable
private fun rememberVictoryAnimation(model: GameViewModel): Boolean {
    val owner = LocalLifecycleOwner.current
    var systemEnabled by remember { mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        ValueAnimator.areAnimatorsEnabled()) }
    val animate = LocalVictoryAnimations.current ?: systemEnabled
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) systemEnabled =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(model, owner, animate) {
        if (animate) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withInfiniteAnimationFrameNanos { it }
            while (true) {
                val now = withInfiniteAnimationFrameNanos { it }
                model.advanceVictoryAnimation((now - last).coerceIn(0L, MAX_FRAME_NANOS) /
                    GameRules.NANOS_PER_MILLI)
                last = now
            }
        }
    }
    return animate
}

@Composable
private fun VictoryContent(state: GameState, palette: GamePalette, onNextRound: () -> Unit) {
    val textShadow = TextStyle(shadow = Shadow(Color(0xFF001020), Offset(1f, 2f), TEXT_SHADOW_RADIUS))
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val topSpace = maxHeight * .43f
        LazyColumn(Modifier.fillMaxSize().testTag("victoryPage"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topSpace, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom)) {
            item {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val font = minOf(32f, maxWidth.value / 8.2f / LocalDensity.current.fontScale).sp
                    Text("ПОЗДРАВЛЯЕМ!\nПОБЕДА!", fontSize = font, lineHeight = font * 1.3f,
                        fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                        color = VICTORY_TITLE_COLOR, style = textShadow, modifier = Modifier.testTag("victoryTitle"))
                }
            }
            item { VictoryFruitCollection(state.fruitCounts) }
            item {
                Text("Круг ${state.completedRounds + 1} пройден", style = MaterialTheme.typography.titleLarge,
                    color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("${state.score} очков", style = MaterialTheme.typography.headlineMedium,
                    color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            }
            item {
                Text("Все фрукты собраны! Следующий круг начнётся на пустом поле. Счёт и скорость сохранятся.",
                    Modifier.widthIn(max = 560.dp), color = Color.White, textAlign = TextAlign.Center)
            }
            item {
                AppActionButton("Следующий круг", ActionStyle.PRIMARY,
                    Modifier.widthIn(max = 440.dp).fillMaxWidth().testTag("nextRound"),
                    palette.piece(Tetromino.S), onNextRound)
            }
        }
    }
}

/** Pixel fireworks launch, burst and fade in staggered continuous loops. */
@Composable
private fun VictoryFireworks(time: () -> Long) {
    val colors = LocalGamePalette.current.pieces
    Canvas(Modifier.fillMaxSize().testTag("victoryFireworks")) {
        val elapsed = time()
        repeat(FIREWORK_BURSTS) { burst ->
            val age = VictoryMotion.age(elapsed, burst)
            val launch = VictoryMotion.launch(age)
            val expansion = VictoryMotion.expansion(age)
            val center = Offset(size.width * (.10f + (burst * 37 % 80) / 100f),
                size.height * (.10f + (burst % 3) * .10f))
            val start = Offset(center.x, center.y + size.height * .30f)
            if (age < LAUNCH_FRACTION) {
                drawRect(colors[burst % colors.size].copy(alpha = VictoryMotion.opacity(launch)),
                    Offset(start.x, start.y + (center.y - start.y) * launch),
                    Size(3.dp.toPx(), 8.dp.toPx()))
                return@repeat
            }
            val opacity = VictoryMotion.opacity(expansion) * FIREWORK_OPACITY
            repeat(PARTICLES_PER_BURST) { particle ->
                val angle = particle * 2.0 * Math.PI / PARTICLES_PER_BURST
                val radius = size.width * .26f * expansion * (if (particle % 2 == 0) 1f else .65f)
                val point = center + Offset(cos(angle).toFloat() * radius,
                    sin(angle).toFloat() * radius + expansion * expansion * 65.dp.toPx())
                drawRect(colors[(particle + burst) % colors.size].copy(alpha = opacity),
                    point, Size(3.dp.toPx(),
                    3.dp.toPx()))
            }
        }
    }
}
