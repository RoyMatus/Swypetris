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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.PlatformTextStyle
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
                    if (Build.VERSION.SDK_INT >= 29) {
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
        model.configureGestures(GestureConfig(
            tapSlop = configuration.scaledTouchSlop / density
        ))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { updateWindowActive = false; model.onBackground() }
            if (event == Lifecycle.Event.ON_RESUME) { updateWindowActive = true; model.onForeground() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            model.cancelGesture()
        }
    }
    LaunchIntroClock(model)
    LaunchedEffect(updateWindowActive, model.screen, updates?.automaticEnabled,
        updates?.delivery?.notice, updates?.notice) {
        if (updateWindowActive) updates?.installAutomaticallyIfReady(model)
    }
    BackHandler(enabled = model.launchIntroPending || model.screen != GameScreen.MENU) {
        model.back()
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
            if (model.screen == GameScreen.MENU) MainMenu(model, onExit, onCheckUpdates = { updates?.check(true) })
            else if (model.screen == GameScreen.CONTACTS) ContactsScreen(model)
            else if (model.screen == GameScreen.PRIVACY) PrivacyScreen(model)
            else if (model.screen == GameScreen.LEGAL) LegalScreen()
            else if (model.screen == GameScreen.HELP) HelpScreen(model)
            else if (model.screen == GameScreen.SETTINGS) SettingsScreen(model,
                onCheckUpdates = { updates?.check(true) }, automaticUpdates = updates?.automaticEnabled == true,
                onAutomaticUpdatesChange = { updates?.requestAutomatic(it) })
            else if (model.screen == GameScreen.VICTORY) VictoryScreen(model)
            else if (model.screen == GameScreen.RECORD) RecordScreen(model)
            else if (model.screen in listOf(GameScreen.GAME_OVER, GameScreen.RESULTS)) ResultsScreen(model)
            else model.game?.let { GameContent(model, it) }
            }
        }
    }
}

    if (model.screen in listOf(GameScreen.MENU, GameScreen.SETTINGS) && updates != null) {
    if (updates.consentRequested) AutomaticUpdateConsentDialog(updates)
    else UpdateDeliveryDialog(updates.delivery, model) { updates.check(true) }
    if (!updates.consentRequested && updates.delivery.notice == null) updates.notice?.let { notice ->
        AlertDialog(onDismissRequest = { if (notice != UpdateNotice.StoreInstalling) updates.dismiss() },
            title = { Text(if (notice is UpdateNotice.Available) "Доступно обновление" else "Проверка обновлений") },
            text = { Text(when (notice) {
                is UpdateNotice.Available -> "Установлена версия ${BuildConfig.VERSION_NAME}. Доступна ${notice.update.versionName}."
                is UpdateNotice.StoreReady -> "RuStore загрузил версию ${notice.update.versionName}. Установка сохранит партию и перезапустит приложение."
                UpdateNotice.StoreInstalling -> "Сохраняем партию. RuStore устанавливает обновление."
                UpdateNotice.Current -> "Установлена актуальная версия ${BuildConfig.VERSION_NAME}."
                UpdateNotice.Failed -> "Не удалось завершить обновление. Проверьте подключение и повторите проверку позже."
                is UpdateNotice.RateLimited -> "GitHub временно ограничил проверку обновлений. Повторить можно после " +
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.MEDIUM)
                        .format(java.util.Date(notice.retryAtMillis)) + " (время устройства). Игру можно продолжить."
            }) },
            confirmButton = {
                if (notice is UpdateNotice.Available) TextButton(onClick = { updates.open(notice.update) },
                    modifier = Modifier.testTag("confirmUpdate")) { Text("Обновить") }
                else if (notice is UpdateNotice.StoreReady) TextButton(onClick = { updates.installStore(model) }) { Text("Установить") }
                else if (notice != UpdateNotice.StoreInstalling) TextButton(onClick = updates::dismiss) { Text("Понятно") }
            },
            dismissButton = if (notice is UpdateNotice.Available || notice is UpdateNotice.StoreReady) ({
                TextButton(onClick = updates::dismiss, modifier = Modifier.testTag("laterUpdate")) { Text("Позже") }
            }) else null)
    }
    }

    }
}

/** Compact menu action with its label, accent color, test tag, and click handler. */
private data class MenuAction(val label: String, val color: Color, val tag: String,
    val icon: ImageVector, val action: () -> Unit)

