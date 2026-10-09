package ru.itoltec.swypetris

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Terminal state, recovery, input validation and actual shared PNG bytes. */
class AbsoluteVictoryIntegrationTest {
    @get:Rule val storage = IsolatedStorageRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private fun model(state: GameState? = null) = GameViewModel(app, state, { 1000L }, false)
    private fun nearMaximum() = GameState(active = Piece(Tetromino.O), next = Tetromino.T,
        score = GameRules.MAX_SCORE - 1, completedRounds = 12)

    @Test fun terminalFlowRecordsOnceAndRestoresPostcardAfterRestart() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val first = model(nearMaximum())
            first.input.command(GameCommand.SOFT_DROP)
            assertEquals(GameScreen.ABSOLUTE_VICTORY, first.screen)
            assertEquals(GameRules.MAX_SCORE, first.results.single().score)
            assertFalse(first.requestRecordName)
            val won = first.game
            first.nextRound()
            first.input.command(GameCommand.HARD_DROP)
            assertEquals(won, first.game)
            first.absolute.changeName("  Абсолютный победитель  ")
            first.onBackground()
            val restored = model()
            restored.resume()
            assertEquals(GameScreen.ABSOLUTE_VICTORY, restored.screen)
            assertEquals(first.absoluteName, restored.absoluteName)
            restored.absolute.preparePostcard()
            assertEquals(GameScreen.POSTCARD, restored.screen)
            assertEquals("Абсолютный победитель", restored.results.single().name)
            restored.pause()
            val postcard = model()
            postcard.resume()
            assertEquals(GameScreen.POSTCARD, postcard.screen)
            assertEquals(1, postcard.results.size)
            assertEquals("Абсолютный победитель", postcard.absoluteName)
            assertEquals(won, postcard.game)
            postcard.newGame()
            assertFalse(postcard.postcardReady)
            assertEquals("", postcard.absoluteName)
            assertFalse(postcard.game!!.absoluteVictory)
        }
    }

    @Test fun emptyNameIsRejectedAndInputHasBoundedLength() {
        val first = model(nearMaximum())
        first.input.command(GameCommand.SOFT_DROP)
        first.absolute.changeName("   ")
        first.absolute.preparePostcard()
        assertTrue(first.absoluteNameError)
        assertEquals(GameScreen.ABSOLUTE_VICTORY, first.screen)
        first.absolute.changeName("Я".repeat(MAX_PLAYER_NAME_LENGTH + 1))
        assertEquals(MAX_PLAYER_NAME_LENGTH, first.absoluteName.length)
        assertFalse(first.absoluteNameError)
        first.onBackground()
        first.absolute.preparePostcard()
        assertFalse(first.postcardReady)
        first.onForeground()
        first.resume()
        first.absolute.preparePostcard()
        assertTrue(first.postcardReady)
    }

    @Test fun sessionSupportsTerminalVictoryAndOldSnapshotsWithoutNewFields() {
        val won = GameEngine().apply(nearMaximum(), GameCommand.SOFT_DROP)
        val snapshot = GameSession("terminal", won, emptyList(), 20, 0, won.gravityNanos, 0,
            finishedAt = 100, absoluteName = "Roy", postcardReady = true)
        assertEquals(snapshot, SessionStore.decode(SessionStore.encode(snapshot)))
        val old = JSONObject(SessionStore.encode(snapshot)).apply {
            remove("absoluteName")
            remove("postcardReady")
        }
        val restored = SessionStore.decode(old.toString())
        assertTrue(restored.state.absoluteVictory)
        assertEquals("", restored.absoluteName)
        assertFalse(restored.postcardReady)
    }

    @Test fun completedPostcardHasStaticPreviewBytesAndReadOnlyContentGrant() {
        val postcard = AbsolutePostcard.create(app, "Roy")
        val again = AbsolutePostcard.create(app, "Roy")
        val different = AbsolutePostcard.create(app, "Анна")
        try {
            assertTrue(postcard.sameAs(again))
            assertFalse(postcard.sameAs(different))
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
        }
    }

    @Test fun terminalAnimationFreezesInBackgroundAndResumesOnBothScreens() {
        val first = model(nearMaximum())
        first.input.command(GameCommand.SOFT_DROP)
        first.advanceVictoryAnimation(100)
        assertEquals(100L, first.victoryAnimationMillis)
        first.onBackground()
        first.advanceVictoryAnimation(100)
        assertEquals(100L, first.victoryAnimationMillis)
        first.onForeground()
        first.resume()
        first.absolute.changeName("Roy")
        first.absolute.preparePostcard()
        first.advanceVictoryAnimation(100)
        assertEquals(200L, first.victoryAnimationMillis)
    }
}
