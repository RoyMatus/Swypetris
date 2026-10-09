package ru.itoltec.swypetris

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLayoutTest {
    @Test fun roundThemesChooseAcrossTheCatalogRatherThanRotateSequentially() {
        val random = kotlin.random.Random(183)
        var current = GamePalettes.all.first().id
        val seen = mutableSetOf<String>()
        var nonSequential = false
        repeat(100) {
            val next = GamePalettes.randomId(current, random)
            assertTrue(GamePalettes.all.any { it.id == next })
            assertFalse(current == next)
            val sequential = GamePalettes.all[(GamePalettes.all.indexOfFirst { it.id == current } + 1) %
                GamePalettes.all.size].id
            nonSequential = nonSequential || next != sequential
            seen += next
            current = next
        }
        assertEquals(GamePalettes.all.map { it.id }.toSet(), seen)
        assertTrue(nonSequential)
    }

    @Test fun everyFruitAvoidsEverySpawnAndNextPreview() {
        for (width in listOf(240f, 320f, 411f, 600f, 1000f))
            for (height in listOf(320f, 600f, 900f))
                for (headerHeight in listOf(48f, 72f))
                    for (count in 1..8) {
                        val placement = fruitPlacement(width, height, count, width * .6f, 40f, headerHeight)
                        assertEquals(HUD_HORIZONTAL_MARGIN, width - placement.left - FRUIT_SIZE, .001f)
                        repeat(count) { index ->
                            val x = placement.left
                            val y = placement.top + index * (FRUIT_SIZE + FRUIT_GAP)
                            val fruit = Rect(x, y, x + FRUIT_SIZE, y + FRUIT_SIZE)
                            assertTrue(fruit.left >= 0 && fruit.right <= width && fruit.bottom <= height)
                            assertTrue(fruit.top >= headerHeight)
                            assertFalse(fruit.overlaps(Rect(HUD_HORIZONTAL_MARGIN, 3f,
                                width * .6f + HUD_HORIZONTAL_MARGIN, 43f)))
                            Tetromino.entries.forEach { assertFalse(fruit.overlaps(pieceBounds(Piece(it), width,
                                height))) }
                        }
                    }
    }
}
