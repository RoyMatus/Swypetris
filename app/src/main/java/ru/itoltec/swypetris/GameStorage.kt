package ru.itoltec.swypetris

import android.app.Application
import android.content.SharedPreferences

/** Selects preference stores; instrumentation tests inject isolated settings before Activity launch. */
object GameStorage {
    internal var testPreferences: SharedPreferences? = null
    // This one-time reset belongs to the approved balance change, not every future rules version.
    internal const val BALANCE_RESET = "rules_7_balance_reset_completed"

    /** Returns user preferences or the explicitly injected test store. */
    fun preferences(application: Application): SharedPreferences =
        testPreferences ?: application.getSharedPreferences("swypetris", 0)

    /** Returns the separate session store or the isolated test store when one is injected. */
    internal fun sessionPreferences(application: Application): SharedPreferences =
        testPreferences ?: application.getSharedPreferences("swypetris_session", 0)

    /** Resets pre-balance results/session once, then initializes the current-version record. */
    fun migrate(preferences: SharedPreferences, sessionPreferences: SharedPreferences = preferences) {
        if (!preferences.getBoolean(BALANCE_RESET, false)) {
            SessionStore(sessionPreferences).clearForMigration()
            clearStatistics(preferences)
            if (!preferences.edit().putBoolean(BALANCE_RESET, true).commit()) {
                // A failed disk write may have changed memory; never let it suppress a retry.
                preferences.edit().remove(BALANCE_RESET).commit()
                error("Не удалось сохранить сброс предыдущих результатов")
            }
        }
        val migration = "rules_${GameRules.VERSION}_migrated"
        val record = "record_v${GameRules.VERSION}"
        if (preferences.getBoolean(migration, false)) return
        val editor = preferences.edit()
        if (!preferences.contains(record)) editor.putInt(record, 0)
        check(editor.putBoolean(migration, true).commit()) { "Не удалось сохранить миграцию правил" }
    }

    /** Returns the best score from stored record keys and results saved under older rule versions. */
    fun legacyRecord(preferences: SharedPreferences): Int = legacyRecord(preferences, ResultStore(preferences).read())

    /** Reuses an already loaded history while retaining every legacy preference-key rule. */
    internal fun legacyRecord(preferences: SharedPreferences, results: List<GameResult>): Int = maxOf(
        preferences.getInt("record", 0), preferences.getInt("legacy_record", 0),
        (2 until GameRules.VERSION).maxOfOrNull { preferences.getInt("record_v$it", 0) } ?: 0,
        results.filter { it.rulesVersion < GameRules.VERSION }.maxOfOrNull { it.score } ?: 0
    )

    /** Removes result history and every legacy record key in one preferences transaction. */
    fun clearStatistics(preferences: SharedPreferences) {
        val editor = preferences.edit()
        preferences.all.keys.filter { it == "record" || it == "legacy_record" ||
            it.matches(Regex("record_v\\d+|results_v\\d+")) }.forEach(editor::remove)
        check(editor.commit()) { "Не удалось удалить статистику" }
    }
}
