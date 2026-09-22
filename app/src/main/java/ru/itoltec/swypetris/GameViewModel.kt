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

/** Экраны приложения; PAUSED сохраняет поле и останавливает игровые часы. */
enum class GameScreen { MENU, SETTINGS, HELP, CONTACTS, PRIVACY, PLAYING, PAUSED, GAME_OVER, RESULTS, RECORD, VICTORY }

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
    private val music = musicPlayback ?: if (autoTick) GameMusic(application) else null
    val legacyRecord = GameStorage.legacyRecord(preferences)
    var results by mutableStateOf(resultStore.read())
        private set
    var currentResultId by mutableStateOf<String?>(null)
        private set
    var requestRecordName by mutableStateOf(false)
        private set
    var playerName by mutableStateOf(preferences.getString("player_name", "") ?: "")
        private set
    var musicSelection by mutableStateOf(MusicSelection.restore(preferences.getString("music_selection", null),
        preferences.getBoolean("music", true)))
        private set
    val musicEnabled: Boolean get() = musicSelection != MusicSelection.Off
    private var foreground = true
    var launchIntroMillis by mutableStateOf(if (showLaunchIntro) 0L else LaunchIntroMotion.DURATION)
        private set
    val launchIntroPending by derivedStateOf { launchIntroMillis < LaunchIntroMotion.DURATION }
    val launchLogoAssembled by derivedStateOf { launchIntroMillis >= 2650L }
    private var recordAtStart = preferences.getInt("record_v4", 0)
    private var playedMillis = 0L
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
    var game by mutableStateOf<GameState?>(initialState)
        private set
    var screen by mutableStateOf(if (initialState == null) GameScreen.MENU else GameScreen.PLAYING)
        private set
    var record by mutableStateOf(preferences.getInt("record_v4", 0))
        private set
    private var gestures = GestureController(GestureConfig(), ::command)
    private var lastGravity = clock()

    /** Прошедшее игровое время удаления; не увеличивается в меню и на паузе. */
    var clearElapsedMillis by mutableStateOf(0L)
        private set
    private var lastAnimationFrame = clock()

    init {
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
        if (screen != GameScreen.PLAYING) return
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
        val wasSoft = gestures.softDropping
        gestures.advance(now)
        if (gestures.softDropping || wasSoft) lastGravity = now
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
        if (foreground && launchIntroPending)
            launchIntroMillis = (launchIntroMillis + delta.coerceIn(0L, LaunchIntroMotion.DURATION))
                .coerceAtMost(LaunchIntroMotion.DURATION)
    }

    /** Пропускает заставку без звука, вибрации и запуска действия меню. */
    fun finishLaunchIntro() { launchIntroMillis = LaunchIntroMotion.DURATION }

    /** Начинает следующий круг той же партии только после явного подтверждения победы. */
    fun nextRound() {
        val state = game ?: return
        if (screen != GameScreen.VICTORY || !state.victoryPending) return
        game = engine.nextRound(state)
        gestures.cancel()
        feedback.stop()
        clearElapsedMillis = 0L
        lastGravity = clock()
        lastPlayFrame = lastGravity
        lastAnimationFrame = lastGravity
        screen = GameScreen.PLAYING
        music?.setPlaying(musicEnabled)
    }

    /** Открывает настройки, сохраняя партию и прогресс очистки на паузе. */
    fun settings() {
        menu()
        screen = GameScreen.SETTINGS
        music?.setPlaying(musicEnabled && foreground)
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
        recordAtStart = record
        playedMillis = 0L
        lastPlayFrame = clock()
        currentResultId = null
        requestRecordName = false
        feedback.stop()
        gestures.cancel()
        game = engine.newGame()
        clearElapsedMillis = 0L
        lastGravity = clock()
        lastAnimationFrame = clock()
        screen = GameScreen.PLAYING
        music?.setPlaying(musicEnabled)
    }

    /** Продолжает партию с полным интервалом до следующего падения. */
    fun resume() {
        if (game == null || game?.gameOver == true) return
        if (game?.victoryPending == true) {
            screen = GameScreen.VICTORY
            return
        }
        if (game?.clearingRows?.isNotEmpty() == true) feedback.resumeClear(LineClearAnimation.TOTAL_MILLIS - clearElapsedMillis, vibrationEnabled)
        gestures.cancel()
        lastGravity = clock()
        lastAnimationFrame = clock()
        screen = GameScreen.PLAYING
        lastPlayFrame = clock()
        music?.setPlaying(musicEnabled)
    }

    /** Останавливает игру и отменяет повторы, в том числе при уходе в фон. */
    fun pause() {
        advanceFrame(clock())
        music?.setPlaying(false)
        feedback.stop()
        gestures.cancel()
        if (screen == GameScreen.PLAYING) screen = GameScreen.PAUSED
    }

    /** Фон останавливает и игру, и прослушивание в настройках. */
    fun onBackground() {
        foreground = false
        pause()
    }

    /** Возвращение в настройки продолжает прослушивание; игровая пауза остаётся явной. */
    fun onForeground() {
        foreground = true
        if (screen == GameScreen.SETTINGS) music?.setPlaying(musicEnabled)
    }

    /** Открывает главное меню, сохраняя текущую партию в памяти. */
    fun menu() {
        advanceFrame(clock())
        music?.setPlaying(false)
        feedback.stop()
        gestures.cancel()
        screen = GameScreen.MENU
    }

    /** Выполняет команду во время игры и отменяет старое касание при смене фигуры. */
    fun command(command: GameCommand) {
        if (screen != GameScreen.PLAYING) return
        if (command == GameCommand.PAUSE) {
            pause()
            return
        }
        val previous = game ?: return
        val updated = engine.apply(previous, command)
        acceptState(previous, updated, clock())
        if (screen == GameScreen.PLAYING)
            feedbackEvent(previous, updated, command)?.let { feedback.play(it, soundEnabled, vibrationEnabled) }
    }

    /** Публикует результат движка, сохраняет рекорд и отменяет жест в начале очистки. */
    private fun acceptState(previous: GameState, updated: GameState, now: Long) {
        game = updated
        if (previous.clearingRows.isEmpty() && updated.clearingRows.isNotEmpty()) {
            gestures.cancel()
            clearElapsedMillis = 0L
            lastAnimationFrame = now
        }
        if (updated.score > record) {
            record = updated.score
            preferences.edit().putInt("record_v4", record).apply()
        }
        if (updated.generation != previous.generation) {
            gestures.cancel()
            lastGravity = now
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
            music?.setPlaying(false)
            saveResult(updated, now)
            gestures.cancel()
            screen = if (requestRecordName) GameScreen.RECORD else GameScreen.GAME_OVER
            if (requestRecordName && musicEnabled) music?.setMode(MusicMode.RECORD)
        }
    }

    /** Передаёт начало касания: координаты в dp, время в uptimeMillis. */
    fun pointerDown(x: Float, y: Float, time: Long) {
        if (screen == GameScreen.PLAYING && game?.clearingRows?.isEmpty() == true) gestures.down(x, y, time)
    }

    /** Передаёт очередную позицию единственного пальца распознавателю. */
    fun pointerMove(x: Float, y: Float, time: Long) {
        if (screen == GameScreen.PLAYING) gestures.move(x, y, time)
    }

    /** Завершает касание и после ускоренного спуска заново отсчитывает гравитацию. */
    fun pointerUp(x: Float, y: Float, time: Long) {
        if (screen != GameScreen.PLAYING) return
        val wasSoft = gestures.softDropping
        gestures.up(x, y, time)
        if (wasSoft) lastGravity = time
    }

    /** Отменяет касание при нескольких пальцах или уничтожении обработчика Compose. */
    fun cancelGesture() = gestures.cancel()

    /** Пересчитывает шаг свайпа при изменении ширины поля; координаты остаются в dp. */
    fun setBoardWidth(widthDp: Float) {
        boardWidthDp = widthDp
        val step = widthDp / 10f / 1.2f
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
        music?.setPlaying(musicEnabled && foreground && screen in listOf(GameScreen.PLAYING, GameScreen.SETTINGS))
    }

    /** Открывает сохранённую историю, оставляя текущую партию на паузе. */
    fun showResults() {
        menu()
        screen = GameScreen.RESULTS
    }

    /** Записывает итог единожды; рекорд сравнивается с результатом до начала партии. */
    private fun saveResult(state: GameState, now: Long) {
        if (currentResultId != null) return
        playedMillis += (now - lastPlayFrame).coerceAtLeast(0)
        lastPlayFrame = now
        val result = GameResult(java.util.UUID.randomUUID().toString(), System.currentTimeMillis(),
            "Игрок", state.score, state.lines, state.level, playedMillis, completedRounds = state.completedRounds)
        currentResultId = result.id
        requestRecordName = state.score > recordAtStart
        results = listOf(result) + results
        resultStore.write(results)
    }

    /** Обновляет имя рекордной партии; пустую строку заменяет нейтральной подписью. */
    fun saveRecordName(name: String) {
        val clean = name.trim().take(40).ifEmpty { "Игрок" }
        playerName = clean
        preferences.edit().putString("player_name", clean).apply()
        results = results.map { if (it.id == currentResultId) it.copy(name = clean) else it }
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




