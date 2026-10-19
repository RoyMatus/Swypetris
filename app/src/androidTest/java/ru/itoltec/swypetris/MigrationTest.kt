package ru.itoltec.swypetris

import android.app.Application
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Approved balance reset removes old progress once, retaining settings and later saves. */
class MigrationTest {
    @get:Rule val storage = IsolatedStorageRule(migrated = false)
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val preferences get() = GameStorage.preferences(app)

    private fun snapshot() = GameSession("saved", GameState(active = Piece(Tetromino.T), next = Tetromino.O),
        emptyList(), 0, 0, GameRules.gravityNanos(1), 0)

    private fun seedOld(preferences: SharedPreferences) {
        ResultStore(preferences).write(listOf(GameResult("old", 123, "Roy", 12000, 30, 4, 999, rulesVersion = 6)))
        preferences.edit().putString("results_v1", "legacy")
            .putInt("record", 12000).putInt("legacy_record", 17000).putInt("record_v1", 20000)
            .putInt("record_v6", 31000).putInt("record_v999", 40000)
            .putBoolean("rules_6_migrated", true).commit()
    }

    @Test fun separateStoresResetOnceAndPreserveSettingsAndNewProgress() {
        val settings = preferences
        val sessions = app.getSharedPreferences("migration_session_${java.util.UUID.randomUUID()}", 0)
        try {
            seedOld(settings)
            settings.edit().putBoolean("music", false).putBoolean("sound", false)
                .putBoolean("vibration", true).putInt("vibration_strength", 60).putBoolean("hints", true)
                .putString("player_name", "Roy").putString("palette", "github_light")
                .putBoolean("random_theme", true).putInt("starting_level", 15)
                .putString("background_path", "private-photo").putLong("update_last_checked", 123L).commit()
            val retained = settings.all.filterKeys {
                it !in listOf("record", "legacy_record") && !it.startsWith("record_v") && !it.startsWith("results_v")
            }
            SessionStore(sessions).write(snapshot())
            sessions.edit().putString("unrelated", "keep").commit()
            GameStorage.migrate(settings, sessions)
            assertNull(SessionStore(sessions).read())
            assertTrue(ResultStore(settings).read().isEmpty())
            assertEquals(0L, GameStorage.legacyRecord(settings))
            assertEquals(0, settings.getInt("record_v${GameRules.VERSION}", -1))
            retained.forEach { (key, value) -> assertEquals(value, settings.all[key]) }
            assertEquals("keep", sessions.getString("unrelated", null))
            assertFalse(settings.contains("record_v999"))
            assertFalse(settings.contains("results_v1"))

            val fresh = GameResult("new", 456, "Roy", 2000, 2, 2, 500)
            ResultStore(settings).write(listOf(fresh))
            settings.edit().putInt("record_v${GameRules.VERSION}", 2000).commit()
            val session = snapshot().copy(id = "new")
            SessionStore(sessions).write(session)
            repeat(3) { GameStorage.migrate(settings, sessions) }
            // Later rules initialization must not turn the fixed reset into a recurring wipe.
            settings.edit().remove("rules_${GameRules.VERSION}_migrated").commit()
            GameStorage.migrate(settings, sessions)
            assertEquals(listOf(fresh), ResultStore(settings).read())
            assertEquals(2000, settings.getInt("record_v${GameRules.VERSION}", -1))
            assertEquals(session, SessionStore(sessions).read())
        } finally {
            sessions.edit().clear().commit()
        }
    }

    @Test fun modelDropsOldContinueButRestoresNewGameAfterRestart() {
        seedOld(preferences)
        SessionStore(preferences).write(snapshot())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val first = GameViewModel(app, null, { 1000L }, false)
            assertNull(first.game)
            assertTrue(first.results.isEmpty())
            assertEquals(0L, first.record)
            first.newGame()
            first.input.command(GameCommand.LEFT)
            first.pause()
            val saved = first.game
            val restarted = GameViewModel(app, null, { 2000L }, false)
            assertEquals(saved, restarted.game)
            restarted.resume()
            assertEquals(GameScreen.PLAYING, restarted.screen)
            assertEquals(saved, restarted.game)
        }
    }

    /** Failed durable commits may nevertheless change the in-memory values. */
    private class FailOnce(private val actual: SharedPreferences, private val failAt: Int)
        : SharedPreferences by actual {
        private var commits = 0
        override fun edit(): SharedPreferences.Editor {
            val editor = actual.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                    editor.putBoolean(key, value)
                    return this
                }
                override fun remove(key: String?): SharedPreferences.Editor {
                    editor.remove(key)
                    return this
                }
                override fun commit(): Boolean {
                    val saved = editor.commit()
                    commits++
                    return saved && commits != failAt
                }
            }
        }
    }

    @Test fun failedSessionCommitDoesNotMarkResetCompleteAndCanRetry() {
        seedOld(preferences)
        SessionStore(preferences).write(snapshot())
        val sessions = FailOnce(preferences, 1)
        assertThrows(IllegalStateException::class.java) { GameStorage.migrate(preferences, sessions) }
        assertFalse(preferences.getBoolean(GameStorage.BALANCE_RESET, false))
        assertTrue(preferences.contains("record_v6"))
        GameStorage.migrate(preferences, sessions)
        assertNull(SessionStore(preferences).read())
        assertFalse(preferences.contains("record_v6"))
    }

    @Test fun failedStatisticsAndMarkerCommitsLeaveResetRetryable() {
        for (failedCommit in 1..2) {
            preferences.edit().clear().commit()
            seedOld(preferences)
            val settings = FailOnce(preferences, failedCommit)
            assertThrows(IllegalStateException::class.java) { GameStorage.migrate(settings, preferences) }
            assertFalse(preferences.getBoolean(GameStorage.BALANCE_RESET, false))
            seedOld(preferences)
            GameStorage.migrate(settings, preferences)
            assertFalse(preferences.contains("record_v6"))
            assertTrue(preferences.getBoolean(GameStorage.BALANCE_RESET, false))
        }
    }
}
