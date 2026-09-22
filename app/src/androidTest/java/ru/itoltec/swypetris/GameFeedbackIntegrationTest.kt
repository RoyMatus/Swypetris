package ru.itoltec.swypetris

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
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
        model.pointerDown(100f, 100f, now)
        now = 1500
        model.advanceFrame(now)
        model.pointerMove(140f, 100f, 1520)
        model.pointerMove(141f, 150f, 1570)
        val dropped = model.game!!
        assertEquals(1, dropped.generation)
        assertEquals(18, dropped.score)
        assertEquals(listOf(Triple(FeedbackEvent.DROP, true, true)), recorder.calls)
        model.pointerMove(200f, 200f, 1580)
        model.pointerUp(200f, 200f, 1590)
        assertEquals(dropped, model.game)
        assertFalse(model.game!!.accelerated)
        assertTrue(model.results.isEmpty())
    }

    /** Удержание и отпускание оставляют признак ускорения до последнего шага гравитации. */
    @Test fun releaseThenGravityStillPlaysDrop() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val recorder = Recorder()
        var now = 1000L
        val model = GameViewModel(application,
            GameState(active = Piece(Tetromino.O, y = 17), next = Tetromino.T), { now }, false, recorder)
        model.pointerDown(50f, 50f, now)
        now += 600
        model.advanceFrame(now)
        assertTrue(model.game!!.accelerated)
        model.pointerUp(50f, 50f, now)
        assertTrue(recorder.calls.isEmpty())
        now += 800
        model.advanceFrame(now)
        assertEquals(listOf(Triple(FeedbackEvent.DROP, true, true)), recorder.calls)
        assertFalse(model.game!!.accelerated)
        model.newGame()
        assertFalse(model.game!!.accelerated)
    }

    /** Запоминает параметры эффектов и остановки для проверки настроек и паузы. */
    private class Recorder : GameFeedback {
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
        override fun resumeClear(remainingMillis: Long, vibration: Boolean) { if (vibration) remaining += remainingMillis }
        /** Сохраняет одно событие вместе с независимыми разрешениями. */
        override fun play(event: FeedbackEvent, sound: Boolean, vibration: Boolean) { calls += Triple(event, sound, vibration) }
        /** Учитывает остановку при переходах между экранами. */
        override fun stop() { stops++ }
        /** Не владеет устройствами и не требует освобождения. */
        override fun release() = Unit
    }

    /** Включение даёт один импульс; повтор значения и загрузка настройки не вибрируют. */
    @Test fun togglePreviewAndIndependentStops() {
        val recorder = Recorder()
        val model = GameViewModel(ApplicationProvider.getApplicationContext(), null, { 1000L }, false, recorder)
        assertEquals(0, recorder.previews)
        model.setVibration(false)
        model.setVibration(true)
        model.setVibration(true)
        assertEquals(1, recorder.previews)
        assertEquals(1, recorder.vibrationStops)
        assertEquals(0, recorder.soundStops)
        assertEquals(0, recorder.stops)
        model.setSound(false)
        assertEquals(1, recorder.soundStops)
        assertEquals(1, recorder.vibrationStops)
        assertEquals(0, recorder.stops)
    }

    /** Очистка звучит один раз, настройки независимы, продолжение не повторяет эффект. */
    @Test fun clearIsSingleAndResumeDoesNotReplay() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val board = List(20) { y -> List<Tetromino?>(10) { x -> if (y == 19 && x !in 4..5) Tetromino.J else null } }
        val initial = GameState(board = board, active = Piece(Tetromino.O, y = 18), next = Tetromino.T)
        var now = 1000L
        val recorder = Recorder()
        val model = GameViewModel(application, initial, { now }, false, recorder)
        val sound = model.soundEnabled
        val vibration = model.vibrationEnabled
        try {
            model.setSound(false)
            model.setVibration(true)
            model.command(GameCommand.HARD_DROP)
            assertEquals(listOf(Triple(FeedbackEvent.CLEAR, false, true)), recorder.calls)
            model.command(GameCommand.HARD_DROP)
            now += 100
            model.advanceFrame(now)
            model.pause()
            assertTrue(recorder.stops > 0)
            model.resume()
            assertEquals(listOf(500L), recorder.remaining)
            now += 500
            model.advanceFrame(now)
            assertEquals(1, recorder.calls.size)
            model.setSound(true)
            model.setVibration(false)
            model.command(GameCommand.HARD_DROP)
            assertEquals(Triple(FeedbackEvent.DROP, true, false), recorder.calls.last())
            model.menu()
            val count = recorder.calls.size
            model.command(GameCommand.HARD_DROP)
            assertEquals(count, recorder.calls.size)
        } finally {
            model.setSound(sound)
            model.setVibration(vibration)
        }
    }
}



