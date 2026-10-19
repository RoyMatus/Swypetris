package ru.itoltec.swypetris

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlin.math.abs

private const val MENU_COLUMN_FRACTION = .5f
private const val MENU_THEME_SWIPE_DISTANCE_DP = 48
private const val MENU_THEME_HORIZONTAL_BIAS = 1.5f

/** Primary gameplay actions sit above compact navigation; the logo keeps the final intro position. */
@Composable
internal fun MainMenu(model: GameViewModel, onExit: () -> Unit, onCheckUpdates: () -> Unit) {
    var menuOrigin by remember { mutableStateOf(Offset.Zero) }
    var logoBounds by remember { mutableStateOf(Rect.Zero) }
    val intro = model.launchIntroPending
    val palette = menuUiPalette()
    val primary = primaryMenuActions(model, palette)
    val secondary = secondaryMenuActions(model, palette)
    Box(Modifier.fillMaxSize().testTag("mainMenuSurface").menuThemeSwipes(model, intro)
        .onGloballyPositioned { menuOrigin = it.positionInRoot() }) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            contentAlignment = Alignment.BottomCenter) {
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
                MainMenuLogo(model, intro, logoHeight, menuOrigin) { logoBounds = it }
                PrimaryMenuActions(primary, primaryHeight, gap, intro, reveal)
                secondary.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(secondaryHeight).then(reveal),
                        horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { item ->
                            Box(Modifier.weight(1f)) {
                                MenuTile(item.label, item.color, item.tag, item.icon, ActionStyle.SECONDARY,
                                    secondaryHeight,
                                    enabled = !intro, onClick = item.action)
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth(MENU_COLUMN_FRACTION).then(reveal)) {
                    MenuTile("Выход", palette.piece(Tetromino.Z), "exitGame",
                        Icons.Outlined.ExitToApp, ActionStyle.SECONDARY, exitHeight,
                        enabled = !intro, destructive = true, onClick = onExit)
                }
            }
            MenuTextButton(onClick = onCheckUpdates, enabled = !intro, role = "link",
                modifier = Modifier.align(Alignment.BottomStart).heightIn(min = 48.dp).testTag("versionCheck")) {
                Text(BuildConfig.VERSION_NAME,
                    color = if (LocalMenuSkin.current == null) palette.muted else Color.Unspecified,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        if (intro) LaunchIntroOverlay(model, logoBounds, Modifier.matchParentSize())
    }
}

/** Child controls consume their down event first; only a complete free-area swipe selects a theme. */
private fun Modifier.menuThemeSwipes(model: GameViewModel, intro: Boolean): Modifier = pointerInput(model, intro) {
    if (intro) return@pointerInput
    val threshold = maxOf(viewConfiguration.touchSlop, MENU_THEME_SWIPE_DISTANCE_DP.dp.toPx())
    awaitEachGesture {
        readMenuThemeSwipe(threshold)?.let(model.options::cyclePalette)
    }
}

private suspend fun AwaitPointerEventScope.readMenuThemeSwipe(threshold: Float): Boolean? {
    val first = awaitFirstDown(requireUnconsumed = false)
    if (first.isConsumed) return null
    return completeMenuThemeSwipe(first, threshold)
}

private suspend fun AwaitPointerEventScope.completeMenuThemeSwipe(first: PointerInputChange,
    threshold: Float): Boolean? {
    while (true) {
        val change = awaitPointerEvent().changes.singleOrNull()
            ?.takeIf { it.isValidMenuSwipe(first, threshold) } ?: break
        val distance = change.position - first.position
        val direction = distance.menuThemeDirection(threshold)
        if (direction != null) change.consume()
        if (!change.pressed) return direction
    }
    return null
}

private fun PointerInputChange.isValidMenuSwipe(first: PointerInputChange, threshold: Float): Boolean {
    val distance = position - first.position
    val vertical = abs(distance.y) > threshold && abs(distance.y) >= abs(distance.x)
    return id == first.id && !isConsumed && !vertical
}

private fun Offset.menuThemeDirection(threshold: Float): Boolean? =
    if (abs(x) > threshold && abs(x) > abs(y) * MENU_THEME_HORIZONTAL_BIAS) x < 0 else null

/** Compact rectangular button whose label adapts to increased font size. */
@Composable
internal fun MenuTile(label: String, accent: Color, tag: String,
    icon: ImageVector,
    style: ActionStyle = ActionStyle.SECONDARY, height: Dp = 64.dp,
    enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    val skin = LocalMenuSkin.current
    val interaction = remember { MutableInteractionSource() }
    val role = when (tag) {
        "newGame" -> "primary"
        "resumeGame" -> "resume"
        "exitGame" -> "destructive"
        else -> tag
    }
    MenuNativeControl { OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(height).testTag(tag)
            .menuActionFrame(role, interaction, enabled),
        interactionSource = interaction,
        shape = RoundedCornerShape(12.dp),
        colors = if (skin != null) ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent, contentColor = menuActionTextColor(role),
            disabledContainerColor = Color.Transparent,
            disabledContentColor = menuActionTextColor(role, false))
        else if (destructive) ButtonDefaults.outlinedButtonColors(
            containerColor = LocalGamePalette.current.background.copy(alpha = .8f), contentColor = accent)
        else paletteButtonColors(accent, style),
        border = if (skin != null) null else paletteButtonBorder(accent, style),
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
                Text(label, fontSize = font.sp, lineHeight = (font * 1.2f).sp,
                    maxLines = 1, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center)
            }
        }
    }
    }
}

