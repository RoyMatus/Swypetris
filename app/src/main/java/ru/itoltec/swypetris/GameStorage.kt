package ru.itoltec.swypetris

import android.app.Application
import android.content.SharedPreferences

/** Selects preference stores; instrumentation tests inject isolated settings before Activity launch. */
object GameStorage {
    internal var testPreferences: SharedPreferences? = null

    /** Returns user preferences or the explicitly injected test store. */
    fun preferences(application: Application): SharedPreferences =
        testPreferences ?: application.getSharedPreferences("swypetris", 0)

    /** Returns the separate session store or the isolated test store when one is injected. */
    internal fun sessionPreferences(application: Application): SharedPreferences =
        testPreferences ?: application.getSharedPreferences("swypetris_session", 0)

    /** Initializes the current-version record once while retaining legacy keys and history. */
    fun migrate(preferences: SharedPreferences) {
        val migration = "rules_${GameRules.VERSION}_migrated"
        val record = "record_v${GameRules.VERSION}"
        if (preferences.getBoolean(migration, false)) return
        val editor = preferences.edit()
        if (!preferences.contains(record)) editor.putInt(record, 0)
        check(editor.putBoolean(migration, true).commit()) { "Не удалось сохранить миграцию правил" }
    }

    /** Returns the best score from stored record keys and results saved under older rule versions. */
    fun legacyRecord(preferences: SharedPreferences): Int = maxOf(
        preferences.getInt("record", 0), preferences.getInt("legacy_record", 0),
        (2 until GameRules.VERSION).maxOfOrNull { preferences.getInt("record_v$it", 0) } ?: 0,
        ResultStore(preferences).read().filter { it.rulesVersion < GameRules.VERSION }.maxOfOrNull { it.score } ?: 0
    )

    /** Removes result history and every legacy record key in one preferences transaction. */
    fun clearStatistics(preferences: SharedPreferences) {
        val editor = preferences.edit().remove("results_v2")
        (listOf("record", "legacy_record") + (2..GameRules.VERSION).map { "record_v$it" })
            .forEach(editor::remove)
        check(editor.commit()) { "Не удалось удалить статистику" }
    }
}
