package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordHistoryTest {
    private fun result(id: Int, score: Int, mode: Difficulty? = null, version: Int = 4) =
        GameResult("$id", id.toLong(), "Игрок", score, 0, 1, 0, version, difficulty = mode)

    @Test fun onlyStrictChronologicalRecordsWithinEachGroup() {
        val history = listOf(result(1, 100), result(2, 50), result(3, 100), result(4, 200),
            result(5, 20, Difficulty.EASY), result(6, 10, Difficulty.HARD), result(7, 15, Difficulty.HARD),
            result(8, 5, version = 3)).reversed()
        assertEquals(listOf("8", "7", "6", "5", "4", "1"), recordHistory(history).map { it.id })
        assertEquals(8, history.size)
    }

    @Test fun equalTimestampsRetainReverseInputTieOrder() {
        val earlier = result(1, 100).copy(dateMillis = 10)
        val later = result(2, 200).copy(dateMillis = 10)
        assertEquals(listOf(later, earlier), recordHistory(listOf(later, earlier)))
        assertEquals(listOf(later), recordHistory(listOf(earlier, later)))
    }

    @Test fun repeatedRecordIdsKeepTheFirstInputEntry() {
        val original = result(1, 100)
        val between = result(2, 150)
        val latest = original.copy(dateMillis = 3, name = "Updated", score = 200)
        assertEquals(listOf(latest, between), recordHistory(listOf(latest, between, original)))
    }
}
