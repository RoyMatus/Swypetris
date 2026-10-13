package ru.itoltec.swypetris

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class ConnectedSessionTest {
    @get:Rule val storage = IsolatedStorageRule()

    private fun session(): GameSession {
        val engine = GameEngine()
        val state = engine.apply(GameState(active = Piece(Tetromino.J, rotation = 3), next = Tetromino.O,
            held = Tetromino.S, score = 230, lines = 2), GameCommand.HARD_DROP)
        return GameSession("connected", state, listOf(Tetromino.L, Tetromino.Z), 1234, 0,
            state.gravityNanos / 2, 500, lockFractionNanos = 123)
    }

    @Test fun newFormatAndMetadataOnlyCacheChangesRoundTrip() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = SessionStore(GameStorage.sessionPreferences(app))
        val original = session()
        store.write(original)
        assertEquals(original, store.read())
        val appearances = original.state.blockAppearances.map { row -> row.map { it?.copy(quarterTurns = 1) } }
        val changed = original.copy(state = original.state.copy(blockAppearances = appearances))
        store.write(changed)
        assertEquals(changed, store.read())
        assertEquals(changed, SessionStore.decode(SessionStore.encode(changed)))
    }

    @Test fun formatFourPreservesPlayableStateAndSubsequentPlacements() {
        val original = session()
        val legacy = JSONObject(SessionStore.encode(original)).put("version", 4).apply { remove("blockAppearances") }
        val restored = SessionStore.decode(legacy.toString())
        assertEquals(original.copy(state = original.state.copy(blockAppearances = emptyList())), restored)
        assertEquals(restored, SessionStore.decode(SessionStore.encode(restored)))
        val next = GameEngine().apply(restored.state, GameCommand.HARD_DROP)
        original.state.board.forEachIndexed { row, cells -> cells.forEachIndexed { column, type ->
            if (type != null) assertEquals(BlockAppearance.legacy, next.blockAppearance(row, column))
        } }
        assertEquals(restored.copy(state = next),
            SessionStore.decode(SessionStore.encode(restored.copy(state = next))))
    }

    @Test fun malformedArtworkCannotSilentlyChangeTheSavedCell() {
        val original = session()
        val row = original.state.board.indexOfFirst { cells -> cells.any { it != null } }
        val column = original.state.board[row].indexOfFirst { it != null }
        for (pair in listOf(listOf(4, 0), listOf(0, -1), listOf(0, 4), listOf(.5, 0), listOf(0, 0, 1))) {
            val json = JSONObject(SessionStore.encode(original))
            json.getJSONArray("blockAppearances").getJSONArray(row).put(column, JSONArray(pair))
            assertThrows(IllegalArgumentException::class.java) { SessionStore.decode(json.toString()) }
        }
        val inconsistent = JSONObject(SessionStore.encode(original))
        inconsistent.getJSONArray("blockAppearances").getJSONArray(0).put(0, JSONArray(listOf(1, 0)))
        assertThrows(IllegalArgumentException::class.java) { SessionStore.decode(inconsistent.toString()) }
    }
}
