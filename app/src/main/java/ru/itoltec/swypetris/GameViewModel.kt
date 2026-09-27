package ru.itoltec.swypetris

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A paused game is owned by the model while the ordinary menu is displayed. */
enum class GameScreen { MENU, SETTINGS, HELP, CONTACTS, PRIVACY, PLAYING, GAME_OVER, RESULTS, RECORD, VICTORY }

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
    showLaunchIntro: Boolean = autoTick && initialState == null
) : AndroidViewModel(application) {
    /** Стандартный конструктор Android использует монотонные часы и автоматический игровой цикл. */
    constructor(application: Application) : this(application, null, SystemClock::uptimeMillis, true)
    private val preferences = GameStorage.preferences(application).also(GameStorage::migrate)
    private val resultStore = ResultStore(preferences)
    private val sessionStore = SessionStore(GameStorage.sessionPreferences(application))
    private val restored = if (initialState == null) sessionStore.read() else null
    private var sessionId = restored?.id ?: java.util.UUID.randomUUID().toString()
    private var finishedAt = restored?.finishedAt ?: 0L
    var difficulty by mutableStateOf(Difficulty.restore(preferences.getString("difficulty", null)))
        private set
    private val music = musicPlayback ?: if (autoTick) GameMusic(application) else null
    val legacyRecord = GameStorage.legacyRecord(preferences)
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
    private var gestures = GestureController(GestureConfig(), ::command)
    private var lastGravity = clock()
    private var gravityRemaining = restored?.gravityRemaining ?: (game?.gravityMillis ?: Difficulty.INITIAL_MILLIS)

    /** Прошедшее игровое время удаления; не увеличивается в меню и на паузе. */
    var clearElapsedMillis by mutableStateOf(restored?.clearMillis ?: 0L)
        private set
    private var lastAnimationFrame = clock()

    init {
        restored?.let { engine.restoreBag(it.bag) }
        gestures.setEnabled(game?.clearingRows?.isEmpty() != false)
        if (restored?.state?.gameOver == true) {
            saveResult(restored.state, clock())
            screen = GameScreen.MENU
            requestRecordName = false
        }
        music?.select(musicSelection)
        if (autoTick) viewModelScope.launch {
            while (true) {
                delay(16)
                advanceFrame(clock())
            }
        }
    }

    /** Продвигает игровые часы; во время удаления работают только её 600 мс, без гравитации. */
    internal fun advanceFrame(now: Long) {
        if (screen != GameScreen.PLAYING || !activeForeground) return
        val current = game ?: return
        playedMillis += (now - lastPlayFrame).coerceAtLeast(0)
        lastPlayFrame = now
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
    /** Применяет системные интервалы и допустимое смещение тапа, полученные от Android. */
    fun configureGestures(config: GestureConfig) {
        gestures.cancel()
        gestureConfig = if (boardWidthDp > 0) config.copy(horizontalStepDistance = boardWidthDp / 12f) else config
        gestures = GestureController(gestureConfig, ::command)
        gestures.setEnabled(game?.clearingRows?.isEmpty() != false)
    }

    private fun bestFor(mode: Difficulty): Int = results.filter { it.difficulty == mode && it.rulesVersion == GameRules.VERSION }
        .maxOfOrNull { it.score } ?: 0

    fun chooseDifficulty(value: Difficulty) {
        difficulty = value
        preferences.edit().putString("difficulty", value.id).apply()
        if (game == null || game?.gameOver == true) record = bestFor(value)
    }

    fun recordFor(mode: Difficulty): Int = bestFor(mode)

    private fun saveSession(now: Long = clock()) {
        val state = game ?: return
        val remaining = if (screen == GameScreen.PLAYING && state.clearingRows.isEmpty())
            (state.gravityMillis - (now - lastGravity).coerceAtLeast(0)).coerceIn(0, state.gravityMillis)
        else gravityRemaining
        sessionStore.write(GameSession(sessionId, state, engine.remainingBag(), playedMillis,
            clearElapsedMillis, remaining, recordAtStart, finishedAt))
    }

    /** Сохраняет совместную настройку тени падения и предварительного просмотра. */
    fun setHints(enabled: Boolean) {
        hintsEnabled = enabled
        preferences.edit().putBoolean("hints", enabled).apply()
    }

    /** Сохраняет звук; отключение немедленно останавливает текущие эффекты. */
    fun setSound(enabled: Boolean) {
        soundEnabled = enabled
        preferences.edit().putBoolean("sound", enabled).apply()
        if (!enabled) feedback.stopSound()
    }

    /** Сохраняет разрешение вибрации независимо от звука. */
    fun setVibration(enabled: Boolean) {
        val wasEnabled = vibrationEnabled
        vibrationEnabled = enabled
        preferences.edit().putBoolean("vibration", enabled).apply()
        if (!enabled) feedback.stopVibration()
        else if (!wasEnabled) feedback.previewVibration()
    }

    /** Применяет известную расцветку без изменения партии. */
    fun setPalette(id: String) {
        paletteId = GamePalettes.find(id).id
        preferences.edit().putString("palette", paletteId).apply()
    }

    /** Запоминает прошедшее время салюта, чтобы пересоздание экрана не повторяло его. */
    fun advanceVictoryAnimation(delta: Long) {
        if (screen == GameScreen.VICTORY)
            victoryAnimationMillis = (victoryAnimationMillis + delta.coerceAtLeast(0)).coerceAtMost(8000L)
    }

    /** Хранит прогресс заставки между пересозданиями Activity; фон не расходует её время. */
    fun advanceLaunchIntro(delta: Long) {
        if (activeForeground && launchIntroPending)
            launchIntroMillis = (launchIntroMillis + delta.coerceIn(0L, LaunchIntroMotion.DURATION))
                .coerceAtMost(LaunchIntroMotion.DURATION)
    }

    /** Пропускает заставку без звука, вибрации и запуска действия меню. */
    fun finishLaunchIntro() { launchIntroMillis = LaunchIntroMotion.DURATION }

    /** Начинает следующий круг той же партии только после явного подтверждения победы. */
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
        music?.setPlaying(musicEnabled)
        saveSession()
    }

    /** Открывает настройки, сохраняя партию и прогресс очистки на паузе. */
    fun settings() {
        menu()
        screen = GameScreen.SETTINGS
        music?.setPlaying(musicEnabled && activeForeground)
    }

    /** Открывает справку, сохраняя партию и останавливая игровые часы. */
    fun help() {
        menu()
        screen = GameScreen.HELP
    }

    /** Открывает контакты, сохраняя партию на паузе без автоматического возобновления. */
    fun contacts() {
        menu()
        screen = GameScreen.CONTACTS
    }

    /** Открывает локальную политику без возобновления партии или музыки. */
    fun privacy() {
        menu()
        screen = GameScreen.PRIVACY
    }

    /** Начинает новую партию, сбрасывая поле, очки и незавершённые жесты. */
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
        game = engine.newGame(difficulty)
        gestures.setEnabled(true)
        clearElapsedMillis = 0L
        lastGravity = clock()
        gravityRemaining = game!!.gravityMillis
        lastAnimationFrame = clock()
        screen = GameScreen.PLAYING
        music?.setPlaying(musicEnabled)
        saveSession()
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
        music?.setPlaying(musicEnabled)
    }

    /** Freeze clocks without issuing a final gravity tick or completing a clear. */
    fun pause() {
        val now = clock()
        if (screen == GameScreen.PLAYING) {
            playedMillis += (now - lastPlayFrame).coerceAtLeast(0)
            lastPlayFrame = now
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
    }

    /** Фон останавливает и игру, и прослушивание в настройках. */
    fun onBackground() {
        foreground = false
        pause()
    }

    /** Возвращение в настройки продолжает прослушивание; игровая пауза остаётся явной. */
    fun onForeground() {
        foreground = true
        if (screen == GameScreen.SETTINGS) music?.setPlaying(musicEnabled && activeForeground)
    }

    fun onWindowFocusChanged(focused: Boolean) {
        windowFocused = focused
        if (!focused) pause()
        else if (screen == GameScreen.SETTINGS) music?.setPlaying(musicEnabled && activeForeground)
    }

    /** Открывает главное меню, сохраняя текущую партию в памяти. */
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
        val previous = game ?: return
        val now = clock()
        playedMillis += (now - lastPlayFrame).coerceAtLeast(0)
        lastPlayFrame = now
        val updated = engine.apply(previous, command)
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
        if (updated != previous) saveSession(now)
    }

    /** Передаёт начало касания: координаты в dp, время в uptimeMillis. */
    fun pointerDown(x: Float, y: Float, time: Long) {
        if (screen == GameScreen.PLAYING && activeForeground) gestures.down(x, y, time)
    }

    /** Передаёт очередную позицию единственного пальца распознавателю. */
    fun pointerMove(x: Float, y: Float, time: Long) {
        if (screen == GameScreen.PLAYING && activeForeground) gestures.move(x, y, time)
    }

    /** A short tap makes one downward step without resetting normal gravity. */
    fun pointerUp(x: Float, y: Float, time: Long) {
        if (screen != GameScreen.PLAYING || !activeForeground) return
        gestures.up(x, y, time)
    }

    /** Отменяет касание при нескольких пальцах или уничтожении обработчика Compose. */
    fun cancelGesture() = gestures.cancel()

    /** Пересчитывает шаг свайпа при изменении ширины поля; координаты остаются в dp. */
    fun setBoardWidth(widthDp: Float) {
        boardWidthDp = widthDp
        val step = widthDp / 12f
        if (step > 0 && step != gestureConfig.horizontalStepDistance) {
            configureGestures(gestureConfig.copy(horizontalStepDistance = step))
        }
    }

    /** Сохраняет режим и немедленно запускает выбранную музыку в игре или настройках. */
    fun chooseMusic(selection: MusicSelection) {
        if (selection == musicSelection) return
        musicSelection = selection
        preferences.edit().putString("music_selection", selection.id).apply()
        music?.select(selection)
        music?.setPlaying(musicEnabled && activeForeground && screen in listOf(GameScreen.PLAYING, GameScreen.SETTINGS))
    }

    /** Открывает сохранённую историю, оставляя текущую партию на паузе. */
    fun showResults() {
        menu()
        screen = GameScreen.RESULTS
    }

    /** Записывает итог единожды; рекорд сравнивается с результатом до начала партии. */
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

    /** Обновляет имя рекордной партии; пустую строку заменяет нейтральной подписью. */
    fun saveRecordName(name: String) {
        val clean = name.trim().take(40).ifEmpty { "Игрок" }
        playerName = clean
        preferences.edit().putString("player_name", clean).apply()
        results = results.map { if (it.id == currentResultId) it.copy(name = clean) else it }
        latestResult = latestResult?.copy(name = clean)
        resultStore.write(results)
        requestRecordName = false
        music?.setPlaying(false)
        screen = GameScreen.GAME_OVER
    }

    /** Пропускает заставку; с рекорда открывает итоги без имени, с остальных страниц — меню. */
    fun back() {
        if (launchIntroPending) { finishLaunchIntro(); return }
        if (screen == GameScreen.PRIVACY) { contacts(); return }
        if (screen == GameScreen.RECORD) saveRecordName("") else menu()
    }

    /** Закрывает аудиоресурсы при окончательном уничтожении модели Activity. */
    override fun onCleared() {
        feedback.release()
        music?.release()
        super.onCleared()
    }
}




