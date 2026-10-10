package ru.itoltec.swypetris

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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

private const val UNEARNED_FRUIT_OPACITY = .22f
private const val STATIC_FIREWORK_MILLIS = 2400L
private const val MAX_FRAME_NANOS = 100_000_000L
private const val FRUIT_GAP_DP = 7
private const val OVERLAY_ALPHA = .95f
private const val GRADIENT_CLEAR_END = .48f
private const val GRADIENT_DARK_START = .56f
private const val GRADIENT_DARK = 0xD8001020
private const val GRADIENT_BOTTOM = 0xF8001020
private const val TEXT_SHADOW_RADIUS = 4f
private const val COMPACT_TITLE_SP = 28f
private const val REGULAR_TITLE_SP = 32f
private const val ROUND_HEADING_WIDTH = 8.2f
private val VICTORY_TITLE_COLORS = listOf(Color(0xFFFFF0A0), Color(0xFFFFCC36), Color(0xFFED9700))
private val VICTORY_TITLE_SHADOW = Color(0xFF7E4400)

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
    VictoryScene(model) { layout -> VictoryContent(state, model::nextRound, layout) }
}

/** Shared, lifecycle-aware celebration; terminal victory replaces only the foreground content. */
@Composable
internal fun VictoryScene(model: GameViewModel, celebration: VictoryCelebration = VictoryCelebration.ROUND,
    content: @Composable (VictoryLayout) -> Unit) {
    val animate = rememberVictoryAnimation(model)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("victoryScene")) {
        val layout = VictoryLayout.forHeight(maxHeight)
        VictoryBackdrop(layout)
        VictoryFireworks(celebration) { if (animate) model.victoryAnimationMillis else STATIC_FIREWORK_MILLIS }
        if (celebration != VictoryCelebration.POSTCARD) VictoryForeground(layout)
        Box(Modifier.fillMaxSize().alpha(OVERLAY_ALPHA).background(Brush.verticalGradient(
            0f to Color.Transparent, GRADIENT_CLEAR_END to Color.Transparent,
            GRADIENT_DARK_START to Color(GRADIENT_DARK), 1f to Color(GRADIENT_BOTTOM))))
        content(layout)
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
private fun VictoryContent(state: GameState, onNextRound: () -> Unit,
    layout: VictoryLayout) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val topSpace = maxHeight * layout.contentTop
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("victoryPage"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topSpace, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (layout.compact) 8.dp else 12.dp, Alignment.Bottom)) {
                item { VictoryTitle(layout) }
                item { VictoryFruitCollection(state.fruitCounts) }
                item {
                    Text("Круг ${state.completedRounds + 1} пройден", style = MaterialTheme.typography.titleLarge,
                        color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text("${state.score} очков", style = MaterialTheme.typography.headlineMedium,
                        color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
                item {
                    Text("Все фрукты собраны!\nСледующий круг начнётся на пустом поле.\nСчёт и скорость сохранятся.",
                        Modifier.widthIn(max = 560.dp), color = Color.White, textAlign = TextAlign.Center)
                }
            }
            Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                contentAlignment = Alignment.Center) {
                VictoryActionButton("Следующий круг",
                    Modifier.widthIn(max = 440.dp).fillMaxWidth().testTag("nextRound"),
                    onNextRound)
            }
        }
    }
}

@Composable
private fun VictoryTitle(layout: VictoryLayout) {
    VictoryHeading("ПОЗДРАВЛЯЕМ!\nПОБЕДА!", layout, Modifier.testTag("victoryTitle"))
}

@Composable
internal fun VictoryHeading(text: String, layout: VictoryLayout, modifier: Modifier,
    widthDivisor: Float = ROUND_HEADING_WIDTH) {
    val textShadow = TextStyle(brush = Brush.verticalGradient(VICTORY_TITLE_COLORS),
        shadow = Shadow(VICTORY_TITLE_SHADOW, Offset(1f, 2f), TEXT_SHADOW_RADIUS))
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val font = minOf(if (layout.compact) COMPACT_TITLE_SP else REGULAR_TITLE_SP,
            maxWidth.value / widthDivisor / LocalDensity.current.fontScale).sp
        Text(text, fontSize = font, lineHeight = font * 1.3f,
            fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
            style = textShadow, modifier = modifier)
    }
}
