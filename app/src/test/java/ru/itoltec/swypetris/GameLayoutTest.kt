package ru.itoltec.swypetris

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class GameLayoutTest {
    @Test fun everyFruitAvoidsEverySpawnAndNextPreview() {
        for (width in listOf(240f, 320f, 411f, 600f, 1000f))
            for (height in listOf(320f, 600f, 900f))
                for (next in Tetromino.entries)
                    for (count in 1..8)
                    for (rightInset in listOf(0f, 24f)) {
                        val placement = fruitPlacement(width, height, count, next, true, width * .6f, 40f, rightInset)
                        assertEquals(width - rightInset - FRUIT_SIZE - FRUIT_GAP, placement.left, .001f)
                        repeat(count) { index ->
                            val x = placement.left
                            val y = placement.top + index * (FRUIT_SIZE + FRUIT_GAP)
                            val fruit = Rect(x, y, x + FRUIT_SIZE, y + FRUIT_SIZE)
                            assertTrue(fruit.left >= 0 && fruit.right <= width && fruit.bottom <= height)
                            assertFalse(fruit.overlaps(Rect(4f, 3f, width * .6f + 4, 43f)))
                            Tetromino.entries.forEach { assertFalse(fruit.overlaps(pieceBounds(Piece(it), width, height))) }
                        }
                    }
    }
}
