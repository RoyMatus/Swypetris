package ru.itoltec.swypetris

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionLifecycleTest {
    @get:Rule val storage = IsolatedStorageRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private var now = 1000L
    private fun model(state: GameState? = null) = GameViewModel(app, state, { now }, false)
    private fun state() = GameState(active = Piece(Tetromino.O), next = Tetromino.T)

    @Test fun previousGravityRemainderAndGameSurviveSlowerProgression() {
        val previous = state().copy(score = 55000, lines = 90, held = Tetromino.Z)
        val snapshot = GameSession("before-slower-gravity", previous, listOf(Tetromino.I),
            1234, 0, 64_151_585, 10000)
        val restored = SessionStore.decode(SessionStore.encode(snapshot))
        assertEquals(snapshot, restored)
        assertEquals(10, restored.state.level)
        assertTrue(restored.state.gravityNanos > restored.gravityRemainingNanos)
    }

    @Test fun hiddenCellsAndNegativePiecePositionSurviveRestore() {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        board[BoardGeometry.row(-5)][0] = Tetromino.J
        val hidden = state().copy(board = board, active = Piece(Tetromino.T, y = -1))
        val snapshot = GameSession("hidden", hidden, listOf(Tetromino.I), 0, 0, 1_000_000_000, 0)
        assertEquals(snapshot, SessionStore.decode(SessionStore.encode(snapshot)))
    }

    @Test fun cumulativeFruitCountsSurviveExistingSessionFormat() {
        val collected = state().copy(score = 93000, completedRounds = 1)
        val snapshot = GameSession("fruits", collected, listOf(Tetromino.I), 0, 0, 1_000_000_000, 0)
        val restored = SessionStore.decode(SessionStore.encode(snapshot))
        assertEquals(snapshot, restored)
        assertEquals(listOf(2, 1, 1, 1, 1, 1, 1, 1), restored.state.fruitCounts)
        assertEquals(1, restored.state.roundFruits)
    }

    @Test fun placementAndChainMetadataSurviveRestore() {
        val qualified = state().copy(lastRotationKick = 4, softDropCells = 3,
            hardDropCells = 2, backToBack = true, combo = 2,
            placement = PlacementResult(2, Spin.FULL, true, 2, true, 3, 2, 5))
        val snapshot = GameSession("chains", qualified, listOf(Tetromino.I), 0, 0, 1_000_000_000, 0)
        assertEquals(snapshot, SessionStore.decode(SessionStore.encode(snapshot)))
    }

    @Test fun incompatibleSessionRetainsSettings() {
        val settings = GameStorage.preferences(app)
        settings.edit().putBoolean("hints", true).putString("player_name", "Roy").apply()
        val sessions = GameStorage.sessionPreferences(app)
        val snapshot = GameSession("old", state(), emptyList(), 0, 0, 1_000_000_000, 0)
        val json = org.json.JSONObject(SessionStore.encode(snapshot)).put("version", 1).toString()
        sessions.edit().putString("session_v1", json).apply()
        assertNull(SessionStore(sessions).read())
        assertTrue(settings.getBoolean("hints", false))
        assertEquals("Roy", settings.getString("player_name", null))
    }

    @Test fun defaultsAndExistingPreferencesArePreserved() {
        val first = model()
        assertFalse(first.hintsEnabled)
        GameStorage.preferences(app).edit().putString("difficulty", "hard").apply()
        first.options.setHints(true)
        repeat(3) {
            val fresh = model()
            assertTrue(fresh.hintsEnabled)
            fresh.newGame()
            assertEquals(1000L, fresh.game!!.gravityMillis)
            assertEquals("hard", GameStorage.preferences(app).getString("difficulty", null))
        }
    }

    @Test fun settingOnlyAppliesToNewGames() {
        val first = model()
        first.newGame()
        first.navigation.menu()
        first.options.chooseStartingLevel(5)
        first.resume()
        assertEquals(1, first.game!!.startingLevel)
        first.newGame()
        assertEquals(5, first.game!!.startingLevel)
    }

    @Test fun restoresBoardQueueAndRemainingGravityAfterFullRestart() {
        val first = model()
        first.newGame()
        first.input.command(GameCommand.RIGHT)
        first.input.command(GameCommand.SOFT_DROP)
        now += 300
        first.pause()
        val saved = first.game
        val bag = first.engine.remainingBag()
        now += 60000
        val restored = model()
        assertEquals(GameScreen.MENU, restored.screen)
        assertEquals(saved, restored.game)
        assertEquals(bag, restored.engine.remainingBag())
        restored.simulation.advanceFrame(now)
        assertEquals(saved, restored.game)
        restored.resume()
        now += 699
        restored.simulation.advanceFrame(now)
        assertEquals(saved, restored.game)
        now++
        restored.simulation.advanceFrame(now)
        assertEquals(saved!!.active.y + 1, restored.game!!.active.y)
        assertEquals(1000L, SessionStore(GameStorage.sessionPreferences(app)).read()!!.playedMillis)
    }

    @Test fun focusAndLifecycleFreezeImmediatelyAndNeverAutoResume() {
        val first = model(state())
        now += 1100 // A due tick must not be executed by pause itself.
        first.onWindowFocusChanged(false)
        assertEquals(state(), first.game)
        assertEquals(GameScreen.MENU, first.screen)
        now += 10000
        first.simulation.advanceFrame(now)
        first.input.command(GameCommand.HARD_DROP)
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
        first.input.pointerDown(100f, 100f, now)
        now += 800
        first.simulation.advanceFrame(now)
        val spawned = first.game!!
        assertEquals(1, spawned.generation)
        first.input.pointerMove(100f, 100f, now)
        assertEquals(spawned, first.game)
        first.input.pointerMove(112f, 100f, now + 20)
        assertEquals(spawned.active.x + 1, first.game!!.active.x)
        val moved = first.game
        first.input.pointerUp(112f, 100f, now + 40)
        assertEquals(moved, first.game)
    }

    private fun clearingStart(): GameState {
        val board = List(BoardGeometry.TOTAL_ROWS) { y -> List<Tetromino?>(10) { x -> if (y == BoardGeometry
            .row(19) && x < 8) Tetromino.J else null } }
        return state().copy(board = board, active = Piece(Tetromino.O, x = 8, y = 18))
    }

    @Test fun pointerTracksDuringClearThenRebasesAtActualPosition() {
        val first = model(clearingStart())
        first.input.pointerDown(100f, 100f, now)
        first.input.command(GameCommand.HARD_DROP)
        first.input.pointerMove(200f, 300f, now + 100)
        now += 600
        first.simulation.advanceFrame(now)
        val spawned = first.game!!
        first.input.pointerMove(200f, 300f, now)
        assertEquals(spawned, first.game)
        first.input.pointerMove(212f, 300f, now + 20)
        assertEquals(spawned.active.x + 1, first.game!!.active.x)
    }

    @Test fun restartDuringClearPreservesItsExactProgress() {
        val first = model(clearingStart().copy(lines = 9))
        first.input.command(GameCommand.HARD_DROP)
        now += 217
        first.pause()
        now += 10000
        val restored = model()
        assertEquals(first.game, restored.game)
        assertEquals(217L, restored.clearElapsedMillis)
        restored.resume()
        now += 382
        restored.simulation.advanceFrame(now)
        assertEquals(0, restored.game!!.generation)
        now++
        restored.simulation.advanceFrame(now)
        assertEquals(1, restored.game!!.generation)
        assertEquals(100, restored.game!!.score)
        assertEquals(10, restored.game!!.lines)
        assertEquals(2, restored.game!!.level)
        assertEquals(1, restored.game!!.placement!!.level)
    }

    @Test fun tapMovesExactlyOneCellAndHoldUsesOnlyGravity() {
        val first = model(state())
        first.input.pointerDown(100f, 100f, now)
        first.input.pointerUp(100f, 100f, now + 100)
        assertEquals(1, first.game!!.active.y)
        assertEquals(0, first.game!!.active.rotation)
        first.input.pointerDown(100f, 100f, now + 200)
        now += 999
        first.simulation.advanceFrame(now)
        assertEquals(1, first.game!!.active.y)
        now++
        first.simulation.advanceFrame(now)
        assertEquals(2, first.game!!.active.y)
        first.input.pointerUp(100f, 100f, now)
        assertEquals(2, first.game!!.active.y)
    }

    @Test fun newGameReplacesSavedSessionAndGameOverCannotContinue() {
        val first = model()
        first.newGame()
        first.input.command(GameCommand.HARD_DROP)
        first.newGame()
        first.pause()
        assertEquals(first.game, model().game)
        val board = state().board.map { it.toMutableList() }
        board[BoardGeometry.row(0)][4] = Tetromino.Z
        val losing = model(state().copy(board = board, active = Piece(Tetromino.O, x = 0, y = 18), score = 50))
        losing.input.command(GameCommand.HARD_DROP)
        assertTrue(losing.game!!.gameOver)
        val restored = model()
        restored.resume()
        assertEquals(GameScreen.MENU, restored.screen)
        assertTrue(restored.game!!.gameOver)
        repeat(3) { assertEquals(1, model().results.size) }
    }

    @Test fun historicalRecordsDoNotCompeteWithNewRules() {
        val legacy = GameResult("legacy", 1, "Roy", 10000, 0, 1, 0, rulesVersion = 5, difficulty = Difficulty.HARD)
        ResultStore(GameStorage.preferences(app)).write(listOf(legacy))
        fun finish(score: Int): GameViewModel {
            val board = state().board.map { it.toMutableList() }
            board[BoardGeometry.row(0)][4] = Tetromino.Z
            return model(state().copy(board = board, active = Piece(Tetromino.O, x = 0, y = 18),
                score = score)).also { it.input.command(GameCommand.HARD_DROP) }
        }
        val record = finish(100)
        assertEquals(2, record.results.size)
        assertTrue(record.requestRecordName)
        assertNull(record.latestResult!!.difficulty)
        assertEquals(6, record.latestResult!!.rulesVersion)
        assertEquals(legacy, record.results.last())
        val ordinary = finish(50)
        assertEquals(2, ordinary.results.size)
        assertEquals(50, ordinary.latestResult!!.score)
        assertFalse(ordinary.requestRecordName)
        assertEquals(100, ordinary.record)
    }

    @Test fun corruptedSaveDoesNotClearPreferencesOrResults() {
        val preferences = GameStorage.preferences(app)
        preferences.edit().putBoolean("hints", true).putString("difficulty", "hard")
            .putString("session_v1", "{broken").apply()
        val restored = model()
        assertNull(restored.game)
        assertTrue(restored.hintsEnabled)
        assertEquals("hard", preferences.getString("difficulty", null))
    }

    @Test fun victoryWithBlockedSpawnCanBeSavedAndContinued() {
        val board = state().board.map { it.toMutableList() }
        board[BoardGeometry.row(0)][4] = Tetromino.Z
        val first = model(state().copy(board = board, active = Piece(Tetromino.O, x = 0, y = 17),
            next = Tetromino.O, score = 79999))
        first.input.command(GameCommand.HARD_DROP)
        val restored = model()
        assertEquals(first.game, restored.game)
        restored.resume()
        assertEquals(GameScreen.VICTORY, restored.screen)
        restored.nextRound()
        assertEquals(1, restored.game!!.completedRounds)
    }
}
