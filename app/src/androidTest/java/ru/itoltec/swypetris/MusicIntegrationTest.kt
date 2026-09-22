package ru.itoltec.swypetris

import android.app.Application
import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Проверяет музыкальные режимы рекорда отдельно от звуковых эффектов и реального динамика. */
class MusicIntegrationTest {
    @get:Rule val storage = IsolatedStorageRule()

    /** Записывает запросы проигрывателю без получения аудиофокуса устройства. */
    private class Recorder : MusicPlayback {
        val modes = mutableListOf<MusicMode>()
        /** Преобразует разрешение игровой музыки в режим. */
        override fun setPlaying(enabled: Boolean) = setMode(if (enabled) MusicMode.GAME else MusicMode.SILENT)
        /** Запоминает запрошенный режим для проверки однократного запуска. */
        override fun setMode(next: MusicMode) { modes += next }
        /** Тестовый проигрыватель не владеет ресурсами. */
        override fun release() = Unit
    }

    /** Музыка управляет фанфарами независимо от звука; фон и возврат останавливают их. */
    @Test fun recordFanfareUsesMusicSettingAndDoesNotRestart() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        for (musicEnabled in listOf(false, true)) for (soundEnabled in listOf(false, true)) {
            val preferences = GameStorage.preferences(application)
            preferences.edit().putInt("record_v4", 0).putBoolean("music", musicEnabled)
                .putBoolean("sound", soundEnabled).commit()
            val board = List(20) { MutableList<Tetromino?>(10) { null } }
            board[0][4] = Tetromino.Z
            val recorder = Recorder()
            val model = GameViewModel(application,
                GameState(board = board, active = Piece(Tetromino.O, x = 0, y = 18), next = Tetromino.O, score = 100),
                { 1000L }, false, musicPlayback = recorder)
            model.command(GameCommand.TICK)
            assertEquals(GameScreen.RECORD, model.screen)
            assertEquals(if (musicEnabled) 1 else 0, recorder.modes.count { it == MusicMode.RECORD })
            repeat(3) { model.advanceFrame(1000L); model.command(GameCommand.TICK) }
            assertEquals(if (musicEnabled) 1 else 0, recorder.modes.count { it == MusicMode.RECORD })
            model.pause()
            assertEquals(MusicMode.SILENT, recorder.modes.last())
            model.resume() // Завершённая партия не должна снова запускать фанфары.
            assertEquals(MusicMode.SILENT, recorder.modes.last())
            model.saveRecordName("Тест")
            assertEquals(MusicMode.SILENT, recorder.modes.last())
            model.back()
            assertEquals(GameScreen.MENU, model.screen)
        }
    }

    /** Проверяет, что упакованный ресурс фанфар декодируется Android и имеет длительность 5 секунд. */
    @Test fun packagedFanfareDecodes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val application = ApplicationProvider.getApplicationContext<Application>()
            val player = MediaPlayer.create(application, R.raw.record_fanfare)
            assertNotNull(player)
            try { assertTrue(player.duration in 4900..5100) } finally { player.release() }
        }
    }

    /** Все восемь полных записей декодируются штатным проигрывателем Android. */
    @Test fun packagedPlaylistDecodes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val application = ApplicationProvider.getApplicationContext<Application>()
            val tracks = listOf(R.raw.korobeiniki to 180600, R.raw.kalinka to 168200,
                R.raw.kamarinskaya to 166917, R.raw.barynya to 174733,
                R.raw.svetit_mesyat to 171723, R.raw.vo_sadu to 175945,
                R.raw.trepak to 71800, R.raw.sugar_plum to 113800)
            for ((resource, duration) in tracks) {
                val player = MediaPlayer.create(application, resource)
                assertNotNull(player)
                try { assertTrue("Ресурс $resource: ${player.duration}", kotlin.math.abs(player.duration - duration) < 200) }
                finally { player.release() }
            }
        }
    }
}
