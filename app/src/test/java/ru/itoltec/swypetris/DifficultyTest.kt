package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test

/** Legacy IDs and titles remain usable when reading historical records. */
class DifficultyTest {
    @Test fun historicalMetadataIsRetained() {
        assertEquals(Difficulty.EASY, Difficulty.find("easy"))
        assertEquals(Difficulty.MEDIUM, Difficulty.find("medium"))
        assertEquals(Difficulty.HARD, Difficulty.find("hard"))
        assertEquals("Сложная", Difficulty.HARD.title)
        assertNull(Difficulty.find(null))
        assertNull(Difficulty.find("unknown"))
    }
}
