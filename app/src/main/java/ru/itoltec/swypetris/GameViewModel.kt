package ru.itoltec.swypetris

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

/** A paused game is owned by the model while the ordinary menu is displayed. */
enum class GameScreen { MENU, SETTINGS, HELP, CONTACTS, PRIVACY, LEGAL, PLAYING, GAME_OVER, RESULTS, RECORD, VICTORY }

/**
 * Владелец партии: соединяет движок, жесты, анимацию и рекорд; переживает пересоздание Activity.
 * Внутренний конструктор принимает исходное поле и часы для воспроизводимых проверок;
 * [autoTick] отключает автоматический цикл, позволяя тестам вызывать [advanceFrame] вручную.
 */
class GameViewModel internal constructor(
    application: Application,
    initialState: GameState?,
    private val clock: () -> Long,
    autoTick: Boolean,
    feedback: GameFeedback? = null,
    musicPlayback: MusicPlayback? = null,
    showLaunchIntro: Boolean = autoTick && initialState == null,
    timer: GameTimer? = null
) : AndroidViewModel(application) {
    /** The standard Android constructor uses a monotonic clock and an automatic game loop. */
    constructor(application: Application) : this(application, null, SystemClock::uptimeMillis, true)
    private val preferences = GameStorage.preferences(application).also(GameStorage::migrate)
    private val resultStore = ResultStore(preferences)
    private val sessionStore = SessionStore(GameStorage.sessionPreferences(application))
    private val restored = if (initialState == null) sessionStore.read() else null
    private var sessionId = restored?.id ?: java.util.UUID.randomUUID().toString()
    private var finishedAt = restored?.finishedAt ?: 0L
    var difficulty by mutableStateOf(Difficulty.restore(preferences.getString("difficulty", null)))
        private set
    var startingLevel by mutableStateOf(preferences.getInt("starting_level", 1)
        .coerceIn(GameRules.MIN_STARTING_LEVEL, GameRules.MAX_STARTING_LEVEL))
        private set
    private val music = musicPlayback ?: if (autoTick) GameMusic(application) else null
    var legacyRecord by mutableStateOf(GameStorage.legacyRecord(preferences))
        private set
    var results by mutableStateOf(resultStore.read())
        private set
    var currentResultId by mutableStateOf<String?>(null)
        private set
    var latestResult by mutableStateOf<GameResult?>(null)
        private set
    val recordResults: List<GameResult> get() = recordHistory(results)
    var requestRecordName by mutableStateOf(false)
        private set
    var playerName by mutableStateOf(preferences.getString("player_name", "") ?: "")
        private set
    var musicSelection by mutableStateOf(MusicSelection.restore(preferences.getString("music_selection", null),
        preferences.getBoolean("music", true)))
        private set
    val musicEnabled: Boolean get() = musicSelection != MusicSelection.Off
    private var foreground = true
    private var windowFocused = true
    private val activeForeground: Boolean get() = foreground && windowFocused
    var launchIntroMillis by mutableStateOf(if (showLaunchIntro) 0L else LaunchIntroMotion.DURATION)
        private set
    val launchIntroPending by derivedStateOf { launchIntroMillis < LaunchIntroMotion.DURATION }
    val launchLogoAssembled by derivedStateOf { launchIntroMillis >= 2650L }
    private var recordAtStart = restored?.recordAtStart ?: bestFor(initialState?.difficulty ?: difficulty)
    private var playedMillis = restored?.playedMillis ?: 0L
    private var lastPlayFrame = clock()
    private var gestureConfig = GestureConfig()
    private var boardWidthDp = 0f
    private val feedback = feedback ?: if (autoTick) AndroidGameFeedback(application) else SilentFeedback
    var soundEnabled by mutableStateOf(preferences.getBoolean("sound", true))
        private set
    var vibrationEnabled by mutableStateOf(preferences.getBoolean("vibration", true))
        private set
    var hintsEnabled by mutableStateOf(preferences.getBoolean("hints", false))
        private set
    var paletteId by mutableStateOf(GamePalettes.find(preferences.getString("palette", null)).id)
        private set
    var victoryAnimationMillis by mutableStateOf(0L)
        private set
    val engine = GameEngine()
    var game by mutableStateOf<GameState?>(initialState ?: restored?.state)
        private set
    var screen by mutableStateOf(if (initialState == null) GameScreen.MENU else GameScreen.PLAYING)
        private set
    var record by mutableStateOf(bestFor(game?.difficulty ?: difficulty))
        private set
    private var gestures = createGestures(GestureConfig())
    private fun createGestures(config: GestureConfig) = GestureController(config, ::command).also {
        it.onHoldReady = { if (vibrationEnabled && activeForeground && screen == GameScreen.PLAYING && game?.holdUsed == false) feedback.holdReady() }
    }
    private var lastGravity = clock()
    private var lastLockFrame = clock()
    private var gravityRemaining = restored?.gravityRemaining ?: (game?.gravityMillis ?: Difficulty.INITIAL_MILLIS)

    /** Elapsed line-clear time increases only during active play, not in the menu or on pause. */
    var clearElapsedMillis by mutableStateOf(restored?.clearMillis ?: 0L)
        private set
    private var lastAnimationFrame = clock()
    private val timer = timer ?: if (autoTick) AndroidGameTimer() else null
    private var scheduledAt: Long? = null

    init {
        restored?.let { engine.restoreBag(it.bag) }
        gestures.setEnabled(game?.clearingRows?.isEmpty() != false)
        if (restored?.state?.gameOver == true) {
            saveResult(restored.state, clock())
            screen = GameScreen.MENU
            requestRecordName = false
        }
        music?.select(musicSelection)
        scheduleNextEvent()
    }

    /** Sleep until a visible clear step or gravity is due; paused games have no timer. */
    private fun scheduleNextEvent() {
        val timer = timer ?: return
        val state = game
        if (screen != GameScreen.PLAYING || !activeForeground || state == null) {
            timer.cancel()
            scheduledAt = null
            return
        }
        val deadline = if (state.clearingRows.isNotEmpty()) {
            val nextStep = ((clearElapsedMillis / LineClearAnimation.STEP_MILLIS + 1) *
                LineClearAnimation.STEP_MILLIS).coerceAtMost(LineClearAnimation.TOTAL_MILLIS)
            lastAnimationFrame + nextStep - clearElapsedMillis
        } else minOf(lastGravity + state.gravityMillis,
            if (engine.grounded(state)) lastLockFrame + state.lockRemaining else Long.MAX_VALUE)
        val nextDeadline = minOf(deadline, gestures.holdDeadline ?: Long.MAX_VALUE)
        if (scheduledAt == nextDeadline) return
        scheduledAt = nextDeadline
        timer.schedule((nextDeadline - clock()).coerceAtLeast(0)) {
            scheduledAt = null
            advanceFrame(clock())
            scheduleNextEvent()
        }
    }

    /** Advances game time; during a clear, only its 600 ms animation progresses, without gravity. */
    internal fun advanceFrame(now: Long) {
        if (screen != GameScreen.PLAYING || !activeForeground) return
        playedMillis += (now - lastPlayFrame).coerceAtLeast(0)
        lastPlayFrame = now
        if (consumeLockTime(now)) return
        gestures.advanceTime(now)
        val current = game ?: return
        if (current.clearingRows.isNotEmpty()) {
            clearElapsedMillis = (clearElapsedMillis + (now - lastAnimationFrame).coerceAtLeast(0)).coerceAtMost(LineClearAnimation.TOTAL_MILLIS)
            lastAnimationFrame = now
            if (clearElapsedMillis >= LineClearAnimation.TOTAL_MILLIS) {
                acceptState(current, engine.finishClear(current), now)
            }
            return
        }
        if (screen == GameScreen.PLAYING && game?.clearingRows?.isEmpty() == true && now - lastGravity >= current.gravityMillis) {
            lastGravity = now
            command(GameCommand.TICK)
        }
    }

    /** Synchronize before input so late touches cannot reset an already expired lock clock. */
    private fun consumeLockTime(now: Long): Boolean {
        val previous = game ?: return false
        val updated = engine.advanceLock(previous, now - lastLockFrame)
        lastLockFrame = now
        if (updated != previous) {
            acceptState(previous, updated, now)
            if (screen == GameScreen.PLAYING)
                feedbackEvent(previous, updated, GameCommand.TICK)?.let { feedback.play(it, soundEnabled, vibrationEnabled) }
        }
        return updated.generation != previous.generation || updated.clearingRows != previous.clearingRows || updated.gameOver
    }
    /** Applies Android's system gesture timings and tap-movement allowance. */
    fun configureGestures(config: GestureConfig) {
        gestures.cancel()
        gestureConfig = if (boardWidthDp > 0) config.copy(horizontalStepDistance = boardWidthDp / 12f) else config
        gestures = createGestures(gestureConfig)
        gestures.setEnabled(game?.clearingRows?.isEmpty() != false)
    }

    /** Highest saved score for [mode] under the current rules version. */
    private fun bestFor(mode: Difficulty): Int = results.filter { it.difficulty == mode && it.rulesVersion == GameRules.VERSION }
        .maxOfOrNull { it.score } ?: 0

    /** Persists [value] for future games and refreshes the displayed record when no game is active. */
    fun chooseDifficulty(value: Difficulty) {
        difficulty = value
        preferences.edit().putString("difficulty", value.id).apply()
        if (game == null || game?.gameOver == true) record = bestFor(value)
    }

    /** Applies only to future games; a resumed session keeps its own starting level. */
    fun chooseStartingLevel(value: Int) {
        require(value in GameRules.MIN_STARTING_LEVEL..GameRules.MAX_STARTING_LEVEL)
        startingLevel = value
        preferences.edit().putInt("starting_level", value).apply()
    }

    /** Returns the current-rules record for the requested [mode]. */
    fun recordFor(mode: Difficulty): Int = bestFor(mode)

    /** Clears saved results without changing settings or the current game's state. */
    fun resetStatistics() {
        GameStorage.clearStatistics(preferences)
        results = emptyList()
        legacyRecord = 0
        record = 0
        recordAtStart = 0
        latestResult = null
        currentResultId = null
        requestRecordName = false
        saveSession()
    }

    /** Saves the board, bag, timers, and record baseline for game restoration after lifecycle changes. */
    private fun saveSession(now: Long = clock()) {
        val state = game ?: return
        val remaining = if (screen == GameScreen.PLAYING && state.clearingRows.isEmpty())
            (state.gravityMillis - (now - lastGravity).coerceAtLeast(0)).coerceIn(0, state.gravityMillis)
        else gravityRemaining
        sessionStore.write(GameSession(sessionId, state, engine.remainingBag(), playedMillis,
            clearElapsedMillis, remaining, recordAtStart, finishedAt))
    }

    /** Persists only the landing ghost setting; Next is always visible. */
    fun setHints(enabled: Boolean) {
        hintsEnabled = enabled
        preferences.edit().putBoolean("hints", enabled).apply()
    }

    /** Persists sound preference and immediately stops active effects when disabled. */
    fun setSound(enabled: Boolean) {
        soundEnabled = enabled
        preferences.edit().putBoolean("sound", enabled).apply()
        if (!enabled) feedback.stopSound()
    }

    /** Persists vibration permission independently of sound. */
    fun setVibration(enabled: Boolean) {
        val wasEnabled = vibrationEnabled
        vibrationEnabled = enabled
        preferences.edit().putBoolean("vibration", enabled).apply()
        if (!enabled) feedback.stopVibration()
        else if (!wasEnabled) feedback.previewVibration()
    }

    /** Applies a known palette without altering the game. */
    fun setPalette(id: String) {
        paletteId = GamePalettes.find(id).id
        preferences.edit().putString("palette", paletteId).apply()
    }

    /** Stores fireworks elapsed time so Activity recreation does not replay them. */
    fun advanceVictoryAnimation(delta: Long) {
        if (screen == GameScreen.VICTORY)
            victoryAnimationMillis = (victoryAnimationMillis + delta.coerceAtLeast(0)).coerceAtMost(8000L)
    }

    /** Retains intro progress across Activity recreation; background time does not advance it. */
    fun advanceLaunchIntro(delta: Long) {
        if (activeForeground && launchIntroPending)
            launchIntroMillis = (launchIntroMillis + delta.coerceIn(0L, LaunchIntroMotion.DURATION))
                .coerceAtMost(LaunchIntroMotion.DURATION)
    }

    /** Skips the intro without sound, vibration, or activating a menu action. */
    fun finishLaunchIntro() { launchIntroMillis = LaunchIntroMotion.DURATION }

    /** Starts the next round of the same game only after explicit victory confirmation. */
    fun nextRound() {
        if (!activeForeground) return
        val state = game ?: return
        if (screen != GameScreen.VICTORY || !state.victoryPending) return
        game = engine.nextRound(state)
        gestures.cancel()
        gestures.setEnabled(true)
        feedback.stop()
        clearElapsedMillis = 0L
        lastGravity = clock()
        gravityRemaining = game!!.gravityMillis
        lastPlayFrame = lastGravity
        lastAnimationFrame = lastGravity
        screen = GameScreen.PLAYING
        lastLockFrame = clock()
        music?.setPlaying(musicEnabled)
        saveSession()
        scheduleNextEvent()
    }

    /** Opens settings while preserving and pausing the game and clear progress. */
    fun settings() {
        menu()
        screen = GameScreen.SETTINGS
        music?.setPlaying(musicEnabled && activeForeground)
    }

    /** Opens help while preserving the game and stopping its clock. */
    fun help() {
        menu()
        screen = GameScreen.HELP
    }

    /** Opens contacts while keeping the game paused until explicit resume. */
    fun contacts() {
        menu()
        screen = GameScreen.CONTACTS
    }

    /** Opens the local privacy policy without resuming game or music. */
    fun privacy() {
        menu()
        screen = GameScreen.PRIVACY
    }

    /** Opens license information while keeping the current game paused. */
    fun legal() {
        menu()
        screen = GameScreen.LEGAL
    }

    /** Starts a new game, resetting board, score, and unfinished gestures. */
    fun newGame() {
        if (!activeForeground) return
        sessionId = java.util.UUID.randomUUID().toString()
        finishedAt = 0L
        record = bestFor(difficulty)
        recordAtStart = record
        playedMillis = 0L
        lastPlayFrame = clock()
        currentResultId = null
        latestResult = null
        requestRecordName = false
        feedback.stop()
        gestures.cancel()
        game = engine.newGame(difficulty, startingLevel)
        gestures.setEnabled(true)
        clearElapsedMillis = 0L
        lastGravity = clock()
        gravityRemaining = game!!.gravityMillis
        lastAnimationFrame = clock()
        screen = GameScreen.PLAYING
        lastLockFrame = clock()
        music?.setPlaying(musicEnabled)
        saveSession()
        scheduleNextEvent()
    }

    /** Continues the saved interval; time spent in the background never counts. */
    fun resume() {
        if (!activeForeground || screen == GameScreen.PLAYING) return
        if (game == null || game?.gameOver == true) return
        if (game?.victoryPending == true) {
            screen = GameScreen.VICTORY
            return
        }
        if (game?.clearingRows?.isNotEmpty() == true) feedback.resumeClear(LineClearAnimation.TOTAL_MILLIS - clearElapsedMillis, vibrationEnabled)
        gestures.cancel()
        gestures.setEnabled(game?.clearingRows?.isEmpty() == true)
        lastGravity = clock() - (game!!.gravityMillis - gravityRemaining.coerceAtMost(game!!.gravityMillis))
        lastAnimationFrame = clock()
        screen = GameScreen.PLAYING
        lastPlayFrame = clock()
        lastLockFrame = clock()
        music?.setPlaying(musicEnabled)
        scheduleNextEvent()
    }

    /** Freeze clocks without issuing a final gravity tick or completing a clear. */
    fun pause() {
        val now = clock()
        if (screen == GameScreen.PLAYING) {
            playedMillis += (now - lastPlayFrame).coerceAtLeast(0)
            lastPlayFrame = now
            game = game?.let { engine.advanceLock(it, now - lastLockFrame, allowLock = false) }
            lastLockFrame = now
            if (game?.clearingRows?.isNotEmpty() == true) {
                clearElapsedMillis = (clearElapsedMillis + (now - lastAnimationFrame).coerceAtLeast(0))
                    .coerceAtMost(LineClearAnimation.TOTAL_MILLIS)
            } else {
                val interval = game?.gravityMillis ?: Difficulty.INITIAL_MILLIS
                gravityRemaining = (interval - (now - lastGravity).coerceAtLeast(0)).coerceIn(0, interval)
            }
            screen = GameScreen.MENU
        } else if (screen == GameScreen.VICTORY) screen = GameScreen.MENU
        music?.setPlaying(false)
        feedback.stop()
        gestures.cancel()
        saveSession(now)
        scheduleNextEvent()
    }

    /** Backgrounding stops both gameplay and settings music preview. */
    fun onBackground() {
        foreground = false
        pause()
    }

    /** Returning to settings resumes music preview; gameplay remains explicitly paused. */
    fun onForeground() {
        foreground = true
        if (screen == GameScreen.SETTINGS) music?.setPlaying(musicEnabled && activeForeground)
    }

    /** Pauses gameplay on focus loss and resumes settings preview only when focus returns. */
    fun onWindowFocusChanged(focused: Boolean) {
        windowFocused = focused
        if (!focused) pause()
        else if (screen == GameScreen.SETTINGS) music?.setPlaying(musicEnabled && activeForeground)
    }

    /** Opens the main menu while retaining the current game in memory. */
    fun menu() {
        pause()
        screen = GameScreen.MENU
    }

    /** Applies commands only while both lifecycle and window focus permit active play. */
    fun command(command: GameCommand) {
        if (screen != GameScreen.PLAYING || !activeForeground) return
        if (command == GameCommand.PAUSE) {
            pause()
            return
        }
        val now = clock()
        playedMillis += (now - lastPlayFrame).coerceAtLeast(0)
        lastPlayFrame = now
        if (consumeLockTime(now)) return
        val previous = game ?: return
        val updated = engine.apply(previous, command)
        if (command == GameCommand.HOLD) gestures.cancel()
        acceptState(previous, updated, now)
        if (screen == GameScreen.PLAYING)
            feedbackEvent(previous, updated, command)?.let { feedback.play(it, soundEnabled, vibrationEnabled) }
    }

    /** Publishes and checkpoints state; a spawn rebases the pointer instead of canceling it. */
    private fun acceptState(previous: GameState, updated: GameState, now: Long) {
        game = updated
        if (previous.clearingRows.isEmpty() && updated.clearingRows.isNotEmpty()) {
            gestures.setEnabled(false)
            clearElapsedMillis = 0L
            lastAnimationFrame = now
        }
        if (updated.generation != previous.generation) {
            gestures.onPieceChanged()
            gestures.setEnabled(updated.clearingRows.isEmpty())
            lastGravity = now
            gravityRemaining = updated.gravityMillis
            clearElapsedMillis = 0L
        }
        if (updated.victoryPending && !previous.victoryPending) {
            playedMillis += (now - lastPlayFrame).coerceAtLeast(0)
            lastPlayFrame = now
            gestures.cancel()
            feedback.stop()
            music?.setPlaying(false)
            screen = GameScreen.VICTORY
            victoryAnimationMillis = 0L
            if (musicEnabled) music?.setMode(MusicMode.RECORD)
        } else if (updated.gameOver) {
            finishedAt = System.currentTimeMillis()
            // Journal before the history write, so a process death cannot lose or duplicate a record.
            saveSession(now)
            music?.setPlaying(false)
            saveResult(updated, now)
            gestures.cancel()
            screen = if (requestRecordName) GameScreen.RECORD else GameScreen.GAME_OVER
            if (requestRecordName && musicEnabled) music?.setMode(MusicMode.RECORD)
        }
        if (updated != previous && !updated.gameOver) saveSession(now)
        scheduleNextEvent()
    }

    /** Forwards touch start in dp with time measured in uptime milliseconds. */
    fun pointerDown(x: Float, y: Float, time: Long) {
        if (screen == GameScreen.PLAYING && activeForeground) gestures.down(x, y, time)
        scheduleNextEvent()
    }

    /** Forwards a single pointer's next position to the gesture recognizer. */
    fun pointerMove(x: Float, y: Float, time: Long) {
        if (screen == GameScreen.PLAYING && activeForeground) gestures.move(x, y, time)
        scheduleNextEvent()
    }

    /** A short tap makes one downward step without resetting normal gravity. */
    fun pointerUp(x: Float, y: Float, time: Long) {
        if (screen != GameScreen.PLAYING || !activeForeground) return
        gestures.up(x, y, time)
        scheduleNextEvent()
    }

    /** Cancels touch after a multi-touch event or disposal of the Compose handler. */
    fun cancelGesture() { gestures.cancel(); scheduleNextEvent() }

    /** Recalculates swipe step when board width changes; coordinates remain in dp. */
    fun setBoardWidth(widthDp: Float) {
        boardWidthDp = widthDp
        val step = widthDp / 12f
        if (step > 0 && step != gestureConfig.horizontalStepDistance) {
            configureGestures(gestureConfig.copy(horizontalStepDistance = step))
        }
    }

    /** Persists the music selection and starts it immediately in game or settings. */
    fun chooseMusic(selection: MusicSelection) {
        if (selection == musicSelection) return
        musicSelection = selection
        preferences.edit().putString("music_selection", selection.id).apply()
        music?.select(selection)
        music?.setPlaying(musicEnabled && activeForeground && screen in listOf(GameScreen.PLAYING, GameScreen.SETTINGS))
    }

    /** Opens saved results while keeping the current game paused. */
    fun showResults() {
        menu()
        screen = GameScreen.RESULTS
    }

    /** Records a result once; compares a new record against scores from before the game began. */
    private fun saveResult(state: GameState, now: Long) {
        if (currentResultId != null) return
        if (screen == GameScreen.PLAYING) playedMillis += (now - lastPlayFrame).coerceAtLeast(0)
        lastPlayFrame = now
        val result = results.firstOrNull { it.id == sessionId } ?: GameResult(sessionId, finishedAt,
            "Игрок", state.score, state.lines, state.level, playedMillis, completedRounds = state.completedRounds,
            difficulty = state.difficulty)
        latestResult = result
        currentResultId = result.id
        requestRecordName = state.score > recordAtStart
        if (requestRecordName && results.none { it.id == result.id }) {
            results = listOf(result) + results
            resultStore.write(results)
        }
        record = bestFor(state.difficulty)
    }

    /** Updates a record holder's name, replacing blank input with a neutral label. */
    fun saveRecordName(name: String) {
        val entered = name.trim().take(40)
        if (entered.isNotEmpty()) {
            playerName = entered
            preferences.edit().putString("player_name", entered).apply()
        }
        val resultName = entered.ifEmpty { "Игрок" }
        results = results.map { if (it.id == currentResultId) it.copy(name = resultName) else it }
        latestResult = latestResult?.copy(name = resultName)
        resultStore.write(results)
        requestRecordName = false
        music?.setPlaying(false)
        screen = GameScreen.GAME_OVER
    }

    /** Skips intro; skips record-name entry to results; leaves other pages for the menu. */
    fun back() {
        if (launchIntroPending) { finishLaunchIntro(); return }
        if (screen == GameScreen.PRIVACY || screen == GameScreen.LEGAL) { contacts(); return }
        if (screen == GameScreen.RECORD) saveRecordName("") else menu()
    }

    /** Closes audio resources when the Activity's model is finally destroyed. */
    override fun onCleared() {
        timer?.cancel()
        scheduledAt = null
        feedback.release()
        music?.release()
        super.onCleared()
    }
}
