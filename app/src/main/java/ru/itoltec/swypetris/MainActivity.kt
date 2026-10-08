package ru.itoltec.swypetris

import android.os.Build
import android.os.Bundle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ru.itoltec.swypetris.ui.theme.SwypetrisTheme

private const val SCORE_FONT_FIT_STEPS = 12
private const val SCORE_PULSE_LEG_MILLIS = 110
private const val FRUIT_COLUMNS = 3


/** Current score scale exposed for testing a single pulse with a controlled Compose clock. */
internal val ScorePulseScale = androidx.compose.ui.semantics.SemanticsPropertyKey<Float>("ScorePulseScale")

/** Android entry point that connects a retained game model to the Compose UI. */
class MainActivity : ComponentActivity() {
    private val gameModel: GameViewModel by viewModels()
    private val appUpdates by lazy { AppUpdates(this) }
    private var windowFocused by mutableStateOf(false)

    /** Keep status information visible while content respects the window's safe upper edge. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwypetrisTheme(darkTheme = true, dynamicColor = false) {
                DisposableEffect(gameModel.paletteId) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    val palette = GamePalettes.find(gameModel.paletteId)
                    controller.isAppearanceLightStatusBars = palette.light
                    controller.isAppearanceLightNavigationBars = palette.light
                    @Suppress("DEPRECATION")
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
                }
                LaunchedEffect(gameModel.screen, windowFocused) {
                    if (windowFocused) {
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                SwypetrisApp(gameModel, appUpdates, onExit = { gameModel.pause(); finishAndRemoveTask() })
            }
        }
        if (savedInstanceState == null) appUpdates.check(manual = false)
    }

    override fun onDestroy() {
        appUpdates.close()
        super.onDestroy()
    }

    /** Forwards backgrounding to the model so gameplay clocks and effects stop. */
    override fun onPause() {
        gameModel.onBackground()
        super.onPause()
    }

    /** Restores foreground status while leaving an explicitly paused game paused. */
    override fun onResume() {
        super.onResume()
        gameModel.onForeground()
    }

    /** Reports focus changes so an obscured window cannot keep gameplay running. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        windowFocused = hasFocus
        gameModel.onWindowFocusChanged(hasFocus)
    }

    /** Handles a new launch intent without replacing the retained game model. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Reusing the task must never start or resume a game implicitly.
        gameModel.pause()
        appUpdates.check(manual = false)
    }
}

/** Connects Android lifecycle, system gesture settings, and screen selection. */
@Composable
internal fun SwypetrisApp(model: GameViewModel, updates: AppUpdates? = null, onExit: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var updateWindowActive by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(model, density, owner) {
        val configuration = ViewConfiguration.get(context)
        model.input.configureGestures(GestureConfig(
            tapSlop = configuration.scaledTouchSlop / density
        ))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { updateWindowActive = false; model.onBackground() }
            if (event == Lifecycle.Event.ON_RESUME) { updateWindowActive = true; model.onForeground() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            model.input.cancelGesture()
        }
    }
    LaunchIntroClock(model)
    LaunchedEffect(updateWindowActive, model.screen, updates?.automaticEnabled,
        updates?.delivery?.notice, updates?.notice) {
        if (updateWindowActive) updates?.installAutomaticallyIfReady(model)
    }
    BackHandler(enabled = model.launchIntroPending || model.screen != GameScreen.MENU) {
        model.navigation.back()
    }
    val palette = GamePalettes.find(model.paletteId)
    CompositionLocalProvider(LocalGamePalette provides palette) {
    MaterialTheme(colorScheme = palette.scheme(), typography = MaterialTheme.typography) {
    Surface(color = palette.background, contentColor = palette.text, modifier = Modifier.fillMaxSize()) {
        val safeInsets = WindowInsets.safeDrawing
        val gameVisible = model.screen == GameScreen.PLAYING
        Box(Modifier.fillMaxSize().then(if (gameVisible) Modifier else Modifier.windowInsetsPadding(safeInsets))) {
            if (model.screen == GameScreen.MENU) ThemeBackdrop(palette,
                LaunchIntroMotion.approachProgress(model.launchIntroMillis))
            key(model.screen) {
            AppScreen(model, updates, onExit)
            }
        }
    }
}

    if (model.screen in listOf(GameScreen.MENU, GameScreen.SETTINGS) && updates != null)
        AppUpdateDialogs(model, updates)

    }
}

/** Fits one HUD line without clipping its text at large font scales or long scores. */
@Composable
private fun fittedHudFont(text: String, preferred: TextUnit, width: Dp, height: Dp? = null,
    fontFamily: FontFamily? = null, fontWeight: FontWeight? = null): TextUnit {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = preferred, fontFamily = fontFamily, fontWeight = fontWeight,
        lineHeight = preferred * 1.2f, letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false))
    return remember(text, preferred, width, height, density, style, measurer) {
        val widthPx = with(density) { width.roundToPx() }.coerceAtLeast(1)
        val bounds = Constraints(minWidth = widthPx, maxWidth = widthPx,
            maxHeight = height?.let { with(density) { it.toPx().toInt() }.coerceAtLeast(1) } ?: Constraints.Infinity)
        fun fits(font: TextUnit): Boolean = !measurer.measure(text,
            style = style.copy(fontSize = font, lineHeight = font * 1.2f),
            constraints = bounds, softWrap = false, maxLines = 1).hasVisualOverflow
        if (fits(preferred)) preferred else {
            // Measure the actual constrained paragraph rather than assuming proportional glyph widths.
            var low = .1f
            var high = preferred.value
            repeat(SCORE_FONT_FIT_STEPS) {
                val middle = (low + high) / 2
                if (fits(middle.sp)) low = middle else high = middle
            }
            low.sp
        }
    }
}

