package ru.itoltec.swypetris

import android.os.Build
import android.os.Bundle
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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

/** Current score scale exposed for testing a single pulse with a controlled Compose clock. */
internal val ScorePulseScale = androidx.compose.ui.semantics.SemanticsPropertyKey<Float>("ScorePulseScale")

/** Android entry point that connects a retained game model to the Compose UI. */
class MainActivity : ComponentActivity() {
    private val gameModel: GameViewModel by viewModels()

    /** Draw backgrounds edge-to-edge, keeping system bars visible on every screen. */
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
                    if (Build.VERSION.SDK_INT >= 29) {
                        window.isNavigationBarContrastEnforced = false
                        @Suppress("DEPRECATION")
                        window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    } else if (Build.VERSION.SDK_INT >= 26 || !palette.light) {
                        // Older three-button navigation still needs a legible matching background.
                        @Suppress("DEPRECATION")
                        window.navigationBarColor = palette.background.toArgb()
                    }
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
                }
                SwypetrisApp(gameModel, onExit = { gameModel.pause(); finishAndRemoveTask() })
            }
        }
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
        gameModel.onWindowFocusChanged(hasFocus)
    }

    /** Handles a new launch intent without replacing the retained game model. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Reusing the task must never start or resume a game implicitly.
        gameModel.pause()
    }
}

/** Connects Android lifecycle, system gesture settings, and screen selection. */
@Composable
fun SwypetrisApp(model: GameViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(model, density, owner) {
        val configuration = ViewConfiguration.get(context)
        model.configureGestures(GestureConfig(
            tapSlop = configuration.scaledTouchSlop / density
        ))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) model.onBackground()
            if (event == Lifecycle.Event.ON_RESUME) model.onForeground()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            model.cancelGesture()
        }
    }
    LaunchIntroClock(model)
    BackHandler(enabled = model.launchIntroPending || model.screen != GameScreen.MENU) {
        model.back()
    }
    val palette = GamePalettes.find(model.paletteId)
    CompositionLocalProvider(LocalGamePalette provides palette) {
    MaterialTheme(colorScheme = palette.scheme(), typography = MaterialTheme.typography) {
    Surface(color = palette.background, contentColor = palette.text, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            key(model.screen) {
            if (model.screen == GameScreen.MENU) MainMenu(model, onExit)
            else if (model.screen == GameScreen.CONTACTS) ContactsScreen(model)
            else if (model.screen == GameScreen.PRIVACY) PrivacyScreen(model)
            else if (model.screen == GameScreen.LEGAL) LegalScreen()
            else if (model.screen == GameScreen.HELP) HelpScreen(model)
            else if (model.screen == GameScreen.SETTINGS) SettingsScreen(model)
            else if (model.screen == GameScreen.VICTORY) VictoryScreen(model)
            else if (model.screen == GameScreen.RECORD) RecordScreen(model)
            else if (model.screen in listOf(GameScreen.GAME_OVER, GameScreen.RESULTS)) ResultsScreen(model)
            else model.game?.let { GameContent(model, it) }
            }
        }
    }
}

    }
}

/** Compact menu action with its label, accent color, test tag, and click handler. */
private data class MenuAction(val label: String, val color: Color, val tag: String, val action: () -> Unit)

