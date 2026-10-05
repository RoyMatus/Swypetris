package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

    @Test fun vibrationOffAndMultiTouchCancellationSuppressReadiness() {
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
}
