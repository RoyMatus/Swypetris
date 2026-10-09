package ru.itoltec.swypetris

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.core.content.edit

/** A paused game is owned by the model while the ordinary menu is displayed. */
enum class GameScreen {
    MENU, SETTINGS, HELP, CONTACTS, PRIVACY, LEGAL, PLAYING, GAME_OVER, RESULTS, RECORD, VICTORY,
    ABSOLUTE_VICTORY, POSTCARD
}

/**
 * Владелец партии: соединяет движок, жесты, анимацию и рекорд; переживает пересоздание Activity.
 * Внутренний конструктор принимает исходное поле и часы для воспроизводимых проверок;
 * [autoTick] отключает автоматический цикл, позволяя тестам вызывать [Simulation.advanceFrame] вручную.
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
    internal val input = Input()
    internal val simulation = Simulation()
    internal val options = Options()
    internal val statistics = Statistics()
    internal val navigation = Navigation()
    internal val absolute = AbsoluteVictory()

    /** The standard Android constructor uses a monotonic clock and an automatic game loop. */
    constructor(application: Application) : this(application, null, SystemClock::uptimeMillis, true)
    private val preferences = GameStorage.preferences(application).also(GameStorage::migrate)
    private val resultStore = ResultStore(preferences)
    private val sessionStore = SessionStore(GameStorage.sessionPreferences(application))
    private val restored = if (initialState == null) sessionStore.read() else null
    private var sessionId = restored?.id ?: java.util.UUID.randomUUID().toString()
    private var finishedAt = restored?.finishedAt ?: 0L
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
    var absoluteName by mutableStateOf(restored?.absoluteName ?: "")
        private set
    var postcardReady by mutableStateOf(restored?.postcardReady ?: false)
        private set
    var absoluteNameError by mutableStateOf(false)
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
    private var recordAtStart = restored?.recordAtStart ?: statistics.bestFor()
    private var playedMillis = restored?.playedMillis ?: 0L
    private var lastPlayFrame = clock()
    private var gestureConfig = GestureConfig()
    private var boardWidthDp = 0f
    private val feedback = feedback ?: if (autoTick) AndroidGameFeedback(application) else SilentFeedback
    var soundEnabled by mutableStateOf(preferences.getBoolean("sound", true))
        private set
    var vibrationEnabled by mutableStateOf(preferences.getBoolean("vibration", true))
        private set
    var vibrationStrength by mutableStateOf(preferences.getInt("vibration_strength", MAX_VIBRATION_STRENGTH)
        .coerceIn(0, MAX_VIBRATION_STRENGTH))
        private set
    private val vibrationAllowed: Boolean get() = vibrationEnabled && vibrationStrength > 0
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
    var record by mutableStateOf(statistics.bestFor())
        private set
    private val holdFeedbackAllowed: Boolean get() =
        vibrationAllowed && activeForeground && screen == GameScreen.PLAYING
    private var gestures = input.createGestures(GestureConfig())
    private var lastGameFrame = clock()
    private var timeline = GameTimeline(restored?.gravityRemainingNanos ?: (game?.gravityNanos ?:
        GameRules.gravityNanos(1)),
        restored?.lockFractionNanos ?: 0, (restored?.clearMillis ?: 0) * GameRules.NANOS_PER_MILLI +
            (restored?.clearFractionNanos ?: 0))
    private var advancing = false
    var clearElapsedMillis by mutableStateOf(timeline.clearMillis)
        private set
    private val timer = timer ?: if (autoTick) AndroidGameTimer() else null
    private var scheduledAt: Long? = null

    init {
        this.feedback.setVibrationStrength(vibrationStrength)
        restored?.let { engine.restoreBag(it.bag) }
        game?.takeIf { it.absoluteVictory }?.let {
            game = engine.checkVictory(it)
            if (finishedAt == 0L) finishedAt = System.currentTimeMillis()
            statistics.saveResult(game!!, clock())
            requestRecordName = false
            if (initialState != null) screen = absolute.destination()
            simulation.saveSession()
        }
        gestures.setEnabled(game?.clearingRows?.isEmpty() != false)
        if (restored?.state?.gameOver == true) {
            statistics.saveResult(restored.state, clock())
            screen = GameScreen.MENU
            requestRecordName = false
        }
        music?.select(musicSelection)
        if (autoTick) simulation.syncOrdinaryMusic()
        simulation.scheduleNextEvent()
    }

    /** Retains the looping celebration phase across Activity recreation and pauses. */
    fun advanceVictoryAnimation(delta: Long) {
        if (activeForeground && screen in listOf(GameScreen.VICTORY, GameScreen.ABSOLUTE_VICTORY, GameScreen.POSTCARD))
            victoryAnimationMillis = VictoryMotion.advance(victoryAnimationMillis, delta)
    }

    /** Terminal flow operations share the model's session and journal; they never resume play. */
    internal inner class AbsoluteVictory {
        fun destination(): GameScreen = if (postcardReady) GameScreen.POSTCARD else GameScreen.ABSOLUTE_VICTORY

        /** The terminal flow persists input independently of whether its Activity is visible. */
        fun changeName(name: String) {
            if (screen != GameScreen.ABSOLUTE_VICTORY) return
            val limited = name.replace('\n', ' ').replace('\r', ' ').take(MAX_PLAYER_NAME_LENGTH)
            absoluteName = if (limited.lastOrNull()?.isHighSurrogate() == true) limited.dropLast(1) else limited
            absoluteNameError = false
            simulation.saveSession()
        }

        /** Validation never substitutes a default name for a missing postcard recipient. */
        fun preparePostcard() {
            if (!activeForeground || screen != GameScreen.ABSOLUTE_VICTORY) return
            val entered = absoluteName.trim()
            if (entered.isEmpty()) { absoluteNameError = true; return }
            absoluteName = entered
            playerName = entered
            preferences.edit { putString("player_name", entered) }
            results = results.map { if (it.id == currentResultId) it.copy(name = entered) else it }
            latestResult = latestResult?.copy(name = entered)
            resultStore.write(results)
            postcardReady = true
            screen = GameScreen.POSTCARD
            simulation.saveSession()
        }

        fun finish(state: GameState, now: Long) {
            finishedAt = System.currentTimeMillis()
            simulation.saveSession()
            statistics.saveResult(state, now)
            requestRecordName = false
            gestures.cancel()
            feedback.stop()
            music?.setPlaying(false)
            screen = GameScreen.ABSOLUTE_VICTORY
            victoryAnimationMillis = 0L
            if (musicEnabled) music?.setMode(MusicMode.RECORD)
        }
    }

    /** Retains intro progress across Activity recreation; background time does not advance it. */
    fun advanceLaunchIntro(delta: Long) {
        if (activeForeground && launchIntroPending)
            launchIntroMillis = (launchIntroMillis + delta.coerceIn(0L, LaunchIntroMotion.DURATION))
                .coerceAtMost(LaunchIntroMotion.DURATION)
    }

    /** Starts the next round of the same game only after explicit victory confirmation. */
    fun nextRound() {
        if (!activeForeground || screen != GameScreen.VICTORY) return
        val state = game ?: return
        if (state.victoryPending) {
            game = engine.nextRound(state)
            gestures.cancel()
            gestures.setEnabled(true)
            feedback.stop()
            clearElapsedMillis = 0L
            lastGameFrame = clock()
            lastPlayFrame = lastGameFrame
            timeline = GameTimeline(game!!.gravityNanos)
            screen = GameScreen.PLAYING
            music?.setPlaying(musicEnabled)
            simulation.saveSession()
            simulation.scheduleNextEvent()
        }
    }

    /** Starts a new game, resetting board, score, and unfinished gestures. */
    fun newGame() {
        if (!activeForeground) return
        sessionId = java.util.UUID.randomUUID().toString()
        finishedAt = 0L
        record = statistics.bestFor()
        recordAtStart = record
        playedMillis = 0L
        lastPlayFrame = clock()
        currentResultId = null
        latestResult = null
        requestRecordName = false
        absoluteName = ""
        absoluteNameError = false
        postcardReady = false
        feedback.stop()
        gestures.cancel()
        game = engine.newGame(startingLevel)
        gestures.setEnabled(true)
        clearElapsedMillis = 0L
        lastGameFrame = clock()
        lastPlayFrame = lastGameFrame
        timeline = GameTimeline(game!!.gravityNanos)
        screen = GameScreen.PLAYING
        music?.setPlaying(musicEnabled)
        simulation.saveSession()
        simulation.scheduleNextEvent()
    }

    /** Continues the saved interval; time spent in the background never counts. */
    fun resume() {
        if (!activeForeground || screen == GameScreen.PLAYING) return
        if (game == null || game?.gameOver == true) return
        if (game?.absoluteVictory == true) {
            screen = absolute.destination()
        } else if (game?.victoryPending == true) {
            screen = GameScreen.VICTORY
        } else {
            if (game?.clearingRows?.isNotEmpty() == true) feedback
                .resumeClear(LineClearAnimation.TOTAL_MILLIS - clearElapsedMillis, vibrationAllowed)
            gestures.cancel()
            gestures.setEnabled(game?.clearingRows?.isEmpty() == true)
            lastGameFrame = clock()
            lastPlayFrame = lastGameFrame
            screen = GameScreen.PLAYING
            music?.setPlaying(musicEnabled)
            simulation.scheduleNextEvent()
        }
    }

    /** Freeze clocks without issuing a final gravity tick or completing a clear. */
    fun pause() {
        val now = clock()
        if (screen == GameScreen.PLAYING) {
            playedMillis += (now - lastPlayFrame).coerceAtLeast(0)
            lastPlayFrame = now
            game = game?.let { timeline.freeze(it, now - lastGameFrame, engine) }
            lastGameFrame = now
            clearElapsedMillis = timeline.clearMillis
            screen = GameScreen.MENU
        } else if (screen in listOf(GameScreen.VICTORY, GameScreen.ABSOLUTE_VICTORY, GameScreen.POSTCARD))
            screen = GameScreen.MENU
        simulation.syncOrdinaryMusic()
        feedback.stop()
        gestures.cancel()
        simulation.saveSession()
        simulation.scheduleNextEvent()
    }

    /** Backgrounding stops both gameplay and settings music preview. */
    fun onBackground() {
        foreground = false
        pause()
    }

    /** Returning to the foreground restores only the music appropriate for the current screen. */
    fun onForeground() {
        foreground = true
        simulation.syncOrdinaryMusic()
    }

    /** Focus loss silences playback; focus return restores the current screen's ordinary mode. */
    fun onWindowFocusChanged(focused: Boolean) {
        windowFocused = focused
        if (!focused) pause()
        else simulation.syncOrdinaryMusic()
    }

    /** Closes audio resources when the Activity's model is finally destroyed. */
    override fun onCleared() {
        timer?.cancel()
        scheduledAt = null
        feedback.release()
        music?.release()
        super.onCleared()
    }
    /** Input operations use the model's state; they do not keep a second copy. */
    internal inner class Input {
        fun createGestures(config: GestureConfig) = GestureController(config, ::command).also {
            it.onHoldReady = { if (holdFeedbackAllowed && game?.holdUsed == false) feedback.holdReady() }
        }

        /** Applies Android's system gesture timings and tap-movement allowance. */
        fun configureGestures(config: GestureConfig) {
            gestures.cancel()
            gestureConfig = if (boardWidthDp > 0) config.copy(horizontalStepDistance = boardWidthDp / 12f) else config
            gestures = createGestures(gestureConfig)
            gestures.setEnabled(game?.clearingRows?.isEmpty() != false)
        }

        /** Applies commands only while both lifecycle and window focus permit active play. */
        fun command(command: GameCommand) {
            if (screen != GameScreen.PLAYING || !activeForeground) return
            if (command == GameCommand.PAUSE) {
                pause()
            } else {
                val now = clock()
                val generation = game?.generation
                simulation.advanceGameTime(now)
                val previous = game
                if (screen == GameScreen.PLAYING && game?.generation == generation && previous != null) {
                    val updated = engine.apply(previous, command)
                    if (command == GameCommand.HOLD) gestures.cancel()
                    timeline.observe(previous, updated)
                    simulation.acceptState(previous, updated, now)
                    if (screen == GameScreen.PLAYING)
                        feedbackEvent(previous, updated, command)?.let {
                            feedback.play(it, soundEnabled, vibrationAllowed)
                        }
                }
            }
        }

        /** Forwards touch start in dp with time measured in uptime milliseconds. */
        fun pointerDown(x: Float, y: Float, time: Long) {
            if (screen == GameScreen.PLAYING && activeForeground) gestures.down(x, y, time)
            simulation.scheduleNextEvent()
        }

        /** Forwards a single pointer's next position to the gesture recognizer. */
        fun pointerMove(x: Float, y: Float, time: Long) {
            if (screen == GameScreen.PLAYING && activeForeground) gestures.move(x, y, time)
            simulation.scheduleNextEvent()
        }

        /** A short tap makes one downward step without resetting normal gravity. */
        fun pointerUp(x: Float, y: Float, time: Long) {
            if (screen != GameScreen.PLAYING || !activeForeground) return
            gestures.up(x, y, time)
            simulation.scheduleNextEvent()
        }

        /** Cancels touch after a multi-touch event or disposal of the Compose handler. */
        fun cancelGesture() { gestures.cancel(); simulation.scheduleNextEvent() }

        /** Recalculates swipe step when board width changes; coordinates remain in dp. */
        fun setBoardWidth(widthDp: Float) {
            boardWidthDp = widthDp
            val step = widthDp / 12f
            if (step > 0 && step != gestureConfig.horizontalStepDistance) {
                configureGestures(gestureConfig.copy(horizontalStepDistance = step))
            }
        }
    }

    /** Simulation operations use the model's state; they do not keep a second copy. */
    internal inner class Simulation {
        /** Keeps ordinary navigation music mutually exclusive and lifecycle-aware. */
        fun syncOrdinaryMusic() {
            val next = when {
                !musicEnabled || !activeForeground -> MusicMode.SILENT
                screen in listOf(GameScreen.MENU, GameScreen.SETTINGS, GameScreen.HELP, GameScreen.CONTACTS,
                    GameScreen.PRIVACY, GameScreen.LEGAL, GameScreen.RESULTS) -> MusicMode.MENU
                screen == GameScreen.PLAYING -> MusicMode.GAME
                else -> MusicMode.SILENT
            }
            music?.setMode(next)
        }

        /** Sleep until a visible clear step or gravity is due; paused games have no timer. */
        fun scheduleNextEvent() {
            val timer = timer ?: return
            val state = game
            if (screen != GameScreen.PLAYING || !activeForeground || state == null) {
                timer.cancel()
                scheduledAt = null
            } else {
            val nanos = timeline.nextEventNanos(state, engine)
            val deadline = lastGameFrame + (nanos + GameRules.NANOS_PER_MILLI - 1) / GameRules.NANOS_PER_MILLI
            val nextDeadline = minOf(deadline, gestures.holdDeadline ?: Long.MAX_VALUE)
            if (scheduledAt != nextDeadline) {
            scheduledAt = nextDeadline
            timer.schedule((nextDeadline - clock()).coerceAtLeast(0)) {
                scheduledAt = null
                advanceFrame(clock())
                scheduleNextEvent()
            }
            }
            }
        }

        /** Account for all elapsed time, ordering events independently of callback frequency. */
        fun advanceFrame(now: Long) {
            if (screen != GameScreen.PLAYING || !activeForeground) return
            advanceGameTime(now)
            if (screen == GameScreen.PLAYING) gestures.advanceTime(now)
            scheduleNextEvent()
        }

        fun advanceGameTime(now: Long) {
            val current = game ?: return
            val elapsed = (now - lastGameFrame).coerceAtLeast(0)
            val playedBefore = playedMillis
            lastPlayFrame = now
            lastGameFrame = now
            advancing = true
            try {
                game = timeline.advance(current, elapsed, engine) { previous, updated ->
                    playedMillis = playedBefore + timeline.advancedNanos / GameRules.NANOS_PER_MILLI
                    acceptState(previous, updated, now)
                    if (screen == GameScreen.PLAYING)
                        feedbackEvent(previous, updated, GameCommand.TICK)?.let { feedback.play(it, soundEnabled,
                            vibrationAllowed) }
                }
            } finally { advancing = false }
            playedMillis = playedBefore + timeline.advancedNanos / GameRules.NANOS_PER_MILLI
            clearElapsedMillis = timeline.clearMillis
            if (game != current || elapsed > 0) saveSession()
        }

        /** Saves the board, bag, timers, and record baseline for game restoration after lifecycle changes. */
        fun saveSession() {
            val state = game ?: return
            sessionStore.write(GameSession(sessionId, state, engine.remainingBag(), playedMillis,
                timeline.clearMillis, timeline.gravityRemainingNanos, recordAtStart, finishedAt,
                timeline.lockFractionNanos, timeline.clearElapsedNanos % GameRules.NANOS_PER_MILLI,
                absoluteName, postcardReady))
        }

        /** Publishes and checkpoints state; a spawn rebases the pointer instead of canceling it. */
        fun acceptState(previous: GameState, updated: GameState, now: Long) {
            game = updated
            if (previous.clearingRows.isEmpty() && updated.clearingRows.isNotEmpty()) {
                gestures.setEnabled(false)
                clearElapsedMillis = 0L
            }
            if (updated.generation != previous.generation) {
                gestures.onPieceChanged()
                gestures.setEnabled(updated.clearingRows.isEmpty())
                clearElapsedMillis = 0L
            }
            acceptConclusion(previous, updated, now)
            if (!advancing) {
                clearElapsedMillis = timeline.clearMillis
                if (updated != previous && !updated.gameOver) saveSession()
                scheduleNextEvent()
            }
        }

        private fun acceptConclusion(previous: GameState, updated: GameState, now: Long) {
            when {
                updated.absoluteVictory && !previous.absoluteVictory -> absolute.finish(updated, now)
                updated.victoryPending && !previous.victoryPending -> {
                    playedMillis += (now - lastPlayFrame).coerceAtLeast(0)
                    lastPlayFrame = now
                    gestures.cancel()
                    feedback.stop()
                    music?.setPlaying(false)
                    screen = GameScreen.VICTORY
                    victoryAnimationMillis = 0L
                    if (musicEnabled) music?.setMode(MusicMode.RECORD)
                }
                updated.gameOver -> {
                    finishedAt = System.currentTimeMillis()
                    // Journal before the history write, so a process death cannot lose or duplicate a record.
                    saveSession()
                    music?.setPlaying(false)
                    statistics.saveResult(updated, now)
                    gestures.cancel()
                    screen = if (requestRecordName) GameScreen.RECORD else GameScreen.GAME_OVER
                    if (requestRecordName && musicEnabled) music?.setMode(MusicMode.RECORD)
                }
            }
        }
    }

    /** Options operations use the model's state; they do not keep a second copy. */
    internal inner class Options {
        /** Applies only to future games; a resumed session keeps its own starting level. */
        fun chooseStartingLevel(value: Int) {
            require(value in GameRules.MIN_STARTING_LEVEL..GameRules.MAX_STARTING_LEVEL)
            startingLevel = value
            preferences.edit().putInt("starting_level", value).apply()
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
            else if (!wasEnabled && vibrationAllowed) feedback.previewVibration()
        }

        /** Takes effect immediately while keeping the separate vibration toggle intact. */
        fun setVibrationStrength(percent: Int) {
            require(percent in 0..MAX_VIBRATION_STRENGTH)
            vibrationStrength = percent
            preferences.edit { putInt("vibration_strength", percent) }
            feedback.setVibrationStrength(percent)
        }

        /** Applies a known palette without altering the game. */
        fun setPalette(id: String) {
            paletteId = GamePalettes.find(id).id
            preferences.edit().putString("palette", paletteId).apply()
        }

        /** Persists the selection; ordinary menus retain their theme and gameplay uses the playlist. */
        fun chooseMusic(selection: MusicSelection) {
            if (selection == musicSelection) return
            musicSelection = selection
            preferences.edit().putString("music_selection", selection.id).apply()
            music?.select(selection)
            simulation.syncOrdinaryMusic()
        }
    }

    /** Statistics operations use the model's state; they do not keep a second copy. */
    internal inner class Statistics {
        /** Historical rules and difficulty records remain separate from the new Marathon record. */
        fun bestFor(): Int = results.filter { it.difficulty == null && it.rulesVersion == GameRules.VERSION }
            .maxOfOrNull { it.score } ?: 0

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
            simulation.saveSession()
        }

        /** Records a result once; compares a new record against scores from before the game began. */
        fun saveResult(state: GameState, now: Long) {
            if (currentResultId != null) return
            if (screen == GameScreen.PLAYING) playedMillis += (now - lastPlayFrame).coerceAtLeast(0)
            lastPlayFrame = now
            val result = results.firstOrNull { it.id == sessionId } ?: GameResult(sessionId, finishedAt,
                "Игрок", state.score, state.lines, state.level, playedMillis, completedRounds = state.completedRounds)
            latestResult = result
            currentResultId = result.id
            requestRecordName = state.score > recordAtStart
            if (requestRecordName && results.none { it.id == result.id }) {
                results = listOf(result) + results
                resultStore.write(results)
            }
            record = bestFor()
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
    }

    /** Navigation operations use the model's state; they do not keep a second copy. */
    internal inner class Navigation {
        /** Skips the intro without sound, vibration, or activating a menu action. */
        fun finishLaunchIntro() { launchIntroMillis = LaunchIntroMotion.DURATION }

        /** Opens settings while preserving and pausing the game and clear progress. */
        fun settings() {
            menu()
            screen = GameScreen.SETTINGS
            simulation.syncOrdinaryMusic()
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

        /** Opens the main menu while retaining the current game in memory. */
        fun menu() {
            pause()
            screen = GameScreen.MENU
            simulation.syncOrdinaryMusic()
        }

        /** Opens saved results while keeping the current game paused. */
        fun showResults() {
            menu()
            screen = GameScreen.RESULTS
        }

        /** Skips intro; skips record-name entry to results; leaves other pages for the menu. */
        fun back() {
            if (launchIntroPending) { finishLaunchIntro(); return }
            if (screen == GameScreen.PRIVACY || screen == GameScreen.LEGAL) { contacts(); return }
            if (screen == GameScreen.RECORD) statistics.saveRecordName("") else menu()
        }
    }

}