/** Hold uses the left lane; the right-aligned score and fruit collection share the right lane. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GameHud(state: GameState, headerHeight: Dp = 48.dp,
    horizontalInsets: PaddingValues = PaddingValues(0.dp), bottomInset: Dp = 0.dp,
    pieceOverlay: @Composable () -> Unit = {}) {
    val displayed = displayScore(state.score)
    val scale = rememberScorePulse(state)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val leftMargin = GAMEPLAY_HUD_MARGIN.dp +
            horizontalInsets.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
        val rightMargin = GAMEPLAY_HUD_MARGIN.dp +
            horizontalInsets.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
        val cellWidth = maxWidth / BoardGeometry.WIDTH
        val nextCells = spawnPiece(state.next).cells()
        val hintLeft = cellWidth * nextCells.minOf { it.x }
        val hintRight = cellWidth * (nextCells.maxOf { it.x } + 1)
        val leftLaneWidth = (hintLeft - leftMargin - 4.dp).coerceAtLeast(1.dp)
        val rightLaneWidth = (maxWidth - rightMargin - hintRight - 4.dp).coerceAtLeast(1.dp)
        val maxHoldWidth = minOf((maxWidth * .22f).coerceIn(48.dp, 88.dp), leftLaneWidth)
        val holdHeight = minOf((headerHeight * .7f).coerceIn(28.dp, 48.dp), headerHeight)
        val holdWidth = holdAreaWidth(state.held, maxHoldWidth, holdHeight)
        val scoreTop = minOf(cellWidth, headerHeight / SPAWN_DISPLAY_ROWS) * .07f
        val scoreHeight = minOf(
            ((maxHeight - bottomInset) / 32).coerceIn(16.dp, 28.dp) * density.fontScale,
            rightLaneWidth / digitalScoreAspect(displayed.length) / SCORE_MAX_SCALE,
            (headerHeight - scoreTop).coerceAtLeast(1.dp) / SCORE_MAX_SCALE
        )
        val scoreWidth = scoreHeight * digitalScoreAspect(displayed.length)

        EarnedFruits(state, scoreWidth, Modifier.align(Alignment.TopEnd)
            .offset(x = -rightMargin, y = scoreTop + scoreHeight * SCORE_MAX_SCALE + FRUIT_GAP.dp)
            .width(scoreWidth).testTag("earnedFruits"))

        // Only the falling piece crosses over fruit pixels; score and Hold stay in front.
        pieceOverlay()
        DigitalScore(displayed, scoreHeight,
            modifier = Modifier.align(Alignment.TopEnd)
                .offset(x = -rightMargin, y = scoreTop)
                .graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .semantics {
                    this[ScorePulseScale] = scale.value
                    contentDescription = "Очки $displayed"
                }
                .testTag("score"))

        Column(
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = leftMargin, y = scoreTop)
                .width(holdWidth),
            horizontalAlignment = Alignment.Start
        ) {
            HoldPreview(state.held, state.holdUsed,
                Modifier.width(holdWidth).height(holdHeight).testTag("holdPreview"))
            HoldLabel(holdWidth)
        }
    }
}

/** Keeps up to three complete fruit/quantity pairs evenly distributed inside the score width. */
@Composable
private fun EarnedFruits(state: GameState, width: Dp, modifier: Modifier) {
    val fruits = Fruit.entries.zip(state.fruitCounts).filter { it.second > 0 }
    if (fruits.isEmpty()) return
    val itemWidth = ((width - FRUIT_GAP.dp * 2) / 3).coerceAtLeast(1.dp)
    val iconSize = minOf(FRUIT_SIZE.dp, itemWidth * .55f).coerceAtLeast(1.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FRUIT_GAP.dp)) {
        fruits.chunked(FRUIT_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = if (row.size == 1) Arrangement.Center else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                row.forEach { (fruit, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.widthIn(max = itemWidth).testTag("earnedFruit_${fruit.name}")) {
                        if (count > 1) {
                            val label = "$count ×"
                            val font = fittedHudFont(label, 10.sp,
                                (itemWidth - iconSize - 2.dp).coerceAtLeast(1.dp))
                            Text(label, maxLines = 1, softWrap = false,
                                style = TextStyle(fontSize = font, lineHeight = font * 1.2f, letterSpacing = 0.sp,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)),
                                color = LocalGamePalette.current.text,
                                modifier = Modifier.testTag("earnedFruitCount_${fruit.name}"))
                        }
                        FruitIcon(fruit, Modifier.size(iconSize))
                    }
                }
            }
        }
    }
}


