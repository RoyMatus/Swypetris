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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.unit.Dp
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
            if (model.screen == GameScreen.MENU) ThemeBackdrop(palette,
                LaunchIntroMotion.approachProgress(model.launchIntroMillis))
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
private data class MenuAction(val label: String, val color: Color, val tag: String,
    val icon: ImageVector, val action: () -> Unit)

/** Primary gameplay actions sit above compact navigation; the logo keeps the final intro position. */
@Composable
private fun MainMenu(model: GameViewModel, onExit: () -> Unit) {
    var menuOrigin by remember { mutableStateOf(Offset.Zero) }
    var logoBounds by remember { mutableStateOf(Rect.Zero) }
    val intro = model.launchIntroPending
    val palette = LocalGamePalette.current
    val primary = buildList {
        add(MenuAction("Новая игра", palette.piece(Tetromino.I), "newGame",
            Icons.Outlined.PlayArrow, model::newGame))
        if (model.game?.gameOver == false) add(MenuAction("Продолжить", palette.piece(Tetromino.S),
            "resumeGame", Icons.Outlined.PlayArrow, model::resume))
    }
    val secondary = listOf(
        MenuAction("Настройки", palette.piece(Tetromino.T), "settings", Icons.Outlined.Settings, model::settings),
        MenuAction("Как играть", palette.piece(Tetromino.J), "help", Icons.Outlined.MenuBook, model::help),
        MenuAction("Результаты", palette.piece(Tetromino.O), "results", Icons.Outlined.EmojiEvents, model::showResults),
        MenuAction("Контакты", palette.piece(Tetromino.L), "contacts", Icons.Outlined.MailOutline, model::contacts)
    )
    Box(Modifier.fillMaxSize().onGloballyPositioned { menuOrigin = it.positionInRoot() }) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        val gap = (maxHeight * .01f).coerceIn(3.dp, 8.dp)
        val primaryHeight = (maxHeight * .11f).coerceIn(48.dp, 64.dp)
        val secondaryHeight = (maxHeight * .10f).coerceIn(48.dp, 56.dp)
        val exitHeight = 48.dp
        val controlsHeight = primaryHeight + secondaryHeight * 2 + exitHeight + gap * 4
        val bottomSpace = maxHeight * .12f
        val logoHeight = minOf(maxHeight * .30f, 260.dp,
            maxHeight - controlsHeight - bottomSpace).coerceAtLeast(0.dp)
        val reveal = Modifier.graphicsLayer {
            val buttonsAlpha = LaunchIntroMotion.buttonsAlpha(model.launchIntroMillis)
            alpha = buttonsAlpha
            translationY = 12.dp.toPx() * (1f - buttonsAlpha)
        }.then(if (intro) Modifier.clearAndSetSemantics {} else Modifier)
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(bottom = bottomSpace).testTag("mainMenu"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap)) {
            Box(Modifier.fillMaxWidth().height(logoHeight), contentAlignment = Alignment.Center) {
                GameTitle(Modifier.onGloballyPositioned {
                    logoBounds = Rect(it.positionInRoot() - menuOrigin, Size(it.size.width.toFloat(), it.size.height.toFloat()))
                }.graphicsLayer { alpha = if (model.launchLogoAssembled) 1f else 0f }
                    .then(if (intro) Modifier.clearAndSetSemantics {} else Modifier),
                    wordmarkOnly = true, heightLimit = logoHeight)
            }
            Row(Modifier.fillMaxWidth().height(primaryHeight).then(reveal),
                horizontalArrangement = Arrangement.spacedBy(gap)) {
                primary.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        MenuTile(item.label, item.color, item.tag, item.icon, ActionStyle.PRIMARY, primaryHeight,
                            enabled = !intro, onClick = item.action)
                    }
                }
            }
            secondary.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().height(secondaryHeight).then(reveal),
                    horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { item ->
                        Box(Modifier.weight(1f)) {
                            MenuTile(item.label, item.color, item.tag, item.icon, ActionStyle.SECONDARY, secondaryHeight,
                                enabled = !intro, onClick = item.action)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth(.5f).then(reveal)) {
                MenuTile("Выход", palette.piece(Tetromino.Z), "exitGame",
                    Icons.Outlined.ExitToApp, ActionStyle.SECONDARY, exitHeight,
                    enabled = !intro, destructive = true, onClick = onExit)
            }
        }
    }
    if (intro) LaunchIntroOverlay(model, logoBounds, Modifier.matchParentSize())
    }
}

