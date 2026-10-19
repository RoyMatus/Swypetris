package ru.itoltec.swypetris

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

/** Compatibility with the retired terminal flow, large scores and actual shared image bytes. */
class AbsoluteVictoryIntegrationTest {
    @get:Rule val storage = IsolatedStorageRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun retiredTerminalSnapshotResumesWithoutLosingProgress() {
        val state = GameState(active = Piece(Tetromino.O), next = Tetromino.T,
            score = 999_999, lines = 900, completedRounds = 12)
        val saved = GameSession("retired", state, emptyList(), 20, 0, state.gravityNanos, 50_000)
        val old = JSONObject(SessionStore.encode(saved)).apply {
            put("version", 5)
            put("victoryPending", true)
            put("finishedAt", 100)
            put("absoluteName", "Roy")
            put("postcardReady", true)
        }
        val restored = SessionStore.decode(old.toString())
        assertEquals(state.score, restored.state.score)
        assertEquals(state.lines, restored.state.lines)
        assertFalse(restored.state.victoryPending)
        assertEquals(0L, restored.finishedAt)
        assertEquals(1_000_000L, GameEngine().apply(restored.state, GameCommand.SOFT_DROP).score)
        val lockedBoard = BoardGeometry.empty().map { it.toMutableList() }
        val lockedPiece = state.active.copy(y = 18)
        lockedPiece.cells().forEach { lockedBoard[BoardGeometry.row(it.y)][it.x] = Tetromino.O }
        val occupied = JSONObject(SessionStore.encode(saved.copy(state = state.copy(board = lockedBoard,
            active = lockedPiece))))
            .put("version", 5).put("victoryPending", true).put("finishedAt", 100)
        val recovered = SessionStore.decode(occupied.toString())
        assertTrue(GameEngine().fits(recovered.state, recovered.state.active))
        assertEquals(state.score, recovered.state.score)
        assertEquals(lockedBoard, recovered.state.board)
    }

    @Test fun malformedNumericCountersAreRejectedRatherThanTruncated() {
        val state = GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 80_001)
        val saved = GameSession("invalid", state, emptyList(), 20, 0, state.gravityNanos, 0)
        for (key in listOf("score", "completedRounds", "recordAtStart")) {
            for (value in listOf<Any>(.5, -1, "9223372036854775808")) {
                val json = JSONObject(SessionStore.encode(saved)).put(key, value)
                assertThrows(IllegalArgumentException::class.java) { SessionStore.decode(json.toString()) }
            }
        }
    }

    @Test fun resumedTerminalRecordIsUpdatedOnceWhenContinuedRunFinallyEnds() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val preferences = GameStorage.preferences(app)
            GameStorage.migrate(preferences)
            val state = GameState(active = Piece(Tetromino.O), next = Tetromino.T,
                score = 999_999, completedRounds = 12)
            val old = JSONObject(SessionStore.encode(GameSession("continued", state, emptyList(), 20, 0,
                state.gravityNanos, 50_000))).put("version", 5).put("victoryPending", true).put("finishedAt", 100)
            preferences.edit().putString("session_v1", old.toString()).commit()
            ResultStore(preferences).write(listOf(GameResult("continued", 100, "Roy", state.score, 0, 1, 20)))
            val model = GameViewModel(app, null, { 1000L }, false)
            model.resume()
            model.input.command(GameCommand.SOFT_DROP)
            val before = model.game!!
            model.simulation.acceptState(before, before.copy(gameOver = true, topOut = TopOut.BLOCK_OUT), 1000L)
            assertEquals(1_000_000L, model.results.single().score)
            assertEquals("continued", model.results.single().id)
            assertEquals("Roy", model.results.single().name)
        }
    }

    @Test fun largeScoresAndCountersRoundTripWithoutNarrowing() {
        val state = GameState(active = Piece(Tetromino.O), next = Tetromino.T,
            score = 3_000_010_000L, completedRounds = 37_500)
        val snapshot = GameSession("large", state, emptyList(), 20, 0, state.gravityNanos, 2_200_000_000L)
        assertEquals(snapshot, SessionStore.decode(SessionStore.encode(snapshot)))
        val result = GameResult("large", 100, "Roy", state.score, 100, 11, 20,
            completedRounds = state.completedRounds)
        ResultStore(GameStorage.preferences(app)).write(listOf(result))
        assertEquals(listOf(result), ResultStore(GameStorage.preferences(app)).read())
    }

    @Test fun recordPostcardReflectsActualScoreAndHasReadOnlyPngGrant() {
        val postcard = AbsolutePostcard.create(app, "Roy", 80_000)
        val again = AbsolutePostcard.create(app, "Roy", 80_000)
        val different = AbsolutePostcard.create(app, "Roy", 1_040_000)
        val longName = AbsolutePostcard.create(app, "Я".repeat(MAX_PLAYER_NAME_LENGTH), 80_000)
        try {
            assertTrue(postcard.sameAs(again))
            assertFalse(postcard.sameAs(different))
            for (y in postcard.height / 3 until postcard.height step 12) {
                for (x in 0 until postcard.width step 12) {
                    assertEquals(postcard.getPixel(x, y), longName.getPixel(x, y))
                }
            }
            val send = AbsolutePostcard.shareIntent(app, postcard)
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("image/png", send.type)
            @Suppress("DEPRECATION")
            val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("content", uri.scheme)
            assertEquals("${app.packageName}.postcards", uri.authority)
            assertEquals(uri, send.clipData!!.getItemAt(0).uri)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            app.contentResolver.openInputStream(uri)!!.use { input ->
                val shared = BitmapFactory.decodeStream(input)
                try { assertTrue(postcard.sameAs(shared)) } finally { shared.recycle() }
            }
            app.contentResolver.delete(uri, null, null)
        } finally {
            postcard.recycle()
            again.recycle()
            different.recycle()
            longName.recycle()
        }
    }
}
