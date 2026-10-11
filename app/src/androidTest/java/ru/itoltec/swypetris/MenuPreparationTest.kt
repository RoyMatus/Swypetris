package ru.itoltec.swypetris

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Controlled factory completion exercises races without depending on decoder speed or sound output. */
class MenuPreparationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun preparationLeavesMainResponsiveAndRemainsSingleFlight() {
        withPreparation { fixture ->
            compose.runOnIdle {
                repeat(3) {
                    fixture.music.setMode(MusicMode.MENU)
                    fixture.focus(AudioManager.AUDIOFOCUS_GAIN)
                }
                assertNull(field(fixture.music, "menuPlayer"))
                assertEquals(1, fixture.attempts.get())
            }
            fixture.complete()
            compose.runOnIdle {
                assertSame(fixture.player, field(fixture.music, "menuPlayer"))
                assertTrue(fixture.player.playing)
                fixture.music.setMode(MusicMode.SILENT)
                assertFalse(fixture.player.playing)
                fixture.music.setMode(MusicMode.MENU)
                assertTrue(fixture.player.playing)
                assertEquals(1, fixture.attempts.get())
            }
        }
    }

    @Test fun completionRespectsModeAndSelectionChangesAndRetainsPreparedPlayer() {
        for (mode in listOf(MusicMode.SILENT, MusicMode.GAME, MusicMode.RECORD)) {
            withPreparation { fixture ->
                compose.runOnIdle { fixture.music.setMode(mode) }
                fixture.complete()
                compose.runOnIdle {
                    assertEquals(0, fixture.player.plays)
                    fixture.music.setMode(MusicMode.MENU)
                    assertTrue(fixture.player.playing)
                    assertEquals(1, fixture.attempts.get())
                }
            }
        }
        withPreparation { fixture ->
            compose.runOnIdle { fixture.music.select(MusicSelection.Off) }
            fixture.complete()
            compose.runOnIdle {
                fixture.focus(AudioManager.AUDIOFOCUS_GAIN)
                assertEquals(0, fixture.player.plays)
                fixture.music.select(MusicSelection.ShuffleAll)
                fixture.music.setMode(MusicMode.MENU)
                assertTrue(fixture.player.playing)
                assertEquals(1, fixture.attempts.get())
            }
        }
    }

    @Test fun completionRespectsFocusLossAndHeadphoneGate() {
        withPreparation { fixture ->
            compose.runOnIdle { fixture.focus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) }
            fixture.complete()
            compose.runOnIdle {
                assertEquals(0, fixture.player.plays)
                fixture.focus(AudioManager.AUDIOFOCUS_GAIN)
                assertTrue(fixture.player.playing)
            }
        }
        withPreparation { fixture ->
            compose.runOnIdle {
                val receiver = field(fixture.music, "noisyReceiver") as BroadcastReceiver
                receiver.onReceive(app, Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            }
            fixture.complete()
            compose.runOnIdle {
                fixture.focus(AudioManager.AUDIOFOCUS_GAIN)
                assertEquals(0, fixture.player.plays)
                fixture.music.setMode(MusicMode.SILENT)
                fixture.music.setMode(MusicMode.MENU)
                assertTrue(fixture.player.playing)
            }
        }
    }

    @Test fun releaseDuringPreparationClosesLateResultWithoutAdoptingOrPlaying() {
        withPreparation { fixture ->
            compose.runOnIdle { fixture.music.release() }
            fixture.complete()
            compose.runOnIdle {
                assertNull(field(fixture.music, "menuPlayer"))
                assertEquals(0, fixture.player.plays)
                assertEquals(1, fixture.player.releases)
                fixture.music.setMode(MusicMode.MENU)
                assertEquals(1, fixture.attempts.get())
            }
        }
    }

    @Test fun releaseClosesAdoptedPlayerExactlyOnce() {
        withPreparation { fixture ->
            fixture.complete()
            compose.runOnIdle {
                fixture.music.release()
                fixture.music.release()
                assertEquals(1, fixture.player.releases)
                assertFalse(fixture.player.playing)
            }
        }
    }

    @Test fun failureDoesNotRetryUntilExplicitMenuVisit() {
        withPreparation { fixture ->
            fixture.failure.set(IOException("Controlled preparation failure"))
            fixture.complete()
            compose.runOnIdle {
                assertNull(field(fixture.music, "menuPlayer"))
                repeat(3) {
                    fixture.focus(AudioManager.AUDIOFOCUS_GAIN)
                    fixture.music.setMode(MusicMode.MENU)
                }
                assertEquals(1, fixture.attempts.get())
                fixture.failure.set(null)
                fixture.music.setMode(MusicMode.SILENT)
                fixture.music.setMode(MusicMode.MENU)
            }
            awaitCompletion(fixture.music)
            compose.runOnIdle {
                assertEquals(2, fixture.attempts.get())
                assertTrue(fixture.player.playing)
            }
        }
    }

    private fun withPreparation(check: (Preparation) -> Unit) {
        val fixture = Preparation()
        try {
            compose.runOnIdle { fixture.music.setMode(MusicMode.MENU) }
            assertTrue("Factory did not start", fixture.entered.await(10, TimeUnit.SECONDS))
            check(fixture)
        } finally {
            compose.runOnIdle { fixture.music.release() }
            fixture.gate.countDown()
            awaitCompletion(fixture.music)
        }
    }

    private inner class Preparation {
        val player = RecordingMenu()
        val attempts = AtomicInteger()
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val failure = AtomicReference<IOException?>()
        val music: GameMusic = createMusic()

        private fun createMusic(): GameMusic {
            lateinit var created: GameMusic
            compose.runOnIdle {
                created = GameMusic(app, Dispatchers.IO) { _, _ ->
                    assertNotSame("Preparation ran on main", Looper.getMainLooper(), Looper.myLooper())
                    attempts.incrementAndGet()
                    entered.countDown()
                    check(gate.await(10, TimeUnit.SECONDS)) { "Factory gate was not released" }
                    failure.get()?.let { throw it }
                    player
                }
            }
            return created
        }

        fun complete() {
            gate.countDown()
            awaitCompletion(music)
        }

        fun focus(change: Int) =
            (field(music, "listener") as AudioManager.OnAudioFocusChangeListener).onAudioFocusChange(change)
    }

    private class RecordingMenu : MenuPlayback {
        var playing = false
        var plays = 0
        var releases = 0
        override fun play() { assertMain(); plays++; playing = true }
        override fun pause() { assertMain(); playing = false }
        override fun release() { assertMain(); releases++; playing = false }
        private fun assertMain() = assertSame(Looper.getMainLooper(), Looper.myLooper())
    }

    private fun awaitCompletion(music: GameMusic) {
        compose.waitUntil(timeoutMillis = 10000) {
            var complete = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                complete = field(music, "menuPreparing") == false
            }
            complete
        }
    }

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)
}
