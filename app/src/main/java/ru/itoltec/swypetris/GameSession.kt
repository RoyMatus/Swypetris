package ru.itoltec.swypetris

import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val SESSION_FORMAT_VERSION = 4


/** Uptime itself is never persisted: all clocks are elapsed or remaining game time. */
internal data class GameSession(
    val id: String,
    val state: GameState,
    val bag: List<Tetromino>,
    val playedMillis: Long,
    val clearMillis: Long,
    val gravityRemainingNanos: Long,
    val recordAtStart: Int,
    val finishedAt: Long = 0L,
    val lockFractionNanos: Long = 0L,
    val clearFractionNanos: Long = 0L
)

/** SharedPreferences applies ordered, atomic file replacements off the UI thread.
 * Android drains pending writes at Activity lifecycle transitions, including explicit exit.
 * A terminal snapshot journals the result; its stable ID makes recovery idempotent.
 */
internal class SessionStore(private val preferences: SharedPreferences) {
    private var lastWritten: GameSession? = null
    private var cachedBoard: List<List<Tetromino?>>? = null
    private var encodedBoard = ""

    /**
     * Persists a changed [session] asynchronously. Reuses the encoded board while its immutable
     * board instance is unchanged, so piece movement need not serialize all 400 cells again.
     */
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

    /** Reads and validates the saved snapshot, or returns null after discarding invalid JSON. */
    fun read(): GameSession? {
        val json = preferences.getString(KEY, null) ?: return null
        return try { decode(json) }
        catch (error: JSONException) { invalid(error); null }
        catch (error: IllegalArgumentException) { invalid(error); null }
    }

    /** Logs a corrupt snapshot and removes only the session key, retaining settings and records. */
    private fun invalid(error: Exception) {
        Log.w("Swypetris", "Invalid saved game; settings and records were retained", error)
        preferences.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "session_v1"
        /** Serializes the full logical board as piece names, using empty strings for vacant cells. */
        private fun encodeBoard(board: List<List<Tetromino?>>): String = JSONArray().apply {
            board.forEach { row -> put(JSONArray(row.map { it?.name ?: "" })) }
        }.toString()

        /** Encodes a complete snapshot, including its current board, for storage or tests. */
        internal fun encode(session: GameSession): String = encode(session, encodeBoard(session.state.board))

        /** Combines session metadata with a previously encoded board to avoid redundant work. */
        private fun encode(session: GameSession, board: String): String {
            val s = session.state
            return JSONObject().put("version", SESSION_FORMAT_VERSION).put("rulesVersion",
                GameRules.VERSION).put("topOut",
                s.topOut?.name ?: "").put("id", session.id)
                .put("active", JSONObject().put("type", s.active.type.name)
                    .put("x", s.active.x).put("y", s.active.y).put("rotation", s.active.rotation))
                .put("next", s.next.name).put("bag", JSONArray(session.bag.map { it.name }))
                .put("startingLevel", s.startingLevel).put("score", s.score).put("lines",
                    s.lines).put("generation", s.generation)
                .put("lockRemaining", s.lockRemaining).put("lockResets", s.lockResets)
                .put("held", s.held?.name ?: "").put("holdUsed", s.holdUsed)
                .put("lastRotationKick", s.lastRotationKick).put("softDropCells", s.softDropCells)
                .put("hardDropCells", s.hardDropCells).put("backToBack", s.backToBack).put("combo", s.combo)
                .put("placement", s.placement?.let { p -> JSONObject().put("lines", p.lines)
                    .put("spin", p.spin.name).put("backToBack", p.backToBack).put("combo", p.combo)
                    .put("perfectClear", p.perfectClear).put("softDropCells", p.softDropCells)
                    .put("hardDropCells", p.hardDropCells).put("level", p.level) })
                .put("gameOver", s.gameOver).put("clearingRows", JSONArray(s.clearingRows))
                .put("completedClears", s.completedClears).put("accelerated", s.accelerated)
                .put("completedRounds", s.completedRounds).put("victoryPending", s.victoryPending)
                .put("playedMillis", session.playedMillis)
                .put("clearMillis", session.clearMillis).put("gravityRemainingNanos", session.gravityRemainingNanos)
                .put("lockFractionNanos", session.lockFractionNanos).put("clearFractionNanos",
                    session.clearFractionNanos)
                .put("recordAtStart", session.recordAtStart).put("finishedAt", session.finishedAt).toString()
                .dropLast(1) + ",\"board\":" + board + "}"
        }

        /**
         * Reconstructs a version-four session and validates board dimensions, piece placement,
         * clearing rows, bag contents, score, and clock bounds before gameplay can resume.
         * Throws for malformed or inconsistent data; [read] removes such snapshots safely.
         */
        internal fun decode(json: String): GameSession {
            val root = JSONObject(json)
            require(root.getInt("version") == SESSION_FORMAT_VERSION &&
                root.getInt("rulesVersion") == GameRules.VERSION)
            val board = readSessionBoard(root)
            val piece = readSessionPiece(root, board)
            val clearing = root.getJSONArray("clearingRows").let { a -> List(a.length()) { a.getInt(it) } }
            require(clearing.size <= GameRules.MAX_CLEAR_LINES && clearing.distinct().size == clearing.size &&
                clearing.all { it in board.indices && board[it].all { cell -> cell != null } })
            val state = GameState(board, piece, Tetromino.valueOf(root.getString("next")),
                root.getInt("score"), root.getInt("lines"), root.getInt("generation"), root.getBoolean("gameOver"),
                clearing, root.getInt("completedClears"), root.getBoolean("accelerated"),
                root.getInt("completedRounds"), root.getBoolean("victoryPending"),
                root.getString("topOut").let { if (it.isEmpty()) null else TopOut.valueOf(it) },
                root.optLong("lockRemaining", LockRules.DELAY_MILLIS), root.optInt("lockResets", 0),
                root.optString("held").let { if (it.isEmpty()) null else Tetromino.valueOf(it) },
                    root.optBoolean("holdUsed", false),
                root.optInt("lastRotationKick", -1), root.optInt("softDropCells", 0), root.optInt("hardDropCells", 0),
                root.optBoolean("backToBack", false), root.optInt("combo", -1),
                root.optJSONObject("placement")?.let { p -> PlacementResult(p.getInt("lines"),
                    Spin.valueOf(p.getString("spin")), p.getBoolean("backToBack"), p.getInt("combo"),
                    p.getBoolean("perfectClear"), p.getInt("softDropCells"), p.getInt("hardDropCells"),
                        p.getInt("level")) },
                startingLevel = if (root.has("startingLevel")) root.getInt("startingLevel") else 1)
            validateSessionState(state)
            val bag = root.getJSONArray("bag").let { a -> List(a.length()) { Tetromino.valueOf(a.getString(it)) } }
            require(bag.size <= Tetromino.entries.size && bag.distinct().size == bag.size)
            val session = GameSession(root.getString("id"), state, bag, root.getLong("playedMillis"),
                root.getLong("clearMillis"), root.getLong("gravityRemainingNanos"), root.getInt("recordAtStart"),
                    root.getLong("finishedAt"),
                root.getLong("lockFractionNanos"), root.getLong("clearFractionNanos"))
            validateSessionClocks(session)
            return session
        }
    }
}

