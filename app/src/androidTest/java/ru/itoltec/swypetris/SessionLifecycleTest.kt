package ru.itoltec.swypetris

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SessionLifecycleTest {
    @get:Rule val storage = IsolatedStorageRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private var now = 1000L
    private fun model(state: GameState? = null) = GameViewModel(app, state, { now }, false)
    private fun state() = GameState(active = Piece(Tetromino.O), next = Tetromino.T)

    @Test fun hiddenCellsAndNegativePiecePositionSurviveRestore() {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        board[BoardGeometry.row(-5)][0] = Tetromino.J
        val hidden = state().copy(board = board, active = Piece(Tetromino.T, y = -1))
        val snapshot = GameSession("hidden", hidden, listOf(Tetromino.I), 0, 0, 800, 0)
        assertEquals(snapshot, SessionStore.decode(SessionStore.encode(snapshot)))
    }

    @Test fun incompatibleSessionRetainsSettingsAndHistory() {
        val settings = GameStorage.preferences(app)
        settings.edit().putBoolean("hints", true).putString("player_name", "Roy").apply()
        val sessions = GameStorage.sessionPreferences(app)
        val snapshot = GameSession("old", state(), emptyList(), 0, 0, 800, 0)
        val json = org.json.JSONObject(SessionStore.encode(snapshot)).put("version", 1).toString()
        sessions.edit().putString("session_v1", json).apply()
        assertNull(SessionStore(sessions).read())
        assertTrue(settings.getBoolean("hints", false))
        assertEquals("Roy", settings.getString("player_name", null))
    }

    @Test fun defaultsAndExistingPreferencesArePreserved() {
        val first = model()
        assertEquals(Difficulty.MEDIUM, first.difficulty)
        assertFalse(first.hintsEnabled)
        first.chooseDifficulty(Difficulty.HARD)
        first.setHints(true)
        repeat(3) {
            val fresh = model()
            assertEquals(Difficulty.HARD, fresh.difficulty)
            assertTrue(fresh.hintsEnabled)
        }
    }

    @Test fun settingOnlyAppliesToNewGames() {
        val first = model()
        first.newGame()
        first.menu()
        first.chooseDifficulty(Difficulty.EASY)
        first.resume()
        assertEquals(Difficulty.MEDIUM, first.game!!.difficulty)
        first.newGame()
        assertEquals(Difficulty.EASY, first.game!!.difficulty)
    }

    @Test fun restoresBoardQueueAndRemainingGravityAfterFullRestart() {
        val first = model()
        first.newGame()
        first.command(GameCommand.RIGHT)
        first.command(GameCommand.SOFT_DROP)
        now += 300
        first.pause()
        val saved = first.game
        val bag = first.engine.remainingBag()
        now += 60000
        val restored = model()
        assertEquals(GameScreen.MENU, restored.screen)
        assertEquals(saved, restored.game)
        assertEquals(bag, restored.engine.remainingBag())
        restored.advanceFrame(now)
        assertEquals(saved, restored.game)
        restored.resume()
        now += 499
        restored.advanceFrame(now)
        assertEquals(saved, restored.game)
        now++
        restored.advanceFrame(now)
        assertEquals(saved!!.active.y + 1, restored.game!!.active.y)
        assertEquals(800L, SessionStore(GameStorage.sessionPreferences(app)).read()!!.playedMillis)
    }

    @Test fun focusAndLifecycleFreezeImmediatelyAndNeverAutoResume() {
        val first = model(state())
        now += 900 // A due tick must not be executed by pause itself.
        first.onWindowFocusChanged(false)
        assertEquals(state(), first.game)
        assertEquals(GameScreen.MENU, first.screen)
        now += 10000
        first.advanceFrame(now)
        first.command(GameCommand.HARD_DROP)
        first.resume()
        assertEquals(GameScreen.MENU, first.screen)
        first.onBackground()
        first.onWindowFocusChanged(true)
        first.resume()
        assertEquals(GameScreen.MENU, first.screen)
        first.onForeground()
        assertEquals(GameScreen.MENU, first.screen)
        assertEquals(state(), first.game)
        first.resume()
        assertEquals(GameScreen.PLAYING, first.screen)
    }

    @Test fun holdingThroughNaturalLockControlsNextPieceWithoutPhantomTap() {
        val first = model(state().copy(active = Piece(Tetromino.O, y = 18)))
        first.pointerDown(100f, 100f, now)
        now += 800
        first.advanceFrame(now)
        val spawned = first.game!!
        assertEquals(1, spawned.generation)
        first.pointerMove(100f, 100f, now)
        assertEquals(spawned, first.game)
        first.pointerMove(112f, 100f, now + 20)
        assertEquals(spawned.active.x + 1, first.game!!.active.x)
        val moved = first.game
        first.pointerUp(112f, 100f, now + 40)
        assertEquals(moved, first.game)
    }

    private fun clearingStart(): GameState {
        val board = List(BoardGeometry.TOTAL_ROWS) { y -> List<Tetromino?>(10) { x -> if (y == BoardGeometry.row(19) && x < 8) Tetromino.J else null } }
        return state().copy(board = board, active = Piece(Tetromino.O, x = 8, y = 18))
    }

    @Test fun pointerTracksDuringClearThenRebasesAtActualPosition() {
        val first = model(clearingStart())
        first.pointerDown(100f, 100f, now)
        first.command(GameCommand.HARD_DROP)
        first.pointerMove(200f, 300f, now + 100)
        now += 600
        first.advanceFrame(now)
        val spawned = first.game!!
        first.pointerMove(200f, 300f, now)
        assertEquals(spawned, first.game)
        first.pointerMove(212f, 300f, now + 20)
        assertEquals(spawned.active.x + 1, first.game!!.active.x)
    }

    @Test fun restartDuringClearPreservesItsExactProgress() {
        val first = model(clearingStart())
        first.command(GameCommand.HARD_DROP)
        now += 217
        first.pause()
        now += 10000
        val restored = model()
        assertEquals(first.game, restored.game)
        assertEquals(217L, restored.clearElapsedMillis)
        restored.resume()
        now += 382
        restored.advanceFrame(now)
        assertEquals(0, restored.game!!.generation)
        now++
        restored.advanceFrame(now)
        assertEquals(1, restored.game!!.generation)
        assertEquals(100, restored.game!!.score)
    }

    @Test fun tapMovesExactlyOneCellAndHoldUsesOnlyGravity() {
        val first = model(state())
        first.pointerDown(100f, 100f, now)
        first.pointerUp(100f, 100f, now + 100)
        assertEquals(1, first.game!!.active.y)
        assertEquals(0, first.game!!.active.rotation)
        first.pointerDown(100f, 100f, now + 200)
        now += 799
        first.advanceFrame(now)
        assertEquals(1, first.game!!.active.y)
        now++
        first.advanceFrame(now)
        assertEquals(2, first.game!!.active.y)
        first.pointerUp(100f, 100f, now)
        assertEquals(2, first.game!!.active.y)
    }

    @Test fun newGameReplacesSavedSessionAndGameOverCannotContinue() {
        val first = model()
        first.newGame()
        first.command(GameCommand.HARD_DROP)
        first.newGame()
        first.pause()
        assertEquals(first.game, model().game)
        val board = state().board.map { it.toMutableList() }
        board[BoardGeometry.row(0)][4] = Tetromino.Z
        val losing = model(state().copy(board = board, active = Piece(Tetromino.O, x = 0, y = 18), score = 50))
        losing.command(GameCommand.TICK)
        assertTrue(losing.game!!.gameOver)
        val restored = model()
        restored.resume()
        assertEquals(GameScreen.MENU, restored.screen)
        assertTrue(restored.game!!.gameOver)
        repeat(3) { assertEquals(1, model().results.size) }
    }

    @Test fun recordsAreIndependentAndNonRecordsAreOnlyShownAsLatestResult() {
        fun finish(score: Int, difficulty: Difficulty): GameViewModel {
            val board = state().board.map { it.toMutableList() }
            board[BoardGeometry.row(0)][4] = Tetromino.Z
            return model(state().copy(board = board, active = Piece(Tetromino.O, x = 0, y = 18),
                score = score, difficulty = difficulty)).also { it.command(GameCommand.TICK) }
        }
        assertEquals(1, finish(100, Difficulty.EASY).results.size)
        assertEquals(1, finish(100, Difficulty.EASY).results.size)
        val ordinary = finish(50, Difficulty.EASY)
        assertEquals(1, ordinary.results.size)
        assertEquals(50, ordinary.latestResult!!.score)
        assertFalse(ordinary.requestRecordName)
        val hard = finish(20, Difficulty.HARD)
        assertEquals(2, hard.results.size)
        assertTrue(hard.requestRecordName)
        assertEquals(100, hard.recordFor(Difficulty.EASY))
        assertEquals(20, hard.recordFor(Difficulty.HARD))
    }

    @Test fun corruptedSaveDoesNotClearPreferencesOrResults() {
        val preferences = GameStorage.preferences(app)
        preferences.edit().putBoolean("hints", true).putString("difficulty", "hard")
            .putString("session_v1", "{broken").apply()
        val restored = model()
        assertNull(restored.game)
        assertTrue(restored.hintsEnabled)
        assertEquals(Difficulty.HARD, restored.difficulty)
    }

    @Test fun victoryWithBlockedSpawnCanBeSavedAndContinued() {
        val board = state().board.map { it.toMutableList() }
        board[BoardGeometry.row(0)][4] = Tetromino.Z
        val first = model(state().copy(board = board, active = Piece(Tetromino.O, x = 0, y = 17),
            next = Tetromino.O, score = 79999))
        first.command(GameCommand.HARD_DROP)
        val restored = model()
        assertEquals(first.game, restored.game)
        restored.resume()
        assertEquals(GameScreen.VICTORY, restored.screen)
        restored.nextRound()
        assertEquals(1, restored.game!!.completedRounds)
        assertEquals(Difficulty.MEDIUM, restored.game!!.difficulty)
    }
}
