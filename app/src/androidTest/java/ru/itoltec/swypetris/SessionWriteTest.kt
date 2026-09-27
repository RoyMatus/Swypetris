package ru.itoltec.swypetris

import android.app.Application
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SessionWriteTest {
    @get:Rule val storage = IsolatedStorageRule()

    private class CountingPreferences(private val actual: SharedPreferences) : SharedPreferences by actual {
        var edits = 0
        override fun edit(): SharedPreferences.Editor { edits++; return actual.edit() }
    }

    @Test fun onlyIdenticalSnapshotsAreSkippedAndBoardCacheCannotBecomeStale() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val preferences = CountingPreferences(GameStorage.sessionPreferences(app))
        val store = SessionStore(preferences)
        val original = GameSession("session", GameState(active = Piece(Tetromino.T), next = Tetromino.O),
            emptyList(), 0, 0, 800, 0)
        store.write(original)
        store.write(original.copy())
        assertEquals(1, preferences.edits)
        val moved = original.copy(state = original.state.copy(active = original.state.active.copy(x = 2)))
        val timed = moved.copy(playedMillis = 200, gravityRemaining = 600)
        val cleared = timed.copy(clearMillis = 60)
        for (session in listOf(moved, timed, cleared)) {
            store.write(session)
            assertEquals(session, store.read())
            assertEquals(session, SessionStore.decode(SessionStore.encode(session)))
        }
        val board = original.state.board.map { it.toMutableList() }
        board[19][0] = Tetromino.J
        val changed = timed.copy(state = timed.state.copy(board = board))
        store.write(changed)
        assertEquals(changed, store.read())
        store.write(original)
        assertEquals(original, store.read())
        assertEquals(6, preferences.edits)
    }
}