/** Rectangular buttons and logo share available height without menu scrolling. */
@Composable
private fun MainMenu(model: GameViewModel, onExit: () -> Unit) {
    var menuOrigin by remember { mutableStateOf(Offset.Zero) }
    var logoBounds by remember { mutableStateOf(Rect.Zero) }
    val intro = model.launchIntroPending
    val palette = LocalGamePalette.current
    val actions = buildList {
        add(MenuAction("Новая игра", palette.piece(Tetromino.I), "newGame", model::newGame))
        if (model.game?.gameOver == false) add(MenuAction("Продолжить", palette.piece(Tetromino.S), "resumeGame", model::resume))
        add(MenuAction("Настройки", palette.piece(Tetromino.T), "settings", model::settings))
        add(MenuAction("Как играть", palette.piece(Tetromino.J), "help", model::help))
        add(MenuAction("Результаты", palette.piece(Tetromino.O), "results", model::showResults))
        add(MenuAction("Контакты", palette.piece(Tetromino.L), "contacts", model::contacts))
        add(MenuAction("Выход", palette.piece(Tetromino.Z), "exitGame", onExit))
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { menuOrigin = it.positionInRoot() }) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
        val rows = actions.chunked(2)
        val gap = 8.dp
        val tileHeight = minOf(64.dp, (maxHeight * .55f - gap * (rows.size - 1)) / rows.size)
        val logoHeight = minOf(220.dp, maxHeight - tileHeight * rows.size - gap * rows.size)
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().testTag("mainMenu"),
            verticalArrangement = Arrangement.spacedBy(gap)) {
            Box(Modifier.fillMaxWidth().height(logoHeight), contentAlignment = Alignment.Center) {
                GameTitle(Modifier.offset(y = (-16).dp).onGloballyPositioned {
                    logoBounds = Rect(it.positionInRoot() - menuOrigin, Size(it.size.width.toFloat(), it.size.height.toFloat()))
                }.graphicsLayer { alpha = if (model.launchLogoAssembled) 1f else 0f }
                    .then(if (intro) Modifier.clearAndSetSemantics {} else Modifier))
            }
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().height(tileHeight).graphicsLayer {
                    val buttonsAlpha = LaunchIntroMotion.buttonsAlpha(model.launchIntroMillis)
                    alpha = buttonsAlpha
                    translationY = 12.dp.toPx() * (1f - buttonsAlpha)
                }.then(if (intro) Modifier.clearAndSetSemantics {} else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { item ->
                        Box(Modifier.weight(1f)) {
                            MenuTile(item.label, item.color, item.tag, enabled = !intro, onClick = item.action)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    if (intro) LaunchIntroOverlay(model, logoBounds, Modifier.matchParentSize())
    }
}

/** Separate screen for persisted settings; returning does not automatically resume gameplay. */
@Composable
private fun SettingsScreen(model: GameViewModel) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("Сложность", style = MaterialTheme.typography.titleLarge)
        Difficulty.entries.forEach { difficulty ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = model.difficulty == difficulty,
                    onClick = { model.chooseDifficulty(difficulty) }, modifier = Modifier.testTag("difficulty_${difficulty.id}"))
                Text(difficulty.title)
            }
        }
        Text("Применяется к новой партии", color = LocalGamePalette.current.muted)
        Spacer(Modifier.height(16.dp))
        SettingToggle("Подсказки", "hints", model.hintsEnabled, model::setHints)
        Text("Тень падения и следующая фигура", color = LocalGamePalette.current.muted)
        SettingToggle("Звук", "sound", model.soundEnabled, model::setSound)
        MusicPicker(model)
        SettingToggle("Вибрация", "vibration", model.vibrationEnabled, model::setVibration)
        Spacer(Modifier.height(24.dp))
        PalettePicker(model)
        Spacer(Modifier.height(24.dp))
    }
}

/** Labeled toggle for one setting, with a stable UI-test tag. */
@Composable
private fun SettingToggle(label: String, tag: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LocalGamePalette.current.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
/** Compact rectangular button whose label adapts to increased font size. */
@Composable
internal fun MenuTile(label: String, accent: Color, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(64.dp).testTag(tag),
        shape = RoundedCornerShape(12.dp),
        colors = paletteButtonColors(accent, ActionStyle.SECONDARY),
        border = paletteButtonBorder(accent, ActionStyle.SECONDARY),
        contentPadding = PaddingValues(8.dp)
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val scale = LocalDensity.current.fontScale
            val font = minOf(20f, maxWidth.value / (label.length * 0.62f) / scale,
                maxHeight.value / 1.4f / scale).coerceAtLeast(10f)
            Text(label, fontSize = font.sp, maxLines = 1, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
/** The board fills the screen; its HUD overlay does not intercept touches. */
@Composable
private fun GameContent(model: GameViewModel, state: GameState) {
    val density = LocalDensity.current.density
    val playing = model.screen == GameScreen.PLAYING
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
    Box(Modifier.fillMaxSize().onSizeChanged { model.setBoardWidth(it.width / density) }.testTag("gameArea").pointerInput(model, playing, density) {
        if (!playing) return@pointerInput
        try {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false)
                val id = first.id
                model.pointerDown(first.position.x / density, first.position.y / density, first.uptimeMillis)
                first.consume()
                var canceled = false
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.id != id && it.pressed }) {
                        canceled = true
                        model.cancelGesture()
                    }
                    val change = event.changes.firstOrNull { it.id == id }
                    if (!canceled && change != null) {
                        if (change.pressed) model.pointerMove(change.position.x / density, change.position.y / density, change.uptimeMillis)
                        else model.pointerUp(change.position.x / density, change.position.y / density, change.uptimeMillis)
                    }
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        } finally {
            model.cancelGesture()
        }
    }) {
        val hints = model.hintsEnabled
        val landing = remember(state.board, state.active, state.clearingRows, hints) {
            if (hints && state.clearingRows.isEmpty()) model.engine.ghost(state) else null
        }
        Board(state, landingHint = landing, showNext = hints, clearTime = { model.clearElapsedMillis })
        GameHud(state, showNext = model.hintsEnabled)
    }
    }
}

