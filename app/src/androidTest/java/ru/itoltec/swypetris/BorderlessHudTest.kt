package ru.itoltec.swypetris

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Checks visual separation and typography without changing fullscreen board geometry. */
class BorderlessHudTest {
    @get:Rule val compose = createComposeRule()

    /** Long scores and doubled fonts cannot enter the Next or Hold regions. */
    @Test fun labelsAndLongScoreStayWithinTheirRegions() {
        var width by mutableIntStateOf(240)
        var height by mutableIntStateOf(400)
        var fontScale by mutableFloatStateOf(2f)
        var palette by mutableStateOf(GamePalettes.find("classic"))
        var next by mutableStateOf(Tetromino.I)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalGamePalette provides palette) {
                val state = GameState(active = Piece(Tetromino.S, y = 8), next = next,
                    held = Tetromino.L, score = Int.MAX_VALUE)
                Box(Modifier.size(width.dp, height.dp)) {
                    val header = height.dp / 22 * SPAWN_DISPLAY_ROWS
                    Board(state)
                    NextSpawnLabel(state, header)
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) { GameHud(state, header) }
                }
            }
        }
        for ((w, h, scale) in listOf(Triple(240, 400, 2f), Triple(320, 640, 1f), Triple(600, 400, 2f))) {
            for (theme in listOf("classic", "solarized_light", "github_light")) {
                for (piece in listOf(Tetromino.I, Tetromino.O, Tetromino.T)) {
                    compose.runOnIdle { width = w; height = h; fontScale = scale; palette = GamePalettes.find(theme); next = piece }
                    val score = compose.onNodeWithTag("score").assertTextEquals(Int.MAX_VALUE.toString())
                        .fetchSemanticsNode().boundsInRoot
                    val label = compose.onNodeWithTag("nextLabel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    val hold = compose.onNodeWithTag("holdPreview").assertContentDescriptionEquals("Запас L, обмен доступен")
                        .fetchSemanticsNode().boundsInRoot
                    val board = compose.onNodeWithTag("board").fetchSemanticsNode().boundsInRoot
                    assertTrue("Score enters the spawn lane at $w/$theme/$piece", score.right <= label.left)
                    assertTrue("Hold enters the spawn lane at $w/$theme/$piece", hold.left >= label.right)
                    assertTrue(hold.right <= board.right)
                    assertTrue(compose.onNodeWithTag("earnedFruits").fetchSemanticsNode().boundsInRoot.top >= hold.bottom)
                    for (tag in listOf("score", "scoreLabel", "nextLabel", "holdLabel")) {
                        val layouts = mutableListOf<TextLayoutResult>()
                        compose.onNodeWithTag(tag).assertIsDisplayed()
                            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        assertEquals(1, layouts.size)
                        val layout = layouts.single()
                        assertFalse("Clipped $tag at $w/$scale/$theme: size=${layout.size}, " +
                            "width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, " +
                            "font=${layout.layoutInput.style.fontSize}, constraints=${layout.layoutInput.constraints}",
                            layout.hasVisualOverflow)
                    }
                }
            }
        }
    }

    /** Every Next shape uses its spawn cells and an outline stronger than its faint flat fill. */
    @Test fun nextHasQuietFillAndReadableOutlinesInDarkAndLightThemes() {
        var palette by mutableStateOf(GamePalettes.find("classic"))
        var next by mutableStateOf(Tetromino.I)
        var visible by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalGamePalette provides palette) {
                Box(Modifier.size(220.dp, 484.dp)) {
                    Board(GameState(active = Piece(Tetromino.O, x = 0, y = if (visible) 8 else -1), next = next))
                }
            }
        }
        for (theme in listOf("classic", "solarized_light", "github_light")) {
            for (piece in Tetromino.entries) {
                compose.runOnIdle { palette = GamePalettes.find(theme); next = piece; visible = false }
                val hidden = compose.onNodeWithTag("board").captureToImage().toPixelMap()
                compose.runOnIdle { visible = true }
                val preview = compose.onNodeWithTag("board").captureToImage().toPixelMap()
                val cellWidth = preview.width / 10f
                val cellHeight = preview.height / 22f
                val gap = minOf(cellWidth, cellHeight) * .07f
                for (cell in spawnPiece(piece).cells()) {
                    val y = ((cell.y + SPAWN_DISPLAY_ROWS + .5f) * cellHeight).toInt()
                    val centerX = ((cell.x + .5f) * cellWidth).toInt()
                    val edgeX = (cell.x * cellWidth + gap).toInt()
                    val fill = difference(preview[centerX, y], hidden[centerX, y])
                    val edge = difference(preview[edgeX, y], hidden[edgeX, y])
                    assertTrue("Next needs a faint fill: $theme/$piece", fill > .001f && fill < .2f)
                    assertTrue("Next outline must dominate the fill: $theme/$piece", edge > fill * 3)
                }
            }
        }
    }

    /** RGB distance from the unchanged board background at the same pixel. */
    private fun difference(first: Color, second: Color): Float =
        abs(first.red - second.red) + abs(first.green - second.green) + abs(first.blue - second.blue)
}