private fun readSessionBoard(root: JSONObject): List<List<Tetromino?>> {
    val rows = root.getJSONArray("board")
    require(rows.length() == BoardGeometry.TOTAL_ROWS)
    val board = List(BoardGeometry.TOTAL_ROWS) { y ->
        val row = rows.getJSONArray(y)
        require(row.length() == BoardGeometry.WIDTH)
        List(BoardGeometry.WIDTH) { x -> row.getString(x).let { if (it.isEmpty()) null else Tetromino.valueOf(it) } }
    }
    return board
}

private fun readSessionPiece(root: JSONObject, board: List<List<Tetromino?>>): Piece {
    val active = root.getJSONObject("active")
    val piece = Piece(Tetromino.valueOf(active.getString("type")), active.getInt("x"),
        active.getInt("y"), active.getInt("rotation"))
    require(piece.rotation in RotationState.entries.indices &&
        piece.x in -(Tetromino.I.box - 1) until BoardGeometry.WIDTH &&
        piece.y in -BoardGeometry.HIDDEN_ROWS until BoardGeometry.VISIBLE_ROWS)
    require(piece.cells().all { it.x in 0 until BoardGeometry.WIDTH &&
        BoardGeometry.row(it.y) in board.indices })
    return piece
}

private fun validateSessionState(state: GameState) {
    val board = state.board
    val piece = state.active
    val clearing = state.clearingRows
    require(state.lastRotationKick in -1..LAST_ROTATION_KICK && state.softDropCells >= 0 &&
        state.hardDropCells in 0..BoardGeometry.TOTAL_ROWS && state.combo >= -1)
    validateSessionPlacement(state.placement)
    require(state.lockRemaining in 0..LockRules.DELAY_MILLIS && state.lockResets in 0..LockRules.MAX_RESETS)
    require(state.score >= 0 && state.lines >= 0 && state.generation >= 0 && state.completedClears >= 0)
    require(state.completedRounds in 0..(state.score / GameRules.ROUND_SCORE))
    require(!state.victoryPending || (!state.gameOver && clearing.isEmpty() &&
        state.roundFruits == Fruit.entries.size))
    require(state.gameOver || state.victoryPending || clearing.isNotEmpty() || GameEngine().fits(state, piece))
}

private fun validateSessionClocks(session: GameSession) {
    val state = session.state
    require(session.id.isNotBlank() && session.playedMillis >= 0 && session.recordAtStart >= 0)
    require(session.clearMillis in 0..LineClearAnimation.TOTAL_MILLIS)
    require(session.gravityRemainingNanos in 0..state.gravityNanos)
    require(session.lockFractionNanos in 0 until GameRules.NANOS_PER_MILLI)
    require(session.clearFractionNanos in 0 until GameRules.NANOS_PER_MILLI)
    require(state.lockRemaining > 0 || session.lockFractionNanos == 0L)
    require(session.clearMillis < LineClearAnimation.TOTAL_MILLIS || session.clearFractionNanos == 0L)
    require(!state.gameOver || session.finishedAt > 0)
}

private fun validateSessionPlacement(placement: PlacementResult?) {
    placement?.let { p -> require(p.lines in 0..GameRules.MAX_CLEAR_LINES && p.combo >= -1 &&
        p.level >= 1 &&
        p.softDropCells >= 0 && p.hardDropCells in 0..BoardGeometry.TOTAL_ROWS) }
}
