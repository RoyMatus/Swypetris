package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises selection through the model and asynchronous playback through the native SoundPool. */
class ThemeSoundIntegrationTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun packagedPairsMatchApprovedBytesAndStablePaletteNames() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val expected = InstrumentationRegistry.getInstrumentation().context.assets
            .open("theme-sounds-sha256.csv").bufferedReader().useLines { lines ->
                lines.associate { line -> line.split(',').let { it[0] to it[1] } }
            }
        val seen = mutableSetOf<String>()
        for (palette in GamePalettes.all) {
            val pair = ThemeSounds.forPalette(palette.id)
            for ((event, resource) in listOf("drop" to pair.drop, "clear" to pair.clear)) {
                val name = if (palette.id == "classic") "${event}_soft" else "${palette.id}_$event"
                assertEquals(name, app.resources.getResourceEntryName(resource))
                val bytes = app.resources.openRawResource(resource).use { it.readBytes() }
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                assertEquals(name, expected.getValue(name), hash)
                seen += name
            }
        }
        assertEquals(expected.keys, seen)
    }

    @Test fun manualRandomAndRestoredThemesReachCommandAndTimedEvents() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val recorder = Recorder()
        var now = 1000L
        val model = GameViewModel(app, null, { now }, false, recorder,
            paletteRandom = kotlin.random.Random(183))
        for (palette in GamePalettes.all) {
            model.options.setPalette(palette.id)
            model.newGame()
            model.input.command(GameCommand.HARD_DROP)
            assertEquals(palette.id to FeedbackEvent.DROP, recorder.events.last())
            model.pause()
            val restoredRecorder = Recorder()
            val restored = GameViewModel(app, null, { now }, false, restoredRecorder)
            assertEquals(palette.id, restoredRecorder.palette)
            restored.resume()
            assertTrue(restoredRecorder.events.isEmpty())
            restored.input.command(GameCommand.HARD_DROP)
            assertEquals(palette.id to FeedbackEvent.DROP, restoredRecorder.events.single())
        }
        model.options.setRandomTheme(true)
        val previous = model.paletteId
        model.newGame()
        assertFalse(previous == model.paletteId)
        model.input.command(GameCommand.HARD_DROP)
        assertEquals(model.paletteId to FeedbackEvent.DROP, recorder.events.last())
        val timed = GameViewModel(app, GameState(active = Piece(Tetromino.O, y = 17), next = Tetromino.T),
            { now }, false, recorder)
        timed.input.pointerDown(50f, 50f, now)
        now += 100
        timed.input.pointerUp(50f, 50f, now)
        now += 800
        timed.simulation.advanceFrame(now)
        assertEquals(timed.paletteId to FeedbackEvent.DROP, recorder.events.last())
        timed.options.setPalette("unknown-palette")
        assertEquals("classic", recorder.palette)
        assertEquals(ThemeSounds.forPalette("classic"), ThemeSounds.forPalette("unknown-palette"))
    }

    @Test fun nativePoolCachesAllPairsStopsOldStreamsAndNeverReplaysPendingLoads() {
        val profile = DeliveryPhaseProfile("theme-native-pool")
        val app = ApplicationProvider.getApplicationContext<Application>()
        lateinit var feedback: AndroidGameFeedback
        profile.measure("setup") { compose.runOnUiThread { feedback = AndroidGameFeedback(app) } }
        val pool = field<Any>(feedback, "pool")
        try {
            for (palette in GamePalettes.all) {
                profile.measure("select-load") { compose.runOnUiThread {
                    feedback.setPalette(palette.id)
                    assertTrue(field<List<Int>>(feedback, "streams").isEmpty())
                    if (palette.id != "classic") {
                        feedback.play(FeedbackEvent.DROP, sound = true, vibration = false)
                        assertTrue(field<List<Int>>(feedback, "streams").isEmpty())
                    }
                } }
                profile.measure("load-wait") { compose.waitUntil(5000) {
                    val pair = field<Map<ThemeSoundPair, Pair<Int, Int>>>(feedback, "samples")
                        .getValue(ThemeSounds.forPalette(palette.id))
                    field<Set<Int>>(feedback, "loaded").containsAll(listOf(pair.first, pair.second))
                } }
                profile.measure("play-assertions") { compose.runOnUiThread {
                    assertTrue(field<List<Int>>(feedback, "streams").isEmpty())
                    for (event in FeedbackEvent.entries) {
                        feedback.play(event, sound = true, vibration = false)
                        assertEquals(1, field<List<Int>>(feedback, "streams").size)
                    }
                    feedback.stopSound()
                    assertTrue(field<List<Int>>(feedback, "streams").isEmpty())
                    feedback.play(FeedbackEvent.CLEAR, sound = false, vibration = false)
                    assertTrue(field<List<Int>>(feedback, "streams").isEmpty())
                } }
            }
            ThemeSoundAudition.recordIfRequested(feedback)
            compose.runOnUiThread {
                val cached = field<Map<ThemeSoundPair, Pair<Int, Int>>>(feedback, "samples").toMap()
                assertEquals(12, cached.size)
                for (palette in GamePalettes.all) feedback.setPalette(palette.id)
                feedback.setPalette("unknown-palette")
                assertEquals(cached, field<Map<ThemeSoundPair, Pair<Int, Int>>>(feedback, "samples"))
                assertSame(pool, field<Any>(feedback, "pool"))
                feedback.release()
                feedback.setPalette("nord")
                feedback.play(FeedbackEvent.DROP, sound = true, vibration = false)
                assertTrue(field<Map<*, *>>(feedback, "samples").isEmpty())
                assertTrue(field<Set<Int>>(feedback, "loaded").isEmpty())
                assertTrue(field<List<Int>>(feedback, "streams").isEmpty())
            }
        } finally {
            compose.runOnUiThread { feedback.release() }
            profile.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(feedback: AndroidGameFeedback, name: String): T =
        AndroidGameFeedback::class.java.getDeclaredField(name).apply { isAccessible = true }.get(feedback) as T

    private class Recorder : GameFeedback {
        var palette = ""
            private set
        val events = mutableListOf<Pair<String, FeedbackEvent>>()
        override fun setPalette(id: String) { palette = id }
        override fun play(event: FeedbackEvent, sound: Boolean, vibration: Boolean) { events += palette to event }
        override fun stop() = Unit
        override fun release() = Unit
    }
}
