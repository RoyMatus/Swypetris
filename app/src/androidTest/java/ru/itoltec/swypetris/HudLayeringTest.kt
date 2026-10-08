package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.asAndroidBitmap
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
        model.setHints(false)
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            Box(Modifier.size(width.dp, 580.dp)) { GameContent(model, state, 24.dp) }
        }
        for (w in listOf(240, 320, 600)) {
            compose.runOnIdle { width = w; state = state.copy(score = 240000, active = Piece(Tetromino.I, x = 6,
                y = 8)) }
            val fruits = compose.onNodeWithTag("earnedFruits").fetchSemanticsNode().boundsInRoot
            val pairs = Fruit.entries.map { compose.onNodeWithTag("earnedFruit_${it.name}")
                .fetchSemanticsNode().boundsInRoot }
            val firstRow = pairs.count { abs(it.top - pairs.first().top) < 1f }
            assertEquals("Fruit rows use at most three evenly distributed items", minOf(3, pairs.size), firstRow)
            save("hud-$w-ordinary.png")
            val unobscured = compose.onRoot().captureToImage().toPixelMap()
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val area = compose.onNodeWithTag("gameArea").fetchSemanticsNode().boundsInRoot
            val cellWidth = area.width / 10
            val cellHeight = area.height / 21
            val gap = minOf(cellWidth, cellHeight) * .07f
            var coveredIcons = 0
            var coveredLabels = 0
            for (row in 0..5) {
                compose.runOnIdle { state = state.copy(active = Piece(Tetromino.I, x = 6, y = row - 2)) }
                val covered = compose.onRoot().captureToImage().toPixelMap()
                if (row == 2) save("hud-$w-overlap.png")
                val labels = Fruit.entries.map {
                    compose.onNodeWithTag("earnedFruitCount_${it.name}").assertTextEquals("3 ×")
                        .fetchSemanticsNode().boundsInRoot
                }
                val icons = Fruit.entries.map { compose.onNodeWithContentDescription(it.title)
                    .fetchSemanticsNode().boundsInRoot }
                compose.runOnIdle { state = state.copy(score = 0) }
                val cleanPiece = compose.onRoot().captureToImage().toPixelMap()
                val yTop = area.top + row * cellHeight + gap
                val yBottom = yTop + cellHeight - gap * 2
                for (y in (maxOf(yTop, fruits.top) - root.top).toInt() + 2 until
                    (minOf(yBottom, fruits.bottom) - root.top).toInt() - 2) {
                    for (x in (fruits.left - root.left).toInt() + 2 until (fruits.right - root.left).toInt() - 2) {
                        val local = x + root.left - area.left
                        val column = (local / cellWidth).toInt()
                        val inCell = local - column * cellWidth
                        if (column !in 6..9 || inCell < gap + 2 || inCell > cellWidth - gap - 2) continue
                        assertEquals("Fruit drawn over active piece at $w/$row/$x/$y", cleanPiece[x, y], covered[x, y])
                        if (unobscured[x, y] != cleanPiece[x, y]) {
                            if (labels.any { it.contains(androidx.compose.ui.geometry.Offset(x + root.left,
                                y + root.top)) }) coveredLabels++
                            else if (icons.any { it.contains(androidx.compose.ui.geometry.Offset(x + root.left,
                                y + root.top)) }) coveredIcons++
                        }
                    }
                }
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

    @androidx.annotation.RequiresApi(26)
    private fun save(name: String) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = java.io.File(app.getExternalFilesDir(null), name)
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