private fun primaryMenuActions(model: GameViewModel, palette: GamePalette): List<MenuAction> {
    return buildList {
        add(MenuAction("Новая игра", palette.piece(Tetromino.I), "newGame",
            Icons.Outlined.PlayArrow, model::newGame))
        if (model.game?.gameOver == false) add(MenuAction("Продолжить", palette.piece(Tetromino.S),
            "resumeGame", Icons.Outlined.PlayArrow, model::resume))
    }
}

private fun secondaryMenuActions(model: GameViewModel, palette: GamePalette): List<MenuAction> {
    return listOf(
        MenuAction("Настройки", palette.piece(Tetromino.T), "settings", Icons.Outlined.Settings,
            model.navigation::settings),
        MenuAction("Как играть", palette.piece(Tetromino.J), "help", Icons.Outlined.MenuBook, model.navigation::help),
        MenuAction("Результаты", palette.piece(Tetromino.O), "results", Icons.Outlined.EmojiEvents,
            model.navigation::showResults),
        MenuAction("Контакты", palette.piece(Tetromino.L), "contacts", Icons.Outlined.MailOutline,
            model.navigation::contacts)
    )
}

@Composable
private fun MainMenuLogo(model: GameViewModel, intro: Boolean, logoHeight: Dp, menuOrigin: Offset,
    onLogoBounds: (Rect) -> Unit) {
    Box(Modifier.fillMaxWidth().height(logoHeight), contentAlignment = Alignment.Center) {
        GameTitle(Modifier.onGloballyPositioned {
            onLogoBounds(Rect(it.positionInRoot() - menuOrigin, Size(it.size.width.toFloat(),
                it.size.height.toFloat())))
        }.graphicsLayer { alpha = if (model.launchLogoAssembled) 1f else 0f }
            .then(if (intro) Modifier.clearAndSetSemantics {} else Modifier),
            wordmarkOnly = true, heightLimit = logoHeight)
    }
}

@Composable
private fun PrimaryMenuActions(actions: List<MenuAction>, height: Dp, gap: Dp, intro: Boolean, reveal: Modifier) {
    Row(Modifier.fillMaxWidth().height(height).then(reveal),
        horizontalArrangement = Arrangement.spacedBy(gap)) {
        actions.forEach { item ->
            Box(Modifier.weight(1f)) {
                MenuTile(item.label, item.color, item.tag, item.icon, ActionStyle.PRIMARY, height,
                    enabled = !intro, onClick = item.action)
            }
        }
    }
}