/** Primary gameplay actions sit above compact navigation; the logo keeps the final intro position. */
@Composable
private fun MainMenu(model: GameViewModel, onExit: () -> Unit, onCheckUpdates: () -> Unit) {
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
    MenuTetrominoBackdrop(logoBounds, Modifier.matchParentSize())
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
        TextButton(onClick = onCheckUpdates, enabled = !intro,
            modifier = Modifier.align(Alignment.BottomStart).testTag("versionCheck")) {
            Text(BuildConfig.VERSION_NAME, color = palette.muted,
                style = MaterialTheme.typography.labelSmall)
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
/** Number of grid rows reserved above the visible playfield for spawning pieces. */
internal const val SPAWN_DISPLAY_ROWS = 2
private const val GAME_GRID_ROWS = BoardGeometry.VISIBLE_ROWS + SPAWN_DISPLAY_ROWS

/** Draws the grid across the entire gameplay surface without outer gutters. */
@Composable
private fun GameGridBackground(geometry: GameplayGeometry) {
    val palette = LocalGamePalette.current
    Canvas(Modifier.fillMaxSize().testTag("gridBackground")) {
        for (column in 0..BoardGeometry.WIDTH) {
            val x = column * size.width / BoardGeometry.WIDTH
            drawLine(palette.grid, Offset(x, 0f), Offset(x, size.height))
        }
        // Join the status area to the first complete row instead of drawing a cropped strip.
        drawLine(palette.grid, Offset.Zero, Offset(size.width, 0f))
        for (row in 2..GAME_GRID_ROWS) {
            val y = geometry.gridTop + row * geometry.cellHeight
            drawLine(palette.grid, Offset(0f, y), Offset(size.width, y))
        }
    }
}

/** Grid extends under system icons; foreground and gestures stay below their safe boundary. */
@Composable
internal fun GameContent(model: GameViewModel, state: GameState, topInset: Dp? = null) {
    val localDensity = LocalDensity.current
    val density = localDensity.density
    val playing = model.screen == GameScreen.PLAYING
    val safeTop = topInset ?: with(localDensity) {
        WindowInsets.statusBars.union(WindowInsets.displayCutout).getTop(this).toDp()
    }
    val palette = LocalGamePalette.current
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.glass,
        lerp(palette.glass, palette.background, if (palette.light) .08f else .35f))))
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
        val hints = model.hintsEnabled
        val landing = remember(state.board, state.active, state.clearingRows, hints) {
            if (hints && state.clearingRows.isEmpty()) model.engine.ghost(state) else null
        }
        Box(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val boardWidth = maxWidth
                val geometry = gameplayGeometry(with(localDensity) { maxHeight.toPx() },
                    with(localDensity) { safeTop.toPx() })
                val spawnBandHeight = with(localDensity) { (geometry.cellHeight * SPAWN_DISPLAY_ROWS).toDp() }
                LaunchedEffect(boardWidth) { model.setBoardWidth(boardWidth.value) }

                GameGridBackground(geometry)
                Box(Modifier.fillMaxSize()) {
                    Board(state, landingHint = landing, clearTime = { model.clearElapsedMillis }, geometry = geometry,
                        drawActive = false)
                    Box(Modifier.offset(y = safeTop).fillMaxWidth().height(spawnBandHeight)
                        .semantics { contentDescription = "Следующая фигура ${state.next.name}" }
                        .testTag("nextPreview"))
                }
                Box(Modifier.fillMaxSize().padding(top = safeTop)
                    .consumeWindowInsets(PaddingValues(top = safeTop))) {
                    GameHud(state, spawnBandHeight,
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                        with(localDensity) { WindowInsets.safeDrawing.getBottom(this).toDp() }) {
                        ActivePiece(state, geometry.copy(safeTop = 0f))
                    }
                }
            }
        }

        // Use the same content-local coordinates for drawing and gameplay gestures.
        Box(Modifier.fillMaxSize().padding(top = safeTop)
            .testTag("gameArea").pointerInput(model, playing, density) {
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
        }) {}
    }
}

/** Fits one HUD line without clipping its text at large font scales or long scores. */
@Composable
private fun fittedHudFont(text: String, preferred: TextUnit, width: Dp, height: Dp? = null): TextUnit {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = preferred,
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
            repeat(12) {
                val middle = (low + high) / 2
                if (fits(middle.sp)) low = middle else high = middle
            }
            low.sp
        }
    }
}

