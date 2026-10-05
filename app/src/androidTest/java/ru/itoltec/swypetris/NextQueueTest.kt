package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
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

    @Test fun nextUsesSquareSpawnGridWithEitherGhostSetting() {
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
                assertTrue(gridBounds.width >= boardBounds.width)
                assertTrue(gridBounds.height >= boardBounds.height)

                val cellWidth = boardBounds.width / BoardGeometry.WIDTH
                val cellHeight = boardBounds.height /
                    (BoardGeometry.VISIBLE_ROWS + SPAWN_DISPLAY_ROWS)
                assertEquals(cellWidth.value, cellHeight.value, 0.5f)

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
