package ru.itoltec.swypetris

import android.app.Application
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** History reuse must preserve migration, record updates and statistics reset. */
class HistoryCacheTest {
    @get:Rule val storage = IsolatedStorageRule()

    private class CountingPreferences(private val actual: SharedPreferences) : SharedPreferences by actual {
        var historyReads = 0
        override fun getString(key: String?, defValue: String?): String? {
            if (key == "results_v2") historyReads++
            return actual.getString(key, defValue)
        }
    }

    @Test fun initializationReadsHistoryOnceAndPreservesLegacyRulesAndMalformedRowHandling() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val actual = GameStorage.preferences(app)
        val old = GameResult("old", 1, "Previous", 500, 0, 1, 0, rulesVersion = 2)
        val current = GameResult("current", 2, "Current", 1000, 0, 1, 0)
        ResultStore(actual).write(listOf(current, old))
        val encoded = actual.getString("results_v2", "[]")!!
        actual.edit().putString("results_v2", encoded.dropLast(1) + ",{}]")
            .putInt("record_v3", 700).commit()
        val counting = CountingPreferences(actual)
        GameStorage.testPreferences = counting
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = GameViewModel(app, GameState(active = Piece(Tetromino.T), next = Tetromino.O),
                { 1000L }, false)
            assertEquals(1, counting.historyReads)
            assertEquals(listOf(current, old), model.results)
            assertEquals(700L, model.legacyRecord)
            assertEquals(1000L, model.record)
            assertEquals(GameStorage.legacyRecord(actual), model.legacyRecord)
        }
    }

    @Test fun cachedHistoryChangesAfterNewRecordRenameAndResetButNotUnrelatedSettings() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val previous = GameResult("previous", 1, "Previous", 100, 0, 1, 0)
        ResultStore(GameStorage.preferences(app)).write(listOf(previous))
        val board = BoardGeometry.empty().map { it.toMutableList() }
        board[BoardGeometry.row(0)][4] = Tetromino.Z
        val state = GameState(board = board, active = Piece(Tetromino.O, x = 0, y = 18),
            next = Tetromino.O, score = 200)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = GameViewModel(app, state, { 1000L }, false)
            val initial = model.recordResults
            assertEquals(listOf(previous), initial)
            assertSame(initial, model.recordResults)
            model.options.setSound(false)
            assertSame(initial, model.recordResults)

            model.input.command(GameCommand.HARD_DROP)
            assertEquals(GameScreen.RECORD, model.screen)
            val withRecord = model.recordResults
            assertNotSame(initial, withRecord)
            assertEquals(listOf(200L, 100L), withRecord.map { it.score })
            assertSame(withRecord, model.recordResults)

            model.statistics.saveRecordName("Updated")
            val renamed = model.recordResults
            assertNotSame(withRecord, renamed)
            assertEquals("Updated", renamed.first().name)
            assertEquals("Previous", renamed.last().name)
            assertSame(renamed, model.recordResults)

            model.statistics.resetStatistics()
            assertTrue(model.recordResults.isEmpty())
            assertTrue(ResultStore(GameStorage.preferences(app)).read().isEmpty())
        }
    }
}