/** The score and whole fruit pairs use the left lane; Hold keeps its compact right lane. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GameHud(state: GameState, headerHeight: Dp = 48.dp,
    horizontalInsets: PaddingValues = PaddingValues(0.dp), bottomInset: Dp = 0.dp,
    pieceOverlay: @Composable () -> Unit = {}) {
    val displayed = GameRules.displayScore(state.score)
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
                        scale.animateTo(SCORE_MAX_SCALE, tween(110))
                        scale.animateTo(1f, tween(110))
                    } finally { pulsing = false }
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val leftMargin = GAMEPLAY_HUD_MARGIN.dp + horizontalInsets.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
        val rightMargin = GAMEPLAY_HUD_MARGIN.dp + horizontalInsets.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
        val sideWidth = (maxWidth * .3f - rightMargin - 4.dp).coerceAtLeast(1.dp)
        val hintLeft = maxWidth / BoardGeometry.WIDTH * spawnPiece(state.next).cells().minOf { it.x }
        val leftWidth = (hintLeft - leftMargin - 4.dp).coerceAtLeast(1.dp)
        val holdWidth = minOf((maxWidth * .22f).coerceIn(48.dp, 88.dp), sideWidth)
        val holdHeight = minOf((headerHeight * .7f).coerceIn(28.dp, 48.dp), headerHeight)
        // The widest spawn occupies columns 3 through 6; keep the score left of that lane.
        val scoreTop = minOf(maxWidth / BoardGeometry.WIDTH, headerHeight / SPAWN_DISPLAY_ROWS) * .07f
        val scoreHeight = minOf(((maxHeight - bottomInset) / 32).coerceIn(16.dp, 28.dp) * density.fontScale,
            leftWidth / digitalScoreAspect(displayed.length) / SCORE_MAX_SCALE,
            (headerHeight - scoreTop).coerceAtLeast(1.dp) / SCORE_MAX_SCALE)

        EarnedFruits(state, leftWidth, Modifier.align(Alignment.TopStart)
                .offset(x = leftMargin, y = scoreTop + scoreHeight * SCORE_MAX_SCALE + FRUIT_GAP.dp)
                .width(leftWidth).testTag("earnedFruits"))

        // Only the falling piece crosses over fruit pixels; score and Hold stay in front.
        pieceOverlay()
        DigitalScore(displayed, scoreHeight,
                modifier = Modifier.align(Alignment.TopStart)
                .offset(x = leftMargin, y = scoreTop)
                .graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    scaleX = scale.value; scaleY = scale.value
                }
                .semantics {
                    this[ScorePulseScale] = scale.value
                    contentDescription = "Очки $displayed"
                }
                .testTag("score"))

        Column(
            modifier = Modifier.align(Alignment.TopEnd)
                .offset(y = scoreTop)
                .padding(end = rightMargin)
                .width(holdWidth),
            horizontalAlignment = Alignment.End
        ) {
            HoldPreview(state.held, state.holdUsed,
                Modifier.width(holdWidth).height(holdHeight).testTag("holdPreview"))
            val labelFont = fittedHudFont("Запас", 10.sp, holdWidth)
            Text("Запас", color = LocalGamePalette.current.muted, maxLines = 1, softWrap = false,
                textAlign = TextAlign.Center,
                style = TextStyle(fontSize = labelFont, lineHeight = labelFont * 1.2f, letterSpacing = 0.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier.fillMaxWidth().testTag("holdLabel"))
        }
    }
}

/** Flow measures each quantity and icon together, so pairs cannot split or enter the hint lane. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EarnedFruits(state: GameState, width: Dp, modifier: Modifier) {
    val fruits = Fruit.entries.zip(state.fruitCounts).filter { it.second > 0 }
    if (fruits.isEmpty()) return
    val iconSize = minOf(FRUIT_SIZE.dp, width)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(FRUIT_GAP.dp),
        verticalArrangement = Arrangement.spacedBy(FRUIT_GAP.dp)) {
        fruits.forEach { (fruit, count) ->
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.testTag("earnedFruit_${fruit.name}")) {
                if (count > 1) {
                    val label = "$count ×"
                    val font = fittedHudFont(label, 10.sp, (width - iconSize - 2.dp).coerceAtLeast(1.dp))
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
        val columns = cells.maxOf { it.x } + 1
        val firstRow = cells.minOf { it.y }
        val rows = cells.maxOf { it.y } - firstRow + 1
        val padding = 4.dp.toPx()
        val step = minOf((size.width - padding * 2) / columns, (size.height - padding * 2) / rows)
        // Keep the existing cell size and horizontal centering; block() adds its own gap.
        // Cancel that gap vertically so the actual painted top equals the score's top.
        val origin = Offset((size.width - columns * step) / 2, -step * .07f)
        clipRect {
            cells.forEach {
                block(Cell(it.x, it.y - firstRow), palette.piece(held), palette.finish,
                    palette.texture, origin, Size(step, step), alpha = if (used) .055f else .16f)
            }
        }
    }
}

/** Draws the logical board, spawn preview, active piece, and optional landing ghost. */
@Composable
internal fun Board(state: GameState, clearElapsedMillis: Long = 0L, landingHint: Piece? = null,
    clearTime: () -> Long = { clearElapsedMillis }, geometry: GameplayGeometry? = null, drawActive: Boolean = true,
    reducedMotion: Boolean = Build.VERSION.SDK_INT >= 26 && !android.animation.ValueAnimator.areAnimatorsEnabled()) {
    val palette = LocalGamePalette.current
    val shards = remember(state.board, state.clearingRows, state.completedClears) { LineClearAnimation.shards(state) }
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Игровое поле, очки ${state.score}, линии ${state.lines}." +
        " Следующая фигура ${state.next.name}" +
        " Запас: ${state.held?.name ?: "пусто"}, ${if (state.holdUsed) "обмен недоступен" else "обмен доступен"}." }.testTag("board")) {
        val elapsed = if (state.clearingRows.isNotEmpty()) clearTime() else 0L
        val cell = Size(size.width / BoardGeometry.WIDTH, geometry?.cellHeight ?: (size.height / GAME_GRID_ROWS))
        val origin = Offset(0f, (geometry?.gridTop ?: 0f) + SPAWN_DISPLAY_ROWS * cell.height)
        val firstVisibleSpawnRow = -SPAWN_DISPLAY_ROWS
        clipRect(top = geometry?.safeTop ?: 0f) {

            // The outline uses engine spawn cells; its faint flat fill cannot look like an active block.
            spawnPiece(state.next).cells()
                .filter { it.y in firstVisibleSpawnRow until BoardGeometry.VISIBLE_ROWS }
                .forEach {
                    val gap = minOf(cell.width, cell.height) * .07f
                    val at = origin + Offset(it.x * cell.width + gap, it.y * cell.height + gap)
                    val bounds = Size(cell.width - gap * 2, cell.height - gap * 2)
                    val previewColor = lerp(palette.piece(state.next), palette.text, if (palette.light) .35f else .1f)
                    drawRect(previewColor.copy(alpha = .0275f), at, bounds)
                    drawRect(previewColor.copy(alpha = if (palette.light) .35f else .30f), at, bounds,
                        style = Stroke(1.dp.toPx()))
                }

            state.board.forEachIndexed { rowIndex, row ->
                val logicalY = rowIndex - BoardGeometry.HIDDEN_ROWS
                val shift = LineClearAnimation.rowShift(rowIndex, state.clearingRows, elapsed, reducedMotion)
                if (logicalY + shift + 1 > firstVisibleSpawnRow &&
                    !(rowIndex in state.clearingRows && LineClearAnimation.isRemoved(elapsed))) {
                    row.forEachIndexed { x, type ->
                        if (type != null) block(Cell(x, logicalY), palette.piece(type), palette.finish,
                            palette.texture, origin + Offset(0f, shift * cell.height), cell)
                    }
                }
            }
            if (state.clearingRows.isNotEmpty()) clipRect(top = origin.y) {
                lineClearEffects(shards, state.clearingRows, palette, origin, cell, elapsed, reducedMotion)
            }

            if (!state.gameOver && state.clearingRows.isEmpty()) {
                landingHint?.let { hint ->
                    hint.cells().filter { it.y in 0 until BoardGeometry.VISIBLE_ROWS }.forEach {
                        block(it, palette.piece(hint.type), palette.finish, palette.texture, origin, cell,
                            alpha = palette.ghostAlpha, outline = true)
                    }
                }
                if (drawActive) fallingPiece(state, palette, origin, cell)
            }
        }
    }
}

/** Dedicated falling-piece layer between fruits and the other HUD indicators. */
@Composable
private fun ActivePiece(state: GameState, geometry: GameplayGeometry) {
    val palette = LocalGamePalette.current
    Canvas(Modifier.fillMaxSize().testTag("activePiece")) {
        val cell = Size(size.width / BoardGeometry.WIDTH, geometry.cellHeight)
        val origin = Offset(0f, geometry.gridTop + SPAWN_DISPLAY_ROWS * cell.height)
        clipRect(top = geometry.safeTop) { fallingPiece(state, palette, origin, cell) }
    }
}

private fun DrawScope.fallingPiece(state: GameState, palette: GamePalette, origin: Offset, cell: Size) {
    if (!state.gameOver && state.clearingRows.isEmpty()) {
        state.active.cells().filter { it.y in -SPAWN_DISPLAY_ROWS until BoardGeometry.VISIBLE_ROWS }.forEach {
            block(it, palette.piece(state.active.type), palette.finish, palette.texture, origin, cell)
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
