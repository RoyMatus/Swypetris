package ru.itoltec.swypetris

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Проверяет совместимость истории и однократность миграции в изолированном хранилище. */
class MigrationTest {
    @get:Rule val storage = IsolatedStorageRule()

    /** Прежние записи и настройки сохраняются, повторная миграция не сбрасывает новый рекорд. */
    @Test fun migrationPreservesHistoryAndCurrentRecord() {
        val preferences = GameStorage.preferences(ApplicationProvider.getApplicationContext<Application>())
        val oldJson = """[{"id":"old","date":123,"name":"Иван","score":12000,"lines":30,"level":4,"duration":999}]"""
        preferences.edit().putString("results_v2", oldJson).putInt("record_v2", 15000)
            .putInt("legacy_record", 17000).putInt("record_v3", 25000)
            .putBoolean("rules_3_migrated", true).putBoolean("music", false).putBoolean("sound", false)
            .putBoolean("vibration", true).putBoolean("hints", true).putString("player_name", "Иван").commit()
        GameStorage.migrate(preferences)
        assertEquals(oldJson, preferences.getString("results_v2", null))
        assertEquals(0, preferences.getInt("record_v4", -1))
        val store = ResultStore(preferences)
        val old = store.read().single()
        assertEquals(2, old.rulesVersion)
        assertEquals(25000, GameStorage.legacyRecord(preferences))
        val fresh = GameResult("new", 456, "Анна", 2000, 2, 2, 500)
        store.write(listOf(fresh, old))
        preferences.edit().putInt("record_v4", 2000).commit()
        repeat(3) { GameStorage.migrate(preferences) }
        assertEquals(2000, preferences.getInt("record_v4", -1))
        assertEquals(listOf(fresh, old), store.read())
        assertEquals("Иван", preferences.getString("player_name", null))
        assertFalse(preferences.getBoolean("music", true))
        assertFalse(preferences.getBoolean("sound", true))
        assertTrue(preferences.getBoolean("vibration", false))
        assertTrue(preferences.getBoolean("hints", false))
        assertEquals(15000, preferences.getInt("record_v2", -1))
        assertEquals(25000, preferences.getInt("record_v3", -1))
        assertTrue(preferences.getBoolean("rules_3_migrated", false))
    }
}

