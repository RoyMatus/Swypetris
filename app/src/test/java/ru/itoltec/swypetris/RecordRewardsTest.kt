package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Recorded awards use known rule compatibility without consulting the current session. */
class RecordRewardsTest {
    private fun result(score: Int, version: Int = GameRules.VERSION, rounds: Int = 0) =
        GameResult("record", 100L, "Игрок", score.toLong(), 0, 1, 0L, version, completedRounds = rounds.toLong())

    @Test fun knownCyclicRecordsMatchSharedAwardsRegardlessOfCompletedRounds() {
        for (version in 4..GameRules.VERSION) for (score in listOf(2466, 42500, 80000, 90000, 788267, 999999)) {
            val expected = Fruit.entries.map { fruitQuantity(score.toLong(), it) }
            assertEquals(expected, recordFruitQuantities(result(score, version)))
            assertEquals(expected, recordFruitQuantities(result(score, version, rounds = 12)))
        }
    }

    @Test fun legacySingleCollectionsStayCappedAndUnknownVersionsStayUnknown() {
        for (version in 2..3) {
            assertEquals(listOf(1L, 1L, 1L, 1L, 0L, 0L, 0L, 0L), recordFruitQuantities(result(42500, version)))
            assertEquals(List(8) { 1L }, recordFruitQuantities(result(788267, version)))
        }
        for (version in listOf(-1, 0, 1, GameRules.VERSION + 1, Int.MAX_VALUE)) {
            assertNull(recordFruitQuantities(result(788267, version)))
        }
    }

    @Test fun quantitiesBelongToEachRecordAndNegativeScoresAwardNothing() {
        val low = result(2466)
        val high = result(90000).copy(id = "other")
        assertEquals(List(8) { 0L }, recordFruitQuantities(low))
        assertEquals(listOf(2L, 1L, 1L, 1L, 1L, 1L, 1L, 1L), recordFruitQuantities(high))
        assertEquals(List(8) { 0L }, recordFruitQuantities(low))
        assertEquals(List(8) { 0L }, recordFruitQuantities(result(Int.MIN_VALUE)))
    }
}
