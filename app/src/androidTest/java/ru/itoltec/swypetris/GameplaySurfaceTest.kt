package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GameplaySurfaceTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun gridContinuesUnderStatusAreaWhileForegroundIsClipped() {
        var top by mutableIntStateOf(52)
        var state by mutableStateOf(GameState(active = Piece(Tetromino.T, y = 8), next = Tetromino.O))
        val model = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), state, { 1000L }, false)
        model.setHints(false)
        var pixelsPerDp = 1f
        compose.setContent {
            pixelsPerDp = LocalDensity.current.density
            Box(Modifier.size(320.dp, 640.dp)) { GameContent(model, state, top.dp) }
        }
        for (inset in listOf(24, 52, 90)) {
            compose.runOnIdle { top = inset; state = state.copy(board = BoardGeometry.empty(), active = Piece(Tetromino.T, y = 8)) }
            val before = compose.onNodeWithTag("gridBackground").captureToImage().toPixelMap()
            val area = compose.onNodeWithTag("gameArea").fetchSemanticsNode().boundsInRoot
            val preview = compose.onNodeWithTag("nextPreview").fetchSemanticsNode().boundsInRoot
            assertEquals(inset * pixelsPerDp, area.top, 1f)
            assertEquals(area.top, preview.top, 1f)
            val sampleY = (inset * pixelsPerDp / 2).toInt()
            val columnX = before.width / 10
            assertTrue("Grid must be visible behind status icons", (-1..1).any {
                before[columnX + it, sampleY] != before[(before.width * .15f).toInt(), sampleY]
            })
            val edgeY = (inset * pixelsPerDp).toInt()
            val middleX = (before.width * .05f).toInt()
            val edge = before[middleX, edgeY]
            val above = before[middleX, edgeY - 3]
            assertTrue("No horizontal strip at the status boundary",
                kotlin.math.abs(edge.red - above.red) + kotlin.math.abs(edge.green - above.green) +
                    kotlin.math.abs(edge.blue - above.blue) < .02f)
            compose.runOnIdle {
                val hidden = BoardGeometry.empty().toMutableList()
                hidden[BoardGeometry.row(-2)] = List(10) { Tetromino.L }
                state = state.copy(board = hidden, active = Piece(Tetromino.I, y = -3))
            }
            val after = compose.onNodeWithTag("gridBackground").captureToImage().toPixelMap()
            for (y in 0 until (inset * pixelsPerDp).toInt()) for (x in 0 until before.width)
                assertEquals("Foreground entered status area at $x/$y", before[x, y], after[x, y])
        }
    }
}
