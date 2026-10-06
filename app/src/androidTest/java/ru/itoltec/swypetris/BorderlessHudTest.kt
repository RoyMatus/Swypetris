package ru.itoltec.swypetris

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Checks visual separation and typography without changing fullscreen board geometry. */
class BorderlessHudTest {
    @get:Rule val compose = createComposeRule()

    /** Visible digit tops align with spawn outlines; score pulses and fruits stay in the left lane. */
    @Test fun digitalScoreAlignsWithHintAndFruitsStayInLeftLane() {
        var width by mutableIntStateOf(240)
        var height by mutableIntStateOf(400)
        var fontScale by mutableFloatStateOf(2f)
        var palette by mutableStateOf(GamePalettes.find("classic"))
        var next by mutableStateOf(Tetromino.I)
        var held by mutableStateOf<Tetromino?>(null)
        var scoreValue by mutableIntStateOf(Int.MAX_VALUE)
        var pixelsPerDp = 1f
        compose.setContent {
            pixelsPerDp = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalGamePalette provides palette) {
                val state = GameState(active = Piece(Tetromino.S, y = 8), next = next,
                    held = held, score = scoreValue)
                Box(Modifier.size(width.dp, height.dp).windowInsetsPadding(
                    WindowInsets(left = 12.dp, top = 52.dp, right = 8.dp, bottom = 0.dp))) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val geometry = gameplayGeometry(with(LocalDensity.current) { maxHeight.toPx() }, 0f)
                        val header = with(LocalDensity.current) { (geometry.cellHeight * 2).toDp() }
                        Board(state, geometry = geometry)
                        GameHud(state, header)
                    }
                }
            }
        }
        for ((w, h, scale) in listOf(Triple(240, 400, 2f), Triple(320, 640, 1f), Triple(600, 400, 2f))) {
            for (theme in listOf("classic", "solarized_light", "github_light")) {
                for (piece in Tetromino.entries) {
                    for (holdPiece in listOf(null, piece)) {
                        for (value in listOf(0, 138, 999999, Int.MAX_VALUE)) {
                            compose.runOnIdle {
                                width = w; height = h; fontScale = scale
                                palette = GamePalettes.find(theme); next = piece; held = holdPiece; scoreValue = value
                            }
                            val score = compose.onNodeWithTag("score").assertTextEquals(value.toString())
                                .fetchSemanticsNode().boundsInRoot
                            val hold = compose.onNodeWithTag("holdPreview").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                            val board = compose.onNodeWithTag("board").fetchSemanticsNode().boundsInRoot
                            val cells = spawnPiece(piece).cells()
                            val rowHeight = board.height / 21
                            val gap = minOf(board.width / 10, rowHeight) * .07f
                            assertEquals("Score top $w/$piece", board.top + gap, score.top, 1f)
                            assertEquals("Hold top $w/$piece", score.top, hold.top, 1f)
                            assertTrue(score.top >= board.top && hold.top >= board.top)
                            val hintLeft = board.left + cells.minOf { it.x } * board.width / 10
                            val hintRight = board.left + (cells.maxOf { it.x } + 1) * board.width / 10
                            assertTrue(score.right <= hintLeft)
                            assertTrue(hold.left >= hintRight)
                            assertEquals(board.left + 20 * pixelsPerDp, score.left, 1f)
                            assertEquals(board.right - 20 * pixelsPerDp, hold.right, 1f)
                            if (value >= GameRules.FRUIT_STEP) {
                                val fruits = compose.onNodeWithTag("earnedFruits").fetchSemanticsNode().boundsInRoot
                                assertTrue(fruits.top >= score.bottom)
                                assertEquals(score.left, fruits.left, 1f)
                                assertTrue(fruits.right <= hintLeft)
                                val items = Fruit.entries.zip(stateFruitCounts(value)).filter { it.second > 0 }
                                var previous: androidx.compose.ui.geometry.Rect? = null
                                items.forEach { (fruit, count) ->
                                    val item = compose.onNodeWithTag("earnedFruit_${fruit.name}").fetchSemanticsNode().boundsInRoot
                                    assertTrue(item.left >= fruits.left && item.right <= fruits.right + 1f)
                                    previous?.let {
                                        if (item.top >= it.bottom) assertEquals(score.left, item.left, 1f)
                                        else assertTrue(item.left >= it.right)
                                    }
                                    if (count > 1) {
                                        val label = compose.onNodeWithTag("earnedFruitCount_${fruit.name}")
                                            .assertTextEquals("$count ×").fetchSemanticsNode().boundsInRoot
                                        assertTrue(label.left >= item.left && label.right <= item.right)
                                    }
                                    previous = item
                                }
                            }
                            val label = compose.onNodeWithTag("holdLabel").assertTextEquals("Запас")
                                .fetchSemanticsNode().boundsInRoot
                            assertTrue(label.top >= hold.bottom && label.left >= hold.left && label.right <= hold.right + 1f)
                            val layouts = mutableListOf<TextLayoutResult>()
                            compose.onNodeWithTag("holdLabel").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                            assertFalse("Hold label must fit", layouts.single().hasVisualOverflow)
                            for (tag in listOf("scoreLabel", "nextLabel"))
                                compose.onNodeWithTag(tag).assertDoesNotExist()
                        }
                    }
                }
            }
        }
    }
    private fun stateFruitCounts(score: Int) = GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = score).fruitCounts

    @Test fun framelessHoldIsExtraFaintAndUsedStateIsStillDistinct() {
        var palette by mutableStateOf(GamePalettes.find("classic"))
        var held by mutableStateOf<Tetromino?>(null)
        var used by mutableStateOf(false)
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalGamePalette provides palette) {
                Box(Modifier.size(320.dp, 480.dp).background(palette.background)) {
                    GameHud(GameState(active = Piece(Tetromino.O), next = Tetromino.T, held = held, holdUsed = used))
                }
            }
        }
        for (theme in listOf("classic", "solarized_light", "github_light")) for (piece in Tetromino.entries) {
            compose.runOnIdle { palette = GamePalettes.find(theme); held = null; used = false }
            val empty = compose.onNodeWithTag("holdPreview").captureToImage().toPixelMap()
            compose.runOnIdle { held = piece }
            val available = compose.onNodeWithTag("holdPreview")
                .assertContentDescriptionEquals("Запас ${piece.name}, обмен доступен").captureToImage().toPixelMap()
            val paintedTop = (0 until available.height).first { y ->
                (0 until available.width).any { x -> difference(available[x, y], empty[x, y]) > .001f }
            }
            assertTrue("Painted Hold must start at the score top: $theme/$piece", paintedTop <= 1)
            compose.runOnIdle { used = true }
            val unavailable = compose.onNodeWithTag("holdPreview")
                .assertContentDescriptionEquals("Запас ${piece.name}, обмен недоступен").captureToImage().toPixelMap()
            var availableDifference = 0f
            var usedDifference = 0f
            for (y in 0 until empty.height) for (x in 0 until empty.width) {
                availableDifference += difference(available[x, y], empty[x, y])
                usedDifference += difference(unavailable[x, y], empty[x, y])
            }
            availableDifference /= empty.width * empty.height
            usedDifference /= empty.width * empty.height
            assertTrue("Recognizable but extra faint: $theme/$piece", availableDifference > .005f && availableDifference < .25f)
            assertTrue("Used Hold remains distinct: $theme/$piece", usedDifference > .001f && availableDifference > usedDifference * 1.5f)
            val corner = (density * 1).toInt()
            for (x in listOf(corner, empty.width - 1 - corner)) for (y in listOf(corner, empty.height - 1 - corner))
                assertEquals("Hold must have no outer brackets", empty[x, y], available[x, y])
        }
    }
    /** Every Next shape uses its spawn cells and an outline stronger than its faint flat fill. */
    @Test fun nextHasQuietFillAndReadableOutlinesInDarkAndLightThemes() {
        var palette by mutableStateOf(GamePalettes.find("classic"))
        var next by mutableStateOf(Tetromino.I)
        compose.setContent {
            CompositionLocalProvider(LocalGamePalette provides palette) {
                Box(Modifier.size(220.dp, 484.dp)) {
                    Board(GameState(active = Piece(Tetromino.O, x = 0, y = -1), next = next))
                }
            }
        }
        for (theme in listOf("classic", "solarized_light", "github_light")) {
            for (piece in Tetromino.entries) {
                compose.runOnIdle { palette = GamePalettes.find(theme); next = piece }
                val preview = compose.onNodeWithTag("board").captureToImage().toPixelMap()
                val cellWidth = preview.width / 10f
                val cellHeight = preview.height / 22f
                val gap = minOf(cellWidth, cellHeight) * .07f
                for (cell in spawnPiece(piece).cells()) {
                    val y = ((cell.y + SPAWN_DISPLAY_ROWS + .5f) * cellHeight).toInt()
                    val centerX = ((cell.x + .5f) * cellWidth).toInt()
                    val edgeX = (cell.x * cellWidth + gap).toInt()
                    val background = preview[(preview.width * .95f).toInt(), y]
                    val fill = difference(preview[centerX, y], background)
                    val edge = difference(preview[edgeX, y], background)
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