/** Compact level and score HUD; frequent drops do not restart its score pulse. */
@Composable
internal fun GameHud(state: GameState, showNext: Boolean = true) {
    var scoreSize by remember { mutableStateOf(Size.Zero) }
    val displayed = GameRules.displayScore(state.score)
    val currentDisplayed by rememberUpdatedState(displayed)
    val nearing by rememberUpdatedState(GameRules.nearingLevel(state.score))
    val scale = remember { Animatable(1f) }
    val color by animateColorAsState(
        if (GameRules.nearingLevel(state.score)) LocalGamePalette.current.gold else LocalGamePalette.current.text,
        tween(220), label = "scoreColor")
    LaunchedEffect(Unit) {
        var pulsing = false
        snapshotFlow { currentDisplayed }.drop(1).collect {
            if (nearing && !pulsing) {
                pulsing = true
                launch {
                    try {
                        scale.animateTo(1.08f, tween(110))
                        scale.animateTo(1f, tween(110))
                    } finally { pulsing = false }
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val textSize = maxOf(12f, with(density) { (maxHeight / 40).toSp() }.value).sp
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontSize = textSize * 1.25f)) { append("${state.level}") }
                append(" | $displayed")
            }, color = color, maxLines = 1,
                style = TextStyle(fontSize = textSize, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier.onSizeChanged { scoreSize = Size(it.width.toFloat(), it.height.toFloat()) }.graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    scaleX = scale.value; scaleY = scale.value
                }
                    .semantics { this[ScorePulseScale] = scale.value }
                    .testTag("score"))
        }
        if (state.roundFruits > 0) {
            val layout = fruitPlacement(maxWidth.value, maxHeight.value, state.roundFruits, state.next, showNext,
                with(density) { scoreSize.width.toDp().value }, with(density) { scoreSize.height.toDp().value })
            Column(Modifier.offset(layout.left.dp, layout.top.dp).testTag("earnedFruits"),
                verticalArrangement = Arrangement.spacedBy(FRUIT_GAP.dp)) {
                Fruit.entries.take(state.roundFruits).chunked(layout.columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(FRUIT_GAP.dp)) {
                        row.forEach { fruit ->
                            FruitIcon(fruit, Modifier.size(FRUIT_SIZE.dp).testTag("earnedFruit_${fruit.name}"))
                        }
                    }
                }
            }
        }
    }
}
/** Draws the board, faint next-piece preview, and optional landing ghost while omitting removed cells. */
@Composable
internal fun Board(state: GameState, clearElapsedMillis: Long = 0L, landingHint: Piece? = null,
    showNext: Boolean = true, clearTime: () -> Long = { clearElapsedMillis }) {
    val palette = LocalGamePalette.current
    val previewCells = remember(state.next) { Piece(state.next).cells() }
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Игровое поле, очки ${state.score}, линии ${state.lines}." + if (showNext) " Следующая фигура ${state.next.name}" else "" }.testTag("board")) {
        val elapsed = if (state.clearingRows.isNotEmpty()) clearTime() else 0L
        val cell = Size(size.width / 10, size.height / 20)
        val origin = Offset.Zero
        drawRect(palette.panel, origin, size)
        for (x in 0..10) drawLine(palette.grid, Offset(x * cell.width, 0f), Offset(x * cell.width, size.height))
        for (y in 0..20) drawLine(palette.grid, Offset(0f, y * cell.height), Offset(size.width, y * cell.height))
        if (showNext) previewCells.forEach { block(it, palette.piece(state.next), origin, cell, alpha = 0.20f) }
        state.board.forEachIndexed { y, row -> row.forEachIndexed { x, type ->
            if (type != null && !(y in state.clearingRows && LineClearAnimation.isRemoved(x, elapsed, state.completedClears))) block(Cell(x, y), palette.piece(type), origin, cell)
        } }
        if (!state.gameOver && state.clearingRows.isEmpty()) {
            landingHint?.let { hint -> hint.cells().forEach { block(it, palette.piece(hint.type), origin, cell, alpha = 0.5f, outline = true) } }
            state.active.cells().forEach { block(it, palette.piece(state.active.type), origin, cell) }
        }
    }
}

/** Draws a colored cell with spacing using independent width and height. */
private fun DrawScope.block(cell: Cell, color: Color, origin: Offset, step: Size, alpha: Float = 1f, outline: Boolean = false) {
    val gap = minOf(step.width, step.height) * 0.07f
    val topLeft = origin + Offset(cell.x * step.width + gap, cell.y * step.height + gap)
    val blockSize = Size(step.width - gap * 2, step.height - gap * 2)
    bevelBlock(topLeft, blockSize, color, alpha, outline)
}
