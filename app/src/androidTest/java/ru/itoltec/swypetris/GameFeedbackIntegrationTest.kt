package ru.itoltec.swypetris

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Проверяет маршрутизацию эффектов моделью без реального аудио и вибрации. */
@RunWith(AndroidJUnit4::class)
class GameFeedbackIntegrationTest {
    @get:org.junit.Rule val storage = IsolatedStorageRule()

    /** Рывок из удержания фиксирует фигуру один раз, начисляет путь и не двигает следующую. */
    @Test fun heldFlickDropsWithSingleFeedbackAndExactScore() {
        var now = 1000L
        val recorder = Recorder()
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O, y = 0), next = Tetromino.T), { now }, false, recorder)
        model.input.pointerDown(100f, 100f, now)
        now = 1500
        model.simulation.advanceFrame(now)
        model.input.pointerMove(140f, 100f, 1520)
        model.input.pointerMove(141f, 170f, 1570)
        val dropped = model.game!!
        assertEquals(1, dropped.generation)
        assertEquals(36, dropped.score)
        assertEquals(listOf(Triple(FeedbackEvent.DROP, true, true)), recorder.calls)
        model.input.pointerMove(141f, 170f, 1580)
        model.input.pointerUp(141f, 170f, 1590)
        assertEquals(dropped, model.game)
        assertFalse(model.game!!.accelerated)
        assertTrue(model.results.isEmpty())
    }

    /** One downward tap keeps its landing feedback until the next gravity step. */
    @Test fun tapThenGravityStillPlaysDrop() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val recorder = Recorder()
        var now = 1000L
        val model = GameViewModel(application,
            GameState(active = Piece(Tetromino.O, y = 17), next = Tetromino.T), { now }, false, recorder)
        model.input.pointerDown(50f, 50f, now)
        now += 100
        model.input.pointerUp(50f, 50f, now)
        assertTrue(model.game!!.accelerated)
        assertTrue(recorder.calls.isEmpty())
        now += 800
        model.simulation.advanceFrame(now)
        assertEquals(listOf(Triple(FeedbackEvent.DROP, true, true)), recorder.calls)
        assertFalse(model.game!!.accelerated)
        model.newGame()
        assertFalse(model.game!!.accelerated)
    }

    /** Запоминает параметры эффектов и остановки для проверки настроек и паузы. */
    private class Recorder : GameFeedback {
        val strengths = mutableListOf<Int>()
        override fun setVibrationStrength(percent: Int) { strengths += percent }
        var holdPulses = 0
        override fun holdReady() { holdPulses++ }
        val calls = mutableListOf<Triple<FeedbackEvent, Boolean, Boolean>>()
        var stops = 0
        var previews = 0
        var vibrationStops = 0
        var soundStops = 0
        /** Учитывает отдельную проверку мотора. */
        override fun previewVibration() { previews++ }
        /** Учитывает остановку мотора независимо от звука. */
        override fun stopVibration() { vibrationStops++ }
        /** Учитывает остановку звука независимо от мотора. */
        override fun stopSound() { soundStops++ }
        val remaining = mutableListOf<Long>()
        /** Запоминает остаток вибрации после паузы. */
        override fun resumeClear(remainingMillis: Long,
            vibration: Boolean) { if (vibration) remaining += remainingMillis }
        /** Сохраняет одно событие вместе с независимыми разрешениями. */
        override fun play(event: FeedbackEvent, sound: Boolean, vibration: Boolean) { calls += Triple(event, sound,
            vibration) }
        /** Учитывает остановку при переходах между экранами. */
        override fun stop() { stops++ }
        /** Не владеет устройствами и не требует освобождения. */
        override fun release() = Unit
    }

    @Test fun strengthAppliesImmediatelyPersistsAndZeroGatesDropPreviewAndHold() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val recorder = Recorder()
        var now = 1000L
        val model = GameViewModel(app, GameState(active = Piece(Tetromino.O), next = Tetromino.T),
            { now }, false, recorder)
        assertEquals(listOf(100), recorder.strengths)
        model.options.setVibrationStrength(50)
        assertEquals(50, recorder.strengths.last())
        assertEquals(50, GameViewModel(app, null, { 1000L }, false).vibrationStrength)
        model.options.setVibrationStrength(0)
        model.options.setVibration(false)
        model.options.setVibration(true)
        assertEquals(0, recorder.previews)
        model.input.pointerDown(100f, 200f, now)
        now += 300
        model.simulation.advanceFrame(now)
        model.input.cancelGesture()
        assertEquals(0, recorder.holdPulses)
        model.input.command(GameCommand.HARD_DROP)
        assertEquals(false, recorder.calls.last().third)
        model.options.setVibrationStrength(100)
        model.input.command(GameCommand.HARD_DROP)
        assertEquals(true, recorder.calls.last().third)
        model.options.setVibration(false)
        model.options.setVibrationStrength(50)
        model.input.command(GameCommand.HARD_DROP)
        assertEquals(false, recorder.calls.last().third)
        assertFalse(model.vibrationEnabled)
        assertEquals(50, GameViewModel(app, null, { 1000L }, false).vibrationStrength)
    }

    @Test fun zeroStrengthGatesClearAndResumeAndCorruptStoredStrengthIsClamped() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        GameStorage.preferences(app).edit().putInt("vibration_strength", 500).commit()
        val board = List(BoardGeometry.TOTAL_ROWS) { row -> List<Tetromino?>(10) { x ->
            if (row == BoardGeometry.row(19) && x !in 4..5) Tetromino.J else null } }
        val recorder = Recorder()
        val model = GameViewModel(app, GameState(board = board, active = Piece(Tetromino.O, y = 18),
            next = Tetromino.T), { 1000L }, false, recorder)
        assertEquals(100, model.vibrationStrength)
        model.options.setVibrationStrength(0)
        model.input.command(GameCommand.HARD_DROP)
        assertEquals(Triple(FeedbackEvent.CLEAR, true, false), recorder.calls.last())
        model.pause()
        model.resume()
        assertTrue(recorder.remaining.isEmpty())
        assertEquals(0, GameViewModel(app, null, { 1000L }, false).vibrationStrength)
    }

    /** Включение даёт один импульс; повтор значения и загрузка настройки не вибрируют. */
    @Test fun togglePreviewAndIndependentStops() {
        val recorder = Recorder()
        val model = GameViewModel(ApplicationProvider.getApplicationContext(), null, { 1000L }, false, recorder)
        assertEquals(0, recorder.previews)
        model.options.setVibration(false)
        model.options.setVibration(true)
        model.options.setVibration(true)
        assertEquals(1, recorder.previews)
        assertEquals(1, recorder.vibrationStops)
        assertEquals(0, recorder.soundStops)
        assertEquals(0, recorder.stops)
        model.options.setSound(false)
        assertEquals(1, recorder.soundStops)
        assertEquals(1, recorder.vibrationStops)
        assertEquals(0, recorder.stops)
    }

    /** Очистка звучит один раз, настройки независимы, продолжение не повторяет эффект. */
    @Test fun clearIsSingleAndResumeDoesNotReplay() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val board = List(BoardGeometry.TOTAL_ROWS) { y -> List<Tetromino?>(10) { x -> if (y == BoardGeometry
            .row(19) && x !in 4..5) Tetromino.J else null } }
        val initial = GameState(board = board, active = Piece(Tetromino.O, y = 18), next = Tetromino.T)
        var now = 1000L
        val recorder = Recorder()
        val model = GameViewModel(application, initial, { now }, false, recorder)
        val sound = model.soundEnabled
        val vibration = model.vibrationEnabled
        try {
            model.options.setSound(false)
            model.options.setVibration(true)
            model.input.command(GameCommand.HARD_DROP)
            assertEquals(listOf(Triple(FeedbackEvent.CLEAR, false, true)), recorder.calls)
            model.input.command(GameCommand.HARD_DROP)
            now += 100
            model.simulation.advanceFrame(now)
            model.pause()
            assertTrue(recorder.stops > 0)
            model.resume()
            assertEquals(listOf(500L), recorder.remaining)
            now += 500
            model.simulation.advanceFrame(now)
            assertEquals(1, recorder.calls.size)
            model.options.setSound(true)
            model.options.setVibration(false)
            model.input.command(GameCommand.HARD_DROP)
            assertEquals(Triple(FeedbackEvent.DROP, true, false), recorder.calls.last())
            model.navigation.menu()
            val count = recorder.calls.size
            model.input.command(GameCommand.HARD_DROP)
            assertEquals(count, recorder.calls.size)
        } finally {
            model.options.setSound(sound)
            model.options.setVibration(vibration)
        }
    }
}

