package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.bottom
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.height
import androidx.compose.ui.test.left
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.right
import androidx.compose.ui.test.top
import androidx.compose.ui.test.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class HudLayeringTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun activePieceCoversFruitIconsAndLabelsOnlyAtIntersectingPixels() {
        var width by mutableIntStateOf(240)
        var state by mutableStateOf(GameState(active = Piece(Tetromino.I, x = 6, y = 8), next = Tetromino.O,
            held = Tetromino.L, score = 240000))
        val model = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), state, { 1000L }, false)
        model.options.setHints(false)
        var density = 1f
        var material: BlockMaterial? = null
        compose.setContent {
            density = LocalDensity.current.density
            material = blockMaterial(LocalGamePalette.current)
            Box(Modifier.size(width.dp, 580.dp)) { GameContent(model, state, 24.dp) }
        }
        for (w in listOf(240, 320, 600)) {
            compose.runOnIdle { width = w; state = state.copy(score = 240000, active = Piece(Tetromino.I, x = 6,
                y = 8)) }
            val fruits = compose.onNodeWithTag("earnedFruits").fetchSemanticsNode().boundsInRoot
            val pairs = Fruit.entries.map { compose.onNodeWithTag("earnedFruit_${it.name}")
                .fetchSemanticsNode().boundsInRoot }
            val firstRow = pairs.count { abs(it.top - pairs.first().top) < 1f }
            assertEquals("Fruit rows use two evenly distributed items", minOf(2, pairs.size), firstRow)
            pairs.chunked(2).forEach { row ->
                row.forEach { assertEquals(row.first().top, it.top, 1f) }
            }
            pairs.chunked(2).zipWithNext().forEach { (above, below) ->
                assertTrue(below.first().top >= above.maxOf { it.bottom })
            }
            Fruit.entries.forEachIndexed { index, fruit ->
                val icon = compose.onNodeWithTag("earnedFruitIcon_${fruit.name}").fetchSemanticsNode().boundsInRoot
                val cell = pairs[index]
                // The old HUD slot stays fixed; only the artwork shrinks by the requested factor.
                val previousArtworkSize = minOf(30 * density, cell.width * .8f).coerceAtLeast(density)
                assertEquals("Gameplay fruit width is two-thirds of its former size",
                    previousArtworkSize / 1.5f, icon.width, 1f)
                assertEquals("Gameplay fruit height is two-thirds of its former size",
                    previousArtworkSize / 1.5f, icon.height, 1f)
                assertEquals(cell.left, icon.left, 1f)
                assertEquals(cell.top, icon.top, 1f)
                assertTrue(icon.right <= cell.right && icon.bottom <= cell.bottom)
            }
            val unobscured = compose.onRoot().captureToImage().toPixelMap()
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val area = compose.onNodeWithTag("gameArea").fetchSemanticsNode().boundsInRoot
            val overlap = HudOverlap(fruits, root, area, unobscured, requireNotNull(material).image.toPixelMap())
            var coveredIcons = 0
            var coveredLabels = 0
            for (row in 0..5) {
                compose.runOnIdle { state = state.copy(active = Piece(Tetromino.I, x = 6, y = row - 2)) }
                val covered = compose.onRoot().captureToImage().toPixelMap()
                val labels = Fruit.entries.map {
                    compose.onNodeWithTag("earnedFruitCount_${it.name}").assertTextEquals("3")
                        .fetchSemanticsNode().boundsInRoot
                }
                val icons = Fruit.entries.map { compose.onNodeWithContentDescription(it.title)
                    .fetchSemanticsNode().boundsInRoot }
                compose.runOnIdle { state = state.copy(score = 0) }
                val cleanPiece = compose.onRoot().captureToImage().toPixelMap()
                val (iconsCovered, labelsCovered) = overlap.countCovered(w, row, covered, cleanPiece, labels, icons)
                coveredIcons += iconsCovered
                coveredLabels += labelsCovered
                compose.runOnIdle { state = state.copy(score = 240000) }
                Fruit.entries.forEach { compose.onNodeWithTag("earnedFruit_${it.name}").assertIsDisplayed() }
            }
            assertTrue("Must exercise icon overlap $w", coveredIcons > 0)
            assertTrue("Must exercise quantity overlap $w", coveredLabels > 0)
            assertEquals(20 * density, area.right - fruits.right, 1f)
        }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun whiteSegmentsAndInactiveSegmentsRemainVisible() {
        var value by mutableStateOf("1")
        compose.setContent { DigitalScore(value, 60.dp, Modifier.background(Color.Black).testTag("digits")) }
        val one = compose.onNodeWithTag("digits").assertTextEquals("1").captureToImage().toPixelMap()
        compose.runOnIdle { value = "8" }
        val eight = compose.onNodeWithTag("digits").assertTextEquals("8").captureToImage().toPixelMap()
        val x = one.width / 2
        val y = (one.height * .0375f).toInt()
        assertTrue("Inactive top segment must be faint but visible", one[x, y].red in .05f.. .2f)
        assertEquals(1f, eight[x, y].red, .01f)
        assertEquals(eight[x, y].red, eight[x, y].green, .01f)
        assertEquals(eight[x, y].red, eight[x, y].blue, .01f)
        assertTrue(eight[x, y].red > one[x, y].red)
    }

    private class HudOverlap(val fruits: androidx.compose.ui.geometry.Rect,
        val root: androidx.compose.ui.geometry.Rect, val area: androidx.compose.ui.geometry.Rect,
        val unobscured: androidx.compose.ui.graphics.PixelMap, val atlas: androidx.compose.ui.graphics.PixelMap) {
        val cellWidth = area.width / 10
        val cellHeight = area.height / 21
        val gap = minOf(cellWidth, cellHeight) * .07f

        fun countCovered(w: Int, row: Int, covered: androidx.compose.ui.graphics.PixelMap,
            cleanPiece: androidx.compose.ui.graphics.PixelMap, labels: List<androidx.compose.ui.geometry.Rect>,
            icons: List<androidx.compose.ui.geometry.Rect>): Pair<Int, Int> {
            var coveredIcons = 0
            var coveredLabels = 0
        val yTop = area.top + row * cellHeight + gap
        val yBottom = yTop + cellHeight - gap * 2
        for (y in (maxOf(yTop, fruits.top) - root.top).toInt() + 2 until
            (minOf(yBottom, fruits.bottom) - root.top).toInt() - 2) {
            for (x in (fruits.left - root.left).toInt() + 2 until (fruits.right - root.left).toInt() - 2) {
                val local = x + root.left - area.left
                val column = (local / cellWidth).toInt()
                val inCell = local - column * cellWidth
                val alpha = if (column in 6..9 && inCell >= gap + 2 && inCell <= cellWidth - gap - 2)
                    artworkAlpha(atlas, Tetromino.I, column - 6,
                    (inCell - gap) / (cellWidth - gap * 2) * 128,
                    (y + root.top - yTop) / (cellHeight - gap * 2) * 128,
                    kotlin.math.ceil(128 / minOf(cellWidth - gap * 2, cellHeight - gap * 2)).toInt() + 1) else 0f
                if (alpha < .9f) continue
                assertArtworkAbove("Fruit drawn over active artwork at $w/$row/$x/$y",
                    cleanPiece[x, y], covered[x, y], alpha)
                val contrast = maxOf(abs(unobscured[x, y].red - cleanPiece[x, y].red),
                    abs(unobscured[x, y].green - cleanPiece[x, y].green),
                    abs(unobscured[x, y].blue - cleanPiece[x, y].blue))
                if (contrast > 2 * (1 - alpha + 1f / 255f)) {
                    if (labels.any { it.contains(androidx.compose.ui.geometry.Offset(x + root.left,
                        y + root.top)) }) coveredLabels++
                    else if (icons.any { it.contains(androidx.compose.ui.geometry.Offset(x + root.left,
                        y + root.top)) }) coveredIcons++
                }
            }
        }
            return coveredIcons to coveredLabels
        }

    }
}