/** Compact rectangular button whose label adapts to increased font size. */
@Composable
internal fun MenuTile(label: String, accent: Color, tag: String,
    icon: ImageVector,
    style: ActionStyle = ActionStyle.SECONDARY, height: Dp = 64.dp,
    enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(height).testTag(tag),
        shape = RoundedCornerShape(12.dp),
        colors = if (destructive) ButtonDefaults.outlinedButtonColors(
            containerColor = LocalGamePalette.current.background.copy(alpha = .8f), contentColor = accent)
        else paletteButtonColors(accent, style),
        border = paletteButtonBorder(accent, style),
        contentPadding = PaddingValues(8.dp)
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val scale = LocalDensity.current.fontScale
            val showIcon = scale < 1.5f
            val iconSpace = if (showIcon) 32.dp else 0.dp
            val font = minOf(20f, (maxWidth - iconSpace).value / (label.length * 0.78f) / scale,
                maxHeight.value / 1.4f / scale).coerceAtLeast(if (style == ActionStyle.PRIMARY) 8f else 10f)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showIcon) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(label, fontSize = font.sp, maxLines = 1, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center)
            }
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
        drawRect(brush = Brush.verticalGradient(listOf(palette.glass,
            lerp(palette.glass, palette.background, if (palette.light) .08f else .35f))))
        if (palette.finish == BlockFinish.RETRO || palette.finish == BlockFinish.NEON) {
            for (y in 1 until 20) drawLine(palette.accent.copy(alpha = if (palette.light) .035f else .05f),
                Offset(0f, (y - .5f) * cell.height), Offset(size.width, (y - .5f) * cell.height))
        }
        for (x in 0..10) drawLine(palette.grid, Offset(x * cell.width, 0f), Offset(x * cell.width, size.height))
        for (y in 0..20) drawLine(palette.grid, Offset(0f, y * cell.height), Offset(size.width, y * cell.height))
        if (showNext) previewCells.forEach { block(it, palette.piece(state.next), palette.finish, palette.texture, origin, cell, alpha = 0.20f) }
        state.board.forEachIndexed { y, row -> row.forEachIndexed { x, type ->
            if (type != null && !(y in state.clearingRows && LineClearAnimation.isRemoved(x, elapsed, state.completedClears))) block(Cell(x, y), palette.piece(type), palette.finish, palette.texture, origin, cell)
        } }
        if (!state.gameOver && state.clearingRows.isEmpty()) {
            landingHint?.let { hint -> hint.cells().forEach { block(it, palette.piece(hint.type), palette.finish, palette.texture, origin, cell, alpha = palette.ghostAlpha, outline = true) } }
            state.active.cells().forEach { block(it, palette.piece(state.active.type), palette.finish, palette.texture, origin, cell) }
        }
    }
}

/** Draws a colored cell with spacing using independent width and height. */
private fun DrawScope.block(cell: Cell, color: Color, finish: BlockFinish, texture: BlockTexture, origin: Offset, step: Size,
    alpha: Float = 1f, outline: Boolean = false) {
    val gap = minOf(step.width, step.height) * 0.07f
    val topLeft = origin + Offset(cell.x * step.width + gap, cell.y * step.height + gap)
    val blockSize = Size(step.width - gap * 2, step.height - gap * 2)
    bevelBlock(topLeft, blockSize, color, finish, texture, alpha, outline)
}
