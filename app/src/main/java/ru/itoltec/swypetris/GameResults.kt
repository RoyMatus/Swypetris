package ru.itoltec.swypetris

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Фруктовые награды: первые два приза следуют Brick Game, остальные — последовательность Swypetris. */
enum class Fruit(val title: String) {
    CHERRY("Вишня"), BANANA("Банан"), GRAPES("Виноград"), STRAWBERRY("Клубника"),
    APPLE("Яблоко"), PEAR("Груша"), PINEAPPLE("Ананас"), WATERMELON("Арбуз")
}

/** Количество призов растёт каждые 10 000 очков, включая пересечение нескольких порогов. */
fun fruitCount(score: Int): Int = score.coerceAtLeast(0) / GameRules.FRUIT_STEP

/** Возвращает количество конкретного фрукта в циклической коллекции партии. */
fun fruitQuantity(score: Int, fruit: Fruit): Int =
    (fruitCount(score) / Fruit.entries.size) + if (fruit.ordinal < fruitCount(score) % Fruit.entries.size) 1 else 0

/** Итог партии с версией правил; время содержит только активную игру, прежние версии не конкурируют с новой. */
data class GameResult(
    val id: String,
    val dateMillis: Long,
    val name: String,
    val score: Int,
    val lines: Int,
    val level: Int,
    val durationMillis: Long,
    val rulesVersion: Int = GameRules.VERSION,
    val completedRounds: Int = 0
)

/** Локальная история без ограничения числа партий; идентификатор защищает от повторной записи. */
class ResultStore(private val preferences: SharedPreferences) {
    /** Читает сохранённую историю; повреждённые элементы пропускаются по одному. */
    fun read(): List<GameResult> = runCatching {
        val array = JSONArray(preferences.getString("results_v2", "[]"))
        (0 until array.length()).mapNotNull { index -> runCatching {
            val row = array.getJSONObject(index)
            GameResult(row.getString("id"), row.getLong("date"), row.getString("name"),
                row.getInt("score"), row.getInt("lines"), row.getInt("level"), row.getLong("duration"), row.optInt("rulesVersion", 2), row.optInt("completedRounds", 0))
        }.getOrNull() }
    }.getOrDefault(emptyList())

    /** Сохраняет снимок истории; используется также после изменения имени рекордсмена. */
    fun write(results: List<GameResult>) {
        val array = JSONArray()
        results.distinctBy { it.id }.forEach { result ->
            array.put(JSONObject().put("id", result.id).put("date", result.dateMillis)
                .put("name", result.name).put("score", result.score).put("lines", result.lines)
                .put("level", result.level).put("duration", result.durationMillis).put("rulesVersion", result.rulesVersion)
                .put("completedRounds", result.completedRounds))
        }
        preferences.edit().putString("results_v2", array.toString()).apply()
    }
}

