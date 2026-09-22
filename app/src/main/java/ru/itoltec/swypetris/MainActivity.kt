package ru.itoltec.swypetris

import android.os.Bundle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.BorderStroke
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

/** Текущий масштаб счёта для проверки одиночного импульса управляемыми часами Compose. */
internal val ScorePulseScale = androidx.compose.ui.semantics.SemanticsPropertyKey<Float>("ScorePulseScale")

/** Точка входа Android: подключает сохраняемую модель партии и Compose-интерфейс. */
class MainActivity : ComponentActivity() {
    private val gameModel: GameViewModel by viewModels()

    /** Создаёт полноэкранное поле и скрывает системные панели только на игровом экране. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwypetrisTheme(darkTheme = true, dynamicColor = false) {
                val immersive = gameModel.screen in listOf(GameScreen.PLAYING, GameScreen.PAUSED)
                DisposableEffect(immersive, gameModel.paletteId) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = GamePalettes.find(gameModel.paletteId).light
                    controller.isAppearanceLightNavigationBars = GamePalettes.find(gameModel.paletteId).light
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (immersive) controller.hide(WindowInsetsCompat.Type.systemBars())
                    else controller.show(WindowInsetsCompat.Type.systemBars())
                    onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
                }
                SwypetrisApp(gameModel, onExit = ::finishAndRemoveTask)
            }
        }
    }
}

/** Соединяет жизненный цикл Android, системные настройки жестов и выбор экрана. */
@Composable
fun SwypetrisApp(model: GameViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(model, density, owner) {
        val configuration = ViewConfiguration.get(context)
        model.configureGestures(GestureConfig(
            tapSlop = configuration.scaledTouchSlop / density,
            longPressMillis = ViewConfiguration.getLongPressTimeout().toLong()
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

/** Действие компактного меню: подпись, цвет, идентификатор и обработчик нажатия. */
private data class MenuAction(val label: String, val color: Color, val tag: String, val action: () -> Unit)

/** Прямоугольные кнопки и логотип делят доступную высоту, меню не требует прокрутки. */
@Composable
private fun MainMenu(model: GameViewModel, onExit: () -> Unit) {
    var menuOrigin by remember { mutableStateOf(Offset.Zero) }
    var logoBounds by remember { mutableStateOf(Rect.Zero) }
    val intro = model.launchIntroPending
    val actions = buildList {
        add(MenuAction("Новая игра", LocalGamePalette.current.accent, "newGame", model::newGame))
        if (model.game?.gameOver == false) add(MenuAction("Продолжить", LocalGamePalette.current.accent, "resumeGame", model::resume))
        add(MenuAction("Настройки", LocalGamePalette.current.secondary, "settings", model::settings))
        add(MenuAction("Как играть", LocalGamePalette.current.accent, "help", model::help))
        add(MenuAction("Результаты", LocalGamePalette.current.gold, "results", model::showResults))
        add(MenuAction("Контакты", LocalGamePalette.current.accent, "contacts", model::contacts))
        add(MenuAction("Выход", LocalGamePalette.current.secondary, "exitGame", onExit))
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
                GameTitle(Modifier.onGloballyPositioned {
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

/** Отдельный экран сохраняемых настроек; возврат не возобновляет партию автоматически. */
@Composable
private fun SettingsScreen(model: GameViewModel) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        SettingToggle("Подсказки", "hints", model.hintsEnabled, model::setHints)
        Text("Тень падения и следующая фигура", color = LocalGamePalette.current.muted)
        SettingToggle("Звук", "sound", model.soundEnabled, model::setSound)
        MusicPicker(model)
        SettingToggle("Вибрация", "vibration", model.vibrationEnabled, model::setVibration)
        Spacer(Modifier.height(24.dp))
        PalettePicker(model)
        Spacer(Modifier.height(24.dp))
        Button(onClick = model::menu) { Text("Назад") }
    }
}

/** Подписанный переключатель отдельной настройки, доступный для автоматической проверки. */
@Composable
private fun SettingToggle(label: String, tag: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LocalGamePalette.current.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
/** Невысокая прямоугольная кнопка с подписью, адаптированной к увеличенному шрифту. */
@Composable
internal fun MenuTile(label: String, accent: Color, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(64.dp).testTag(tag),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.13f), contentColor = accent,
            disabledContainerColor = accent.copy(alpha = 0.13f), disabledContentColor = accent),
        border = BorderStroke(2.dp, accent.copy(alpha = 0.75f)),
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
/** Поле заполняет экран; информационная панель поверх него не перехватывает касания. */
@Composable
private fun GameContent(model: GameViewModel, state: GameState) {
    val density = LocalDensity.current.density
    val playing = model.screen == GameScreen.PLAYING
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
        Board(state, model.clearElapsedMillis, if (model.hintsEnabled && state.clearingRows.isEmpty()) model.engine.ghost(state) else null, showNext = model.hintsEnabled)
        GameHud(state)
        when (model.screen) {
            GameScreen.PAUSED -> GameDialog("Пауза", "Партия ждёт продолжения", model, true)
            else -> Unit
        }
    }
}

/** Компактная панель уровня и очков; импульсы не перезапускаются при частом спуске. */
@Composable
internal fun GameHud(state: GameState) {
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
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val density = LocalDensity.current
        val textSize = maxOf(12f, with(density) { (maxHeight / 40).toSp() }.value).sp
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontSize = textSize * 1.25f)) { append("${state.level}") }
                append(" | $displayed")
            }, color = color, maxLines = 1,
                style = TextStyle(fontSize = textSize, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier.weight(1f).graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    scaleX = scale.value; scaleY = scale.value
                }
                    .semantics { this[ScorePulseScale] = scale.value }
                    .testTag("score"))
        }
        if (state.roundFruits > 0) {
            Column(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 2.dp).testTag("earnedFruits"),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Fruit.entries.take(state.roundFruits).forEach { fruit ->
                    FruitIcon(fruit, Modifier.size(20.dp).testTag("earnedFruit_${fruit.name}"))
                }
            }
        }
    }
}
/** Модальное меню блокирует поле и предлагает продолжить, начать заново или выйти. */
@Composable
private fun GameDialog(title: String, message: String, model: GameViewModel, canResume: Boolean) {
    AlertDialog(
        onDismissRequest = model::menu,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (canResume) TextButton(onClick = model::resume) { Text("Продолжить") }
                TextButton(onClick = model::newGame) { Text("Новая игра") }
                TextButton(onClick = model::menu) { Text("Главное меню") }
            }
        }
    )
}

