package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NextQueueTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun previewAppearsOnlyAfterActiveLeavesOneEmptyRow() {
        var state by mutableStateOf(GameState(active = Piece(Tetromino.O, x = 0, y = -1), next = Tetromino.I))
        compose.setContent { Box(Modifier.size(220.dp, 484.dp)) { Board(state); NextSpawnLabel(state, 44.dp) } }
        fun previewPixel(): androidx.compose.ui.graphics.Color {
            val image = compose.onNodeWithTag("board").captureToImage().toPixelMap()
            return image[(image.width * .35f).toInt(), (image.height * 1.5f / 22).toInt()]
        }
        val hidden = previewPixel()
        compose.onNodeWithTag("nextLabel").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(active = state.active.copy(y = 0)) }
        assertEquals("An adjacent active piece must still hide the preview", hidden, previewPixel())
        compose.onNodeWithTag("nextLabel").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(active = state.active.copy(y = 1)) }
        assertNotEquals("One empty row must reveal the preview", hidden, previewPixel())
        compose.onNodeWithTag("nextLabel").assertIsDisplayed().assertTextEquals("ДАЛЕЕ")
        compose.runOnIdle { state = state.copy(active = spawnPiece(Tetromino.O).copy(x = 0)) }
        assertEquals("A replacement spawn must hide the preview again", hidden, previewPixel())
        compose.onNodeWithTag("nextLabel").assertDoesNotExist()
    }

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
                compose.runOnIdle { model.setHints(ghost) }
                val previewBounds = compose.onNodeWithTag("nextPreview").assertIsDisplayed()
                    .assertContentDescriptionEquals("Следующая фигура I").getUnclippedBoundsInRoot()
                val boardBounds = compose.onNodeWithTag("board").getUnclippedBoundsInRoot()
                val gridBounds = compose.onNodeWithTag("gridBackground").getUnclippedBoundsInRoot()

                assertEquals(boardBounds.left.value, previewBounds.left.value, 0.5f)
                assertEquals(boardBounds.right.value, previewBounds.right.value, 0.5f)
                assertEquals(boardBounds.top.value, previewBounds.top.value, 0.5f)
                assertTrue(previewBounds.bottom < boardBounds.bottom)
                val boardWidth = boardBounds.right - boardBounds.left
                val boardHeight = boardBounds.bottom - boardBounds.top
                val gridWidth = gridBounds.right - gridBounds.left
                val gridHeight = gridBounds.bottom - gridBounds.top
                assertEquals(gridWidth.value, boardWidth.value, 0.5f)
                assertEquals(gridHeight.value, boardHeight.value, 0.5f)
                val area = compose.onNodeWithTag("gameArea").getUnclippedBoundsInRoot()
                assertEquals(area, boardBounds)
                assertEquals(area, gridBounds)

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

}
