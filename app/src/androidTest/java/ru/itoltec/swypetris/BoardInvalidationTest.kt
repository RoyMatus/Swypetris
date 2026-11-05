package ru.itoltec.swypetris

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BoardInvalidationTest {
    @get:Rule val compose = createComposeRule()

    @SdkSuppress(minSdkVersion = 26)
    @Test fun staticBoardSkipsFallingPieceUpdatesButKeepsSemanticsAndVisibleChanges() {
        var state by mutableStateOf(GameState(active = Piece(Tetromino.T), next = Tetromino.O))
        val draws = AtomicInteger()
        compose.setContent {
            Box(Modifier.size(240.dp, 480.dp).drawWithContent {
                draws.incrementAndGet()
                drawContent()
            }) { Board(state, drawActive = false) }
        }
        compose.waitForIdle()
        val original = compose.onNodeWithTag("board").captureToImage().asAndroidBitmap()
        val initial = draws.get()
        compose.runOnIdle { state = state.copy(active = state.active.copy(y = 1), score = 100) }
        compose.waitForIdle()
        compose.onNodeWithTag("board").assertContentDescriptionContains("Игровое поле, очки 100", substring = true)
        assertEquals("A separately drawn falling piece must not repaint settled artwork", initial, draws.get())
        compose.runOnIdle { state = state.copy(next = Tetromino.I) }
        compose.waitForIdle()
        val preview = compose.onNodeWithTag("board").captureToImage().asAndroidBitmap()
        assertTrue("Next preview changes must repaint", !preview.sameAs(original))
        compose.runOnIdle {
            val board = state.board.map { it.toMutableList() }
            board[BoardGeometry.row(19)][0] = Tetromino.J
            state = state.copy(board = board)
        }
        compose.waitForIdle()
        val settled = compose.onNodeWithTag("board").captureToImage().asAndroidBitmap()
        assertTrue("Settled cells must repaint", !settled.sameAs(preview))
    }
}
