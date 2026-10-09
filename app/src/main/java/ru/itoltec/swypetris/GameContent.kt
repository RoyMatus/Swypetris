package ru.itoltec.swypetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private const val LIGHT_GLASS_BLEND = .08f
private const val DARK_GLASS_BLEND = .35f

/** Number of grid rows reserved above the visible playfield for spawning pieces. */
internal const val SPAWN_DISPLAY_ROWS = 2
internal const val GAME_GRID_ROWS = BoardGeometry.VISIBLE_ROWS + SPAWN_DISPLAY_ROWS

/** Draws the grid across the entire gameplay surface without outer gutters. */
@Composable
internal fun GameGridBackground(geometry: GameplayGeometry) {
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
        lerp(palette.glass, palette.background, if (palette.light) LIGHT_GLASS_BLEND else DARK_GLASS_BLEND))))
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
                LaunchedEffect(boardWidth) { model.input.setBoardWidth(boardWidth.value) }

                if (model.background.enabled) BackgroundImageLayer(model.background.image, model.background.crop)
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
                        with(localDensity) { WindowInsets.safeDrawing.getBottom(this).toDp() },
                        scoreBackdrop = model.background.enabled && model.background.image != null) {
                        ActivePiece(state, geometry.copy(safeTop = 0f))
                    }
                }
            }
        }

        // Use the same content-local coordinates for drawing and gameplay gestures.
        Box(Modifier.fillMaxSize().padding(top = safeTop).testTag("gameArea")
            .gamePointerInput(model, playing, density)) {}
    }
}


private fun Modifier.gamePointerInput(model: GameViewModel, playing: Boolean, density: Float): Modifier =
    pointerInput(model, playing, density) {
        if (!playing) return@pointerInput
        try {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false)
                val id = first.id
                model.input.pointerDown(first.position.x / density, first.position.y / density, first.uptimeMillis)
                first.consume()
                var canceled = false
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.id != id && it.pressed }) {
                        canceled = true
                        model.input.cancelGesture()
                    }
                    val change = event.changes.firstOrNull { it.id == id }
                    if (!canceled && change != null) {
                        if (change.pressed) model.input.pointerMove(change.position.x / density,
                            change.position.y / density, change.uptimeMillis)
                        else model.input.pointerUp(change.position.x / density, change.position.y / density,
                            change.uptimeMillis)
                    }
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        } finally {
            model.input.cancelGesture()
        }
    }
