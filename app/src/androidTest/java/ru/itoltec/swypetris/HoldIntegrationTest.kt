package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.bottom
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.height
import androidx.compose.ui.test.left
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.right
import androidx.compose.ui.test.top
import androidx.compose.ui.test.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HoldIntegrationTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun gesturePersistsHoldAndDoesNotControlReplacement() {
        var now = 1000L
        var pulses = 0
        val feedback = object : GameFeedback {
            override fun play(event: FeedbackEvent, sound: Boolean, vibration: Boolean) = Unit
            override fun holdReady() { pulses++ }
            override fun stop() = Unit
            override fun release() = Unit
        }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = GameViewModel(app, GameState(active = Piece(Tetromino.T), next = Tetromino.I), { now }, false,
            feedback)
        model.input.pointerDown(100f, 200f, now)
        now += 300
        model.simulation.advanceFrame(now)
        assertEquals(1, pulses)
        model.input.pointerMove(100f, 176f, now)
        val held = model.game!!
        assertEquals(Tetromino.T, held.held)
        assertEquals(Tetromino.I, held.active.type)
        model.input.pointerMove(200f, 100f, now + 50)
        model.input.pointerUp(200f, 100f, now + 60)
        assertEquals(held, model.game)
        model.pause()
        val restored = GameViewModel(app, null, { now + 10000 }, false)
        assertEquals(held, restored.game)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.size(320.dp, 480.dp)) { Board(held); GameHud(held) }
            }
        }
        compose.onNodeWithTag("holdPreview").assertContentDescriptionEquals("Запас T, обмен недоступен")
    }

    @Test fun vibrationOffAndCancelledTouchSuppressReadiness() {
        var pulses = 0
        var now = 1000L
        val feedback = object : GameFeedback {
            override fun play(event: FeedbackEvent, sound: Boolean, vibration: Boolean) = Unit
            override fun holdReady() { pulses++ }
            override fun stop() = Unit
            override fun release() = Unit
        }
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.T), next = Tetromino.O), { now }, false, feedback)
        model.options.setVibration(false)
        model.input.pointerDown(100f, 200f, now)
        now += 300
        model.simulation.advanceFrame(now)
        model.input.pointerUp(100f, 200f, now)
        assertNull(model.game!!.held)
        assertEquals(0, pulses)
        model.options.setVibration(true)
        model.input.pointerDown(100f, 200f, now)
        model.input.cancelGesture()
        now += 300
        model.simulation.advanceFrame(now)
        assertEquals(0, pulses)
        model.pause()
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun holdPreviewMovesOutOfBoardAndPreservesSettledCells() {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        board[BoardGeometry.row(2)][1] = Tetromino.J
        var state by mutableStateOf(GameState(board = board, active = Piece(Tetromino.T, y = 10), next = Tetromino.I))
        compose.setContent { Box(Modifier.size(220.dp, 484.dp)) { Board(state); GameHud(state) } }
        compose.onNodeWithTag("holdPreview").assertContentDescriptionEquals("Запас пуст, обмен доступен")
        val before = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        compose.runOnIdle { state = state.copy(held = Tetromino.O) }
        compose.onNodeWithTag("holdPreview").assertContentDescriptionEquals("Запас O, обмен доступен")
        val after = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        fun pixel(image: androidx.compose.ui.graphics.PixelMap, x: Float, visualRow: Float) =
            image[(image.width * x / BoardGeometry.WIDTH).toInt(),
                (image.height * visualRow / (BoardGeometry.VISIBLE_ROWS + SPAWN_DISPLAY_ROWS)).toInt()]
        assertEquals(pixel(before, 1.2f, 2.3f + SPAWN_DISPLAY_ROWS),
            pixel(after, 1.2f, 2.3f + SPAWN_DISPLAY_ROWS))
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun emptyHoldDrawsNothingOverTheBoard() {
        var showHud by mutableStateOf(false)
        val state = GameState(active = Piece(Tetromino.T, y = 10), next = Tetromino.I)
        compose.setContent {
            Box(Modifier.size(220.dp, 484.dp)) {
                Board(state)
                if (showHud) GameHud(state)
            }
        }
        val before = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        compose.runOnIdle { showHud = true }
        val hold = compose.onNodeWithTag("holdPreview").fetchSemanticsNode().boundsInRoot
        val board = compose.onNodeWithTag("board").fetchSemanticsNode().boundsInRoot
        val after = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        for (y in (hold.top - board.top).toInt() until (hold.bottom - board.top).toInt()) {
            for (x in (hold.left - board.left).toInt() until (hold.right - board.left).toInt()) {
                assertEquals("Empty Hold must leave the board unchanged", before[x, y], after[x, y])
            }
        }
        assertTrue(hold.width <= board.width * .22f)
    }
}
