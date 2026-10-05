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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
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
        val model = GameViewModel(app, GameState(active = Piece(Tetromino.T), next = Tetromino.I), { now }, false, feedback)
        model.pointerDown(100f, 200f, now)
        now += 300
        model.advanceFrame(now)
        assertEquals(1, pulses)
        model.pointerMove(100f, 176f, now)
        val held = model.game!!
        assertEquals(Tetromino.T, held.held)
        assertEquals(Tetromino.I, held.active.type)
        model.pointerMove(200f, 100f, now + 50)
        model.pointerUp(200f, 100f, now + 60)
        assertEquals(held, model.game)
        model.pause()
        val restored = GameViewModel(app, null, { now + 10000 }, false)
        assertEquals(held, restored.game)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.size(320.dp, 480.dp)) { Board(held); GameHud(held) }
            }
        }
        compose.onNodeWithTag("board").assertContentDescriptionContains("Запас: T, обмен недоступен", substring = true)
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
        val model = GameViewModel(ApplicationProvider.getApplicationContext(), GameState(active = Piece(Tetromino.T), next = Tetromino.O), { now }, false, feedback)
        model.setVibration(false)
        model.pointerDown(100f, 200f, now)
        now += 300
        model.advanceFrame(now)
        model.pointerUp(100f, 200f, now)
        assertNull(model.game!!.held)
        assertEquals(0, pulses)
        model.setVibration(true)
        model.pointerDown(100f, 200f, now)
        model.cancelGesture()
        now += 300
        model.advanceFrame(now)
        assertEquals(0, pulses)
        model.pause()
    }

    @Test fun previewIsVisibleButNeverCoversSettledCells() {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        board[BoardGeometry.row(2)][1] = Tetromino.J
        var state by mutableStateOf(GameState(board = board, active = Piece(Tetromino.T, y = 10), next = Tetromino.I))
        compose.setContent { Box(Modifier.size(320.dp, 480.dp)) { Board(state, showNext = false) } }
        val before = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        compose.runOnIdle { state = state.copy(held = Tetromino.O) }
        val after = compose.onNodeWithTag("board").captureToImage().toPixelMap()
        fun pixel(image: androidx.compose.ui.graphics.PixelMap, x: Float, y: Float) =
            image[(image.width * x / 10).toInt(), (image.height * y / 20).toInt()]
        assertNotEquals(pixel(before, .6f, 1.7f), pixel(after, .6f, 1.7f))
        assertEquals(pixel(before, 1.2f, 2.3f), pixel(after, 1.2f, 2.3f))
    }
}
