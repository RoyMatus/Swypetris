package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.bottom
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.height
import androidx.compose.ui.test.left
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.right
import androidx.compose.ui.test.top
import androidx.compose.ui.test.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NextQueueTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun previewAppearsAtSpawnWithoutVisibleLabel() {
        var state by mutableStateOf(GameState(active = Piece(Tetromino.O, x = 0, y = -1), next = Tetromino.I))
        compose.setContent { Box(Modifier.size(220.dp, 484.dp)) { Board(state); GameHud(state, 44.dp) } }
        fun previewPixel(): androidx.compose.ui.graphics.Color {
            val image = compose.onNodeWithTag("board").captureToImage().toPixelMap()
            return image[(image.width * .35f).toInt(), (image.height * 1.5f / 22).toInt()]
        }
        val immediate = previewPixel()
        val image = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        assertNotEquals("Preview must be visible at spawn", image[(image.width * .95f).toInt(),
            (image.height * 1.5f / 22).toInt()], immediate)
        compose.onNodeWithTag("nextLabel").assertDoesNotExist()
        for (y in listOf(0, 1, 8, -1)) {
            compose.runOnIdle { state = state.copy(active = state.active.copy(y = y)) }
            assertEquals("Descent must not change the spawn preview", immediate, previewPixel())
            compose.onNodeWithTag("nextLabel").assertDoesNotExist()
        }
    }

    /** Non-overlapping preview cells retain their faint fill for every shape and rotation. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun previewIsIndependentOfActiveShapeRotationAndHeight() {
        var state by mutableStateOf(GameState(active = spawnPiece(Tetromino.O), next = Tetromino.I))
        compose.setContent { Box(Modifier.size(220.dp, 484.dp)) { Board(state) } }
        for (next in Tetromino.entries) for (active in Tetromino.entries) for (rotation in 0..3) {
            compose.runOnIdle { state = state.copy(next = next, active = Piece(active, y = 8, rotation = rotation)) }
            val descended = compose.onNodeWithTag("board").captureToImage().toPixelMap()
            for (y in listOf(-2, -1, 0, 1)) {
                compose.runOnIdle { state = state.copy(active = state.active.copy(y = y)) }
                val spawned = compose.onNodeWithTag("board").captureToImage().toPixelMap()
                assertUncoveredPreview(state, descended, spawned)
            }
        }
    }

    /** The active blocks cover the preview, which updates without a movement or gravity frame. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun activeRemainsOnTopAndPreviewUpdatesAfterLockAndBothHoldPaths() {
        val engine = GameEngine(kotlin.random.Random(42))
        var state by mutableStateOf(engine.newGame())
        compose.setContent { Box(Modifier.size(220.dp, 484.dp)) { Board(state) } }
        fun checkRenderedState() {
            val current = state
            val image = compose.onNodeWithTag("board").captureToImage().toPixelMap()
            compose.runOnIdle { state = current.copy(active = current.active.copy(x = 0, y = 8)) }
            val unobscured = compose.onNodeWithTag("board").captureToImage().toPixelMap()
            for (cell in spawnPiece(current.next).cells().filterNot { it in current.active.cells() }) {
                assertNotEquals("Upcoming ${current.next} missing", image.colorAt(Cell(9, cell.y)), image.colorAt(cell))
                assertEquals(unobscured.colorAt(cell), image.colorAt(cell))
            }
            val alternate = Tetromino.entries.first { it != current.next }
            compose.runOnIdle { state = current.copy(next = alternate) }
            val otherPreview = compose.onNodeWithTag("board").captureToImage().toPixelMap()
            for (cell in current.active.cells().filter { it.y >= -SPAWN_DISPLAY_ROWS }) {
                assertEquals("Active block must cover both preview colors", image.colorAt(cell),
                    otherPreview.colorAt(cell))
            }
            compose.runOnIdle { state = current }
        }
        checkRenderedState()
        compose.runOnIdle { state = engine.apply(state, GameCommand.HOLD) }
        assertTrue(state.holdUsed)
        checkRenderedState()
        compose.runOnIdle { state = engine.apply(state, GameCommand.HARD_DROP) }
        assertFalse(state.holdUsed)
        checkRenderedState()
        val beforeSwap = state
        compose.runOnIdle { state = engine.apply(state, GameCommand.HOLD) }
        assertEquals(beforeSwap.held, state.active.type)
        assertEquals(beforeSwap.next, state.next)
        checkRenderedState()
    }

    private fun PixelMap.colorAt(cell: Cell) = this[
        ((cell.x + .5f) * width / BoardGeometry.WIDTH).toInt(),
        ((cell.y + SPAWN_DISPLAY_ROWS + .5f) * height / (BoardGeometry.VISIBLE_ROWS + SPAWN_DISPLAY_ROWS)).toInt()]

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun nextUsesFullScreenSpawnGridWithEitherGhostSetting() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        var width by mutableIntStateOf(320)
        var height by mutableIntStateOf(640)
        var fontScale by mutableFloatStateOf(1f)
        val state = GameState(active = spawnPiece(Tetromino.T).copy(y = 8), next = Tetromino.I,
            held = Tetromino.O, score = 70000)
        val model = GameViewModel(app, state, { 1000L }, false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                Box(Modifier.size(width.dp, height.dp)) { SwypetrisApp(model) {} }
            }
        }

        for ((w, h, scale) in listOf(Triple(240, 400, 2f), Triple(320, 640, 1f), Triple(600, 400, 2f))) {
            compose.runOnIdle { width = w; height = h; fontScale = scale }
            for (ghost in listOf(false, true)) {
                compose.runOnIdle { model.options.setHints(ghost) }
                val previewBounds = compose.onNodeWithTag("nextPreview").assertIsDisplayed()
                    .assertContentDescriptionEquals("Следующая фигура I").getUnclippedBoundsInRoot()
                val boardBounds = compose.onNodeWithTag("board").getUnclippedBoundsInRoot()
                val gridBounds = compose.onNodeWithTag("gridBackground").getUnclippedBoundsInRoot()

                assertEquals(boardBounds.left.value, previewBounds.left.value, 0.5f)
                assertEquals(boardBounds.right.value, previewBounds.right.value, 0.5f)
                val area = compose.onNodeWithTag("gameArea").getUnclippedBoundsInRoot()
                assertEquals(area.top.value, previewBounds.top.value, 0.5f)
                assertTrue(previewBounds.bottom < boardBounds.bottom)
                val boardWidth = boardBounds.right - boardBounds.left
                val boardHeight = boardBounds.bottom - boardBounds.top
                val gridWidth = gridBounds.right - gridBounds.left
                val gridHeight = gridBounds.bottom - gridBounds.top
                assertEquals(gridWidth.value, boardWidth.value, 0.5f)
                assertEquals(gridHeight.value, boardHeight.value, 0.5f)
                assertEquals(boardBounds, gridBounds)
                assertEquals(boardBounds.left, area.left)
                assertEquals(boardBounds.right, area.right)
                assertEquals(boardBounds.bottom, area.bottom)
                assertTrue(area.top >= boardBounds.top)

                compose.runOnIdle {
                    assertEquals(state, model.game)
                    assertTrue(model.engine.remainingBag().isEmpty())
                }
            }
            val file = java.io.File(app.getExternalFilesDir(null), "spawn-grid-$w.png")
            file.outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }


    private fun assertUncoveredPreview(state: GameState, descended: PixelMap, spawned: PixelMap) {
        for (cell in spawnPiece(state.next).cells().filterNot { it in state.active.cells() }) {
        assertNotEquals("Missing preview ${state.next} behind ${state.active.type}/${state.active.rotation}" +
            " at ${state.active.y}",
            descended.colorAt(Cell(9, cell.y)), descended.colorAt(cell))
        assertEquals("Preview moved or disappeared for ${state.next}/${state.active.type}/${state.active.rotation}" +
            " at ${state.active.y}",
            descended.colorAt(cell), spawned.colorAt(cell))
        }
    }
}
