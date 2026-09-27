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
        if (preferences.getBoolean("rules_4_migrated", false)) return
        val editor = preferences.edit()
        if (!preferences.contains("record_v4")) editor.putInt("record_v4", 0)
        check(editor.putBoolean("rules_4_migrated", true).commit()) { "Не удалось сохранить миграцию правил" }
    }

    /** Returns the best score from stored record keys and results saved under older rule versions. */
    fun legacyRecord(preferences: SharedPreferences): Int = maxOf(
        preferences.getInt("record", 0), preferences.getInt("legacy_record", 0),
        preferences.getInt("record_v2", 0),
        preferences.getInt("record_v3", 0),
        preferences.getInt("record_v4", 0),
        ResultStore(preferences).read().filter { it.rulesVersion < GameRules.VERSION }.maxOfOrNull { it.score } ?: 0
    )
}
