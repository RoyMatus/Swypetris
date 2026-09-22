package ru.itoltec.swypetris

import android.app.Application
import android.content.SharedPreferences

/** Выбирает хранилище; инструментальные проверки внедряют отдельные настройки до запуска Activity. */
object GameStorage {
    internal var testPreferences: SharedPreferences? = null

    /** Возвращает пользовательские настройки либо явно внедрённое тестовое хранилище. */
    fun preferences(application: Application): SharedPreferences =
        testPreferences ?: application.getSharedPreferences("swypetris", 0)

    /** Инициализирует рекорд новой версии один раз, сохраняя все прежние ключи и историю. */
    fun migrate(preferences: SharedPreferences) {
        if (preferences.getBoolean("rules_4_migrated", false)) return
        val editor = preferences.edit()
        if (!preferences.contains("record_v4")) editor.putInt("record_v4", 0)
        check(editor.putBoolean("rules_4_migrated", true).commit()) { "Не удалось сохранить миграцию правил" }
    }

    /** Возвращает лучший прежний результат, не смешивая его с текущими правилами. */
    fun legacyRecord(preferences: SharedPreferences): Int = maxOf(
        preferences.getInt("record", 0), preferences.getInt("legacy_record", 0),
        preferences.getInt("record_v2", 0),
        preferences.getInt("record_v3", 0),
        ResultStore(preferences).read().filter { it.rulesVersion < GameRules.VERSION }.maxOfOrNull { it.score } ?: 0
    )
}
