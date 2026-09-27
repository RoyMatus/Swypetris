package ru.itoltec.swypetris

import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Uptime itself is never persisted: all clocks are elapsed or remaining game time. */
internal data class GameSession(
    val id: String,
    val state: GameState,
    val bag: List<Tetromino>,
    val playedMillis: Long,
    val clearMillis: Long,
    val gravityRemaining: Long,
    val recordAtStart: Int,
    val finishedAt: Long = 0L
)

/** SharedPreferences applies ordered, atomic file replacements off the UI thread.
 * Android drains pending writes at Activity lifecycle transitions, including explicit exit.
 * A terminal snapshot journals the result; its stable ID makes recovery idempotent.
 */
internal class SessionStore(private val preferences: SharedPreferences) {
    private var lastWritten: GameSession? = null
    private var cachedBoard: List<List<Tetromino?>>? = null
    private var encodedBoard = ""

    fun write(session: GameSession) {
        if (session == lastWritten) return
        // GameState owns immutable boards; movement keeps the same board instance.
        if (cachedBoard !== session.state.board) {
            encodedBoard = encodeBoard(session.state.board)
            cachedBoard = session.state.board
        }
        preferences.edit().putString(KEY, encode(session, encodedBoard)).apply()
        lastWritten = session
    }

    fun read(): GameSession? {
        val json = preferences.getString(KEY, null) ?: return null
        return try { decode(json) }
        catch (error: JSONException) { invalid(error); null }
        catch (error: IllegalArgumentException) { invalid(error); null }
    }

    private fun invalid(error: Exception) {
        Log.w("Swypetris", "Invalid saved game; settings and records were retained", error)
        preferences.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "session_v1"
        private fun encodeBoard(board: List<List<Tetromino?>>): String = JSONArray().apply {
            board.forEach { row -> put(JSONArray(row.map { it?.name ?: "" })) }
        }.toString()

        internal fun encode(session: GameSession): String = encode(session, encodeBoard(session.state.board))

        private fun encode(session: GameSession, board: String): String {
            val s = session.state
            return JSONObject().put("version", 1).put("id", session.id)
                .put("active", JSONObject().put("type", s.active.type.name)
                    .put("x", s.active.x).put("y", s.active.y).put("rotation", s.active.rotation))
                .put("next", s.next.name).put("bag", JSONArray(session.bag.map { it.name }))
                .put("score", s.score).put("lines", s.lines).put("generation", s.generation)
                .put("gameOver", s.gameOver).put("clearingRows", JSONArray(s.clearingRows))
                .put("completedClears", s.completedClears).put("accelerated", s.accelerated)
                .put("completedRounds", s.completedRounds).put("victoryPending", s.victoryPending)
                .put("difficulty", s.difficulty.id).put("playedMillis", session.playedMillis)
                .put("clearMillis", session.clearMillis).put("gravityRemaining", session.gravityRemaining)
                .put("recordAtStart", session.recordAtStart).put("finishedAt", session.finishedAt).toString()
                .dropLast(1) + ",\"board\":" + board + "}"
        }

        internal fun decode(json: String): GameSession {
            val root = JSONObject(json)
            require(root.getInt("version") == 1)
            val rows = root.getJSONArray("board")
            require(rows.length() == 20)
            val board = List(20) { y ->
                val row = rows.getJSONArray(y)
                require(row.length() == 10)
                List(10) { x -> row.getString(x).let { if (it.isEmpty()) null else Tetromino.valueOf(it) } }
            }
            val active = root.getJSONObject("active")
            val piece = Piece(Tetromino.valueOf(active.getString("type")), active.getInt("x"),
                active.getInt("y"), active.getInt("rotation"))
            require(piece.rotation in 0..3 && piece.x in -3..9 && piece.y in 0..19)
            require(piece.cells().all { it.x in 0..9 && it.y in 0..19 })
            val clearing = root.getJSONArray("clearingRows").let { a -> List(a.length()) { a.getInt(it) } }
            require(clearing.size <= 4 && clearing.distinct().size == clearing.size && clearing.all { it in 0..19 && board[it].all { cell -> cell != null } })
            val difficulty = requireNotNull(Difficulty.find(root.getString("difficulty")))
            val state = GameState(board, piece, Tetromino.valueOf(root.getString("next")),
                root.getInt("score"), root.getInt("lines"), root.getInt("generation"), root.getBoolean("gameOver"),
                clearing, root.getInt("completedClears"), root.getBoolean("accelerated"),
                root.getInt("completedRounds"), root.getBoolean("victoryPending"), difficulty)
            require(state.score >= 0 && state.lines >= 0 && state.generation >= 0 && state.completedClears >= 0)
            require(state.completedRounds in 0..(state.score / GameRules.ROUND_SCORE))
            require(!state.victoryPending || (!state.gameOver && clearing.isEmpty() && state.roundFruits == 8))
            require(state.gameOver || state.victoryPending || clearing.isNotEmpty() || GameEngine().fits(state, piece))
            val bag = root.getJSONArray("bag").let { a -> List(a.length()) { Tetromino.valueOf(a.getString(it)) } }
            require(bag.size <= 7 && bag.distinct().size == bag.size)
            val session = GameSession(root.getString("id"), state, bag, root.getLong("playedMillis"),
                root.getLong("clearMillis"), root.getLong("gravityRemaining"), root.getInt("recordAtStart"), root.getLong("finishedAt"))
            require(session.id.isNotBlank() && session.playedMillis >= 0 && session.recordAtStart >= 0)
            require(session.clearMillis in 0..LineClearAnimation.TOTAL_MILLIS)
            require(session.gravityRemaining in 0..Difficulty.INITIAL_MILLIS)
            require(!state.gameOver || session.finishedAt > 0)
            return session
        }
    }
}