@Composable
private fun HoldLabel(width: Dp) {
    val labelFont = fittedHudFont("ЗАПАС", 14.sp, width,
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    Text("ЗАПАС", color = LocalGamePalette.current.muted, maxLines = 1, softWrap = false,
        textAlign = TextAlign.Center,
        style = TextStyle(fontSize = labelFont, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, lineHeight = labelFont * 1.2f, letterSpacing = 0.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false)),
        modifier = Modifier.fillMaxWidth().testTag("holdLabel"))
}

/** Shrinks only the reserve area; the held cells retain their original step size. */
private fun holdAreaWidth(held: Tetromino?, maxWidth: Dp, height: Dp): Dp {
    val cells = (held ?: Tetromino.T).shape
    val columns = cells.maxOf { it.x } - cells.minOf { it.x } + 1
    val rows = cells.maxOf { it.y } - cells.minOf { it.y } + 1
    val padding = 4.dp * 2
    val step = minOf((maxWidth - padding) / columns, (height - padding) / rows)
    return (step * columns + padding).coerceIn(1.dp, maxWidth)
}

/** Draws the current Hold piece in spawn orientation and exposes availability to accessibility. */
@Composable
private fun HoldPreview(held: Tetromino?, used: Boolean, modifier: Modifier) {
    val palette = LocalGamePalette.current
    Canvas(modifier.semantics {
        contentDescription = when {
            held == null -> "Запас пуст, обмен доступен"
            used -> "Запас ${held.name}, обмен недоступен"
            else -> "Запас ${held.name}, обмен доступен"
        }
    }) {
        if (held == null) return@Canvas
        val cells = held.shape
        val firstColumn = cells.minOf { it.x }
        val columns = cells.maxOf { it.x } - firstColumn + 1
        val firstRow = cells.minOf { it.y }
        val rows = cells.maxOf { it.y } - firstRow + 1
        val padding = 4.dp.toPx()
        val step = minOf((size.width - padding * 2) / columns, (size.height - padding * 2) / rows)
        // Preserve cell size; align the painted edge with the inset-aware reserve lane.
        // block() adds a gap on both axes, so cancel it at the lane origin.
        val origin = Offset(-step * .07f, -step * .07f)
        clipRect {
            cells.forEach {
                block(Cell(it.x - firstColumn, it.y - firstRow), palette.piece(held), palette.finish,
                    palette.texture, origin, Size(step, step), alpha = if (used) .055f else .16f)
            }
        }
    }
}


@Composable
private fun rememberScorePulse(state: GameState):
    androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val currentLines by rememberUpdatedState(state.lines)
    val nearing by rememberUpdatedState(GameRules.nearingLevel(state.lines, state.startingLevel))
    val scale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        var pulsing = false
        snapshotFlow { currentLines }.drop(1).collect {
            if (nearing && !pulsing) {
                pulsing = true
                launch {
                    try {
                        scale.animateTo(SCORE_MAX_SCALE, tween(SCORE_PULSE_LEG_MILLIS))
                        scale.animateTo(1f, tween(SCORE_PULSE_LEG_MILLIS))
                    } finally { pulsing = false }
                }
            }
        }
    }
    return scale
}
