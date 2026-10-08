package ru.itoltec.swypetris

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Fruit awards cycle through the collection; the first two follow Brick Game, the rest Swypetris. */
enum class Fruit(val title: String) {
    CHERRY("Вишня"), BANANA("Банан"), GRAPES("Виноград"), STRAWBERRY("Клубника"),
    APPLE("Яблоко"), PEAR("Груша"), PINEAPPLE("Ананас"), WATERMELON("Арбуз")
}

/** Counts awards earned at each fruit threshold, including multiple thresholds crossed at once. */
fun fruitCount(score: Int): Int = score.coerceAtLeast(0) / GameRules.FRUIT_STEP

/** Returns how many times a particular [fruit] has been earned in this game. */
fun fruitQuantity(score: Int, fruit: Fruit): Int =
    (fruitCount(score) / Fruit.entries.size) + if (fruit.ordinal < fruitCount(score) % Fruit.entries.size) 1 else 0

/** Final game result with a rules version; active play time excludes pauses,
    and old rules do not compete with new ones. */
data class GameResult(
    val id: String,
    val dateMillis: Long,
    val name: String,
    val score: Int,
    val lines: Int,
    val level: Int,
    val durationMillis: Long,
    val rulesVersion: Int = GameRules.VERSION,
    val completedRounds: Int = 0,
    val difficulty: Difficulty? = null
)

/** Unbounded local result history; stable IDs prevent duplicate writes. */
class ResultStore(private val preferences: SharedPreferences) {
    /** Reads saved results, skipping malformed entries individually. */
    fun read(): List<GameResult> = runCatching {
        val array = JSONArray(preferences.getString("results_v2", "[]"))
        (0 until array.length()).mapNotNull { index -> runCatching {
            val row = array.getJSONObject(index)
            GameResult(row.getString("id"), row.getLong("date"), row.getString("name"),
                row.getInt("score"), row.getInt("lines"), row.getInt("level"), row.getLong("duration"),
                    row.optInt("rulesVersion", 2), row.optInt("completedRounds", 0),
                Difficulty.find(row.optString("difficulty")))
        }.getOrNull() }
    }.getOrDefault(emptyList())

    /** Writes the current history snapshot, including edits to a record holder's name. */
    fun write(results: List<GameResult>) {
        val array = JSONArray()
        results.distinctBy { it.id }.forEach { result ->
            array.put(JSONObject().put("id", result.id).put("date", result.dateMillis)
                .put("name", result.name).put("score", result.score).put("lines", result.lines)
                .put("level", result.level).put("duration", result.durationMillis).put("rulesVersion",
                    result.rulesVersion)
                .put("completedRounds", result.completedRounds).put("difficulty", result.difficulty?.id))
        }
        preferences.edit().putString("results_v2", array.toString()).apply()
    }
}

/** Old storage stays intact; only chronological personal bests are displayed. */
fun recordHistory(results: List<GameResult>): List<GameResult> {
    val best = mutableMapOf<Pair<Int, Difficulty?>, Int>()
    val ids = mutableSetOf<String>()
    results.asReversed().sortedBy { it.dateMillis }.forEach { result ->
        val key = result.rulesVersion to result.difficulty
        if (result.score > (best[key] ?: 0)) {
            best[key] = result.score
            ids += result.id
        }
    }
    return results.filter { it.id in ids }.distinctBy { it.id }
}
