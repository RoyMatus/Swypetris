package ru.itoltec.swypetris

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GestureDensityTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun composePointerSurvivesNaturalPieceSpawn() {
        var now = 1000L
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O, y = 18), next = Tetromino.T), { now }, false)
        compose.setContent { SwypetrisApp(model) {} }
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val density = context.resources.displayMetrics.density
        val step = maxOf(12 * density, android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()) + 2
        compose.onNodeWithTag("gameArea").performTouchInput { down(center) }
        compose.runOnIdle { now += 800; model.advanceFrame(now) }
        val spawned = model.game!!
        assertEquals(1, spawned.generation)
        compose.onNodeWithTag("gameArea").performTouchInput { moveBy(Offset(step, 0f)); up() }
        compose.runOnIdle {
            assertEquals(spawned.active.x + 1, model.game!!.active.x)
            assertEquals(spawned.active.y, model.game!!.active.y)
            assertEquals(spawned.active.rotation, model.game!!.active.rotation)
            assertEquals(1, model.game!!.generation)
        }
    }

    @Test fun realPixelEventsAreConvertedToDpAcrossDensities() {
        var density by mutableFloatStateOf(1f)
        var model by mutableStateOf(GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.T), next = Tetromino.O), { 1000L }, false))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density)) {
                Box(Modifier.fillMaxSize()) { SwypetrisApp(model) {} }
            }
        }
        for (scale in listOf(1f, 1.5f, 3f, 4f)) {
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop / scale
            val rotateDistance = maxOf(24f, slop * 2) + 2
            val dropDistance = maxOf(48f, slop * 4) + 4
            compose.runOnIdle {
                density = scale
                model = GameViewModel(ApplicationProvider.getApplicationContext(),
                    GameState(active = Piece(Tetromino.T), next = Tetromino.O), { 1000L }, false)
            }
            compose.onNodeWithTag("gameArea").performTouchInput { click(center) }
            compose.runOnIdle { assertEquals(1, model.game!!.active.y); assertEquals(0, model.game!!.active.rotation) }
            compose.onNodeWithTag("gameArea").performTouchInput { swipe(center, center - Offset(0f, rotateDistance * scale), 100) }
            compose.runOnIdle { assertEquals(1, model.game!!.active.rotation) }
            compose.onNodeWithTag("gameArea").performTouchInput { swipe(center, center + Offset(48 * scale, 48 * scale), 100) }
            compose.runOnIdle { assertEquals(0, model.game!!.generation) }
            compose.onNodeWithTag("gameArea").performTouchInput { swipe(center, center + Offset(0f, dropDistance * scale), 100) }
            compose.runOnIdle { assertEquals(1, model.game!!.generation) }
        }
    }
}
