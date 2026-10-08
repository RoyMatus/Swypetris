package ru.itoltec.swypetris

import android.app.Application
import android.media.MediaPlayer
import android.media.AudioTrack
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Проверяет музыкальные режимы рекорда отдельно от звуковых эффектов и реального динамика. */
class MusicIntegrationTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()

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
            preferences.edit().clear().putInt("record_v4", 0).putBoolean("music", musicEnabled)
                .putBoolean("sound", soundEnabled).commit()
            val board = List(BoardGeometry.TOTAL_ROWS) { MutableList<Tetromino?>(10) { null } }
            board[BoardGeometry.row(0)][4] = Tetromino.Z
            val recorder = Recorder()
            val model = GameViewModel(application,
                GameState(board = board, active = Piece(Tetromino.O, x = 0, y = 18), next = Tetromino.O, score = 100),
                { 1000L }, false, musicPlayback = recorder)
            model.input.command(GameCommand.HARD_DROP)
            assertEquals(GameScreen.RECORD, model.screen)
            assertEquals(if (musicEnabled) 1 else 0, recorder.modes.count { it == MusicMode.RECORD })
            repeat(3) { model.simulation.advanceFrame(1000L); model.input.command(GameCommand.HARD_DROP) }
            assertEquals(if (musicEnabled) 1 else 0, recorder.modes.count { it == MusicMode.RECORD })
            model.pause()
            assertEquals(MusicMode.SILENT, recorder.modes.last())
            model.resume() // Завершённая партия не должна снова запускать фанфары.
            assertEquals(MusicMode.SILENT, recorder.modes.last())
            model.statistics.saveRecordName("Тест")
            assertEquals(MusicMode.SILENT, recorder.modes.last())
            model.navigation.back()
            assertEquals(GameScreen.MENU, model.screen)
        }
    }

    /** Меню использует отдельный режим, а потеря фокуса и выключение музыки сразу дают тишину. */
    @Test fun menuMusicFollowsNavigationAndLifecycle() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val preferences = GameStorage.preferences(application)
        preferences.edit().clear().putBoolean("music", true).commit()
        val recorder = Recorder()
        val model = GameViewModel(application, null, { 1000L }, false, musicPlayback = recorder)

        model.onForeground()
        assertEquals(MusicMode.MENU, recorder.modes.last())

        model.navigation.settings()
        assertEquals(MusicMode.MENU, recorder.modes.last())

        model.navigation.help()
        assertEquals(GameScreen.HELP, model.screen)
        assertEquals(MusicMode.MENU, recorder.modes.last())

        model.onWindowFocusChanged(false)
        assertEquals(MusicMode.SILENT, recorder.modes.last())
        model.onWindowFocusChanged(true)
        assertEquals(MusicMode.MENU, recorder.modes.last())

        model.options.chooseMusic(MusicSelection.Off)
        assertEquals(MusicMode.SILENT, recorder.modes.last())
        for (navigate in listOf(model.navigation::settings, model.navigation::help, model.navigation::contacts,
            model.navigation::privacy,
            model.navigation::legal, model.navigation::showResults, model.navigation::menu)) {
            navigate()
            assertEquals(MusicMode.SILENT, recorder.modes.last())
        }
        model.newGame()
        assertEquals(MusicMode.SILENT, recorder.modes.last())
        val restartedRecorder = Recorder()
        val restarted = GameViewModel(application, null, { 1000L }, false, musicPlayback = restartedRecorder)
        restarted.onForeground()
        assertEquals(MusicMode.SILENT, restartedRecorder.modes.last())
        model.navigation.menu()
        model.options.chooseMusic(MusicSelection.Track(Song.KOROBEINIKI))
        assertEquals(MusicMode.MENU, recorder.modes.last())
    }

    @Test fun packagedMenuLoopRetainsPreparedSamplesAndLoudness() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val wave = app.resources.openRawResource(R.raw.menu_melody_loop).use { it.readBytes() }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(wave)
            .joinToString("") { "%02x".format(it) }
        assertEquals("2ac09325859bba039003f69737e7a882d883936a6481c29940cd9aa3a46ca56f", hash)
        val samples = MenuTheme.decode(wave)
        assertEquals(MenuTheme.FRAME_COUNT * MenuTheme.CHANNELS, samples.size)
        assertEquals(1f, MenuTheme.GAIN, 0f)
        assertEquals(.9030584f, samples.maxOf { kotlin.math.abs(it) }, .000001f)
        val rms = kotlin.math.sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
        assertEquals(-16.892328, 20 * kotlin.math.log10(rms), .0001)
        assertThrows(IllegalArgumentException::class.java) { MenuTheme.decode(wave.copyOf(50)) }
        val wrongFormat = wave.copyOf().apply { this[34] = 16 }
        assertThrows(IllegalArgumentException::class.java) { MenuTheme.decode(wrongFormat) }
    }

    @Test fun realMenuPlayerRepeatsThreeTimesAndKeepsPositionAcrossNavigation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        lateinit var music: GameMusic
        lateinit var model: GameViewModel
        lateinit var track: AudioTrack
        compose.runOnIdle {
            music = GameMusic(app)
            model = GameViewModel(app, null, android.os.SystemClock::uptimeMillis, false, musicPlayback = music)
            model.onForeground()
            track = menuTrack(music)
            assertEquals(MenuTheme.SAMPLE_RATE, track.sampleRate)
            assertEquals(2, track.channelCount)
        }
        try {
            val started = android.os.SystemClock.uptimeMillis()
            var previous = 0L
            val navigation = listOf(model.navigation::settings, model.navigation::help, model.navigation::contacts,
                model.navigation::privacy,
                model.navigation::legal, model.navigation::showResults, model.navigation::menu)
            var screen = 0
            while (previous < MenuTheme.FRAME_COUNT * 3L && android.os.SystemClock.uptimeMillis() - started < 105000) {
                compose.runOnIdle {
                    navigation[screen++ % navigation.size]()
                    assertSame(track, menuTrack(music))
                    assertEquals(AudioTrack.PLAYSTATE_PLAYING, track.playState)
                }
                Thread.sleep(250) // Sample actual audio progress; never retry a failed player.
                val position = track.playbackHeadPosition.toLong() and 0xffffffffL
                assertTrue("Navigation restarted the loop", position >= previous)
                previous = position
            }
            assertTrue("Three real repeats must complete", previous >= MenuTheme.FRAME_COUNT * 3L)
            android.util.Log.i("SwypetrisMusicTest", "Menu loop frames=$previous; " +
                "elapsed_ms=${android.os.SystemClock.uptimeMillis() - started}; " +
                "gain=1.0")
            compose.runOnIdle {
                model.onBackground()
                assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
                model.onForeground()
                assertSame(track, menuTrack(music))
                assertEquals(AudioTrack.PLAYSTATE_PLAYING, track.playState)
                model.options.chooseMusic(MusicSelection.Off)
                assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
                for (mode in MusicMode.entries) music.setMode(mode)
                assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
                assertEquals(MusicMode.SILENT, field(music, "mode"))
                assertNull(field(music, "gamePlayer"))
                assertNull(field(music, "recordPlayer"))
                val restored = GameViewModel(app, null, { 1000L }, false)
                assertEquals(MusicSelection.Off, restored.musicSelection)
            }
        } finally { compose.runOnIdle { music.release() } }
    }

    @Test fun focusAndHeadphoneGatesPauseAllMusicSources() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        compose.runOnIdle {
            val music = GameMusic(app)
            try {
                music.setMode(MusicMode.MENU)
                val track = menuTrack(music)
                val listener = field(music, "listener") as AudioManager.OnAudioFocusChangeListener
                listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
                listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
                assertEquals(AudioTrack.PLAYSTATE_PLAYING, track.playState)
                val receiver = field(music, "noisyReceiver") as android.content.BroadcastReceiver
                receiver.onReceive(app, android.content.Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
                listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
                assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
                music.setMode(MusicMode.SILENT)
                music.setMode(MusicMode.RECORD)
                assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
                music.select(MusicSelection.Off)
                assertFalse((field(music, "recordPlayer") as MediaPlayer).isPlaying)
                listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
                assertEquals(AudioTrack.PLAYSTATE_PAUSED, track.playState)
            } finally { music.release() }
        }
    }

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)

    private fun menuTrack(music: GameMusic): AudioTrack = field(field(music, "menuPlayer")!!, "track") as AudioTrack

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
                try { assertTrue("Ресурс $resource: ${player.duration}",
                    kotlin.math.abs(player.duration - duration) < 200) }
                finally { player.release() }
            }
        }
    }
}