/** Рисует поле, постоянное бледное превью и необязательную тень падения; удалённые клетки пропускает. */
@Composable
internal fun Board(state: GameState, clearElapsedMillis: Long = 0L, landingHint: Piece? = null, showNext: Boolean = true) {
    val palette = LocalGamePalette.current
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Игровое поле, очки ${state.score}, линии ${state.lines}." + if (showNext) " Следующая фигура ${state.next.name}" else "" }.testTag("board")) {
        val cell = Size(size.width / 10, size.height / 20)
        val origin = Offset.Zero
        drawRect(palette.panel, origin, size)
        for (x in 0..10) drawLine(palette.grid, Offset(x * cell.width, 0f), Offset(x * cell.width, size.height))
        for (y in 0..20) drawLine(palette.grid, Offset(0f, y * cell.height), Offset(size.width, y * cell.height))
        if (showNext) Piece(state.next).cells().forEach { block(it, palette.piece(state.next), origin, cell, alpha = 0.20f) }
        state.board.forEachIndexed { y, row -> row.forEachIndexed { x, type ->
            if (type != null && !(y in state.clearingRows && LineClearAnimation.isRemoved(x, clearElapsedMillis, state.completedClears))) block(Cell(x, y), palette.piece(type), origin, cell)
        } }
        if (!state.gameOver && state.clearingRows.isEmpty()) {
            landingHint?.let { hint -> hint.cells().forEach { block(it, palette.piece(hint.type), origin, cell, alpha = 0.5f, outline = true) } }
            state.active.cells().forEach { block(it, palette.piece(state.active.type), origin, cell) }
        }
    }
}

/** Рисует цветную клетку с зазором, используя независимую ширину и высоту. */
private fun DrawScope.block(cell: Cell, color: Color, origin: Offset, step: Size, alpha: Float = 1f, outline: Boolean = false) {
    val gap = minOf(step.width, step.height) * 0.07f
    val topLeft = origin + Offset(cell.x * step.width + gap, cell.y * step.height + gap)
    val blockSize = Size(step.width - gap * 2, step.height - gap * 2)
    bevelBlock(topLeft, blockSize, color, alpha, outline)
}






