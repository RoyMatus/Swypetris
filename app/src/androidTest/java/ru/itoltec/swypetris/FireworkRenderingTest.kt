package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import androidx.lifecycle.ViewModelProvider
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/** Deterministic software-rendered frames; baseline artifacts stay outside source control. */
class FireworkRenderingTest {
    @get:Rule val storage = IsolatedStorageRule()
    private data class Fixture(val name: String, val width: Int, val height: Int, val density: Float,
        val celebration: VictoryCelebration)

    private val fixtures = listOf(
        Fixture("round-small", 360, 800, 1f, VictoryCelebration.ROUND),
        Fixture("round-large", 1080, 1920, 3f, VictoryCelebration.ROUND),
        Fixture("absolute-small", 360, 800, 1f, VictoryCelebration.ABSOLUTE),
        Fixture("absolute-large", 1080, 1920, 3f, VictoryCelebration.ABSOLUTE),
        Fixture("postcard", 1080, 1440, 3f, VictoryCelebration.POSTCARD)
    )
    private val times = listOf(0L, 300L, 755L, 756L, 757L, 1405L, 1406L, 1407L,
        2055L, 2056L, 2057L, 2400L, 4000L, 4199L, 4200L)

    @Test fun exportsDeterministicBoundaryFrames() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Frame export is an opt-in audit", args.containsKey("fireworkPhase"))
        val phase = args.getString("fireworkPhase", "current")
        require(phase.matches(Regex("[a-zA-Z0-9_-]+")))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.getExternalFilesDir(null), "firework-audit/$phase")
        assertTrue(directory.isDirectory || directory.mkdirs())
        val manifest = mutableListOf("frame\twidth\theight\targb_sha256")
        fixtures.forEach { fixture ->
            val bitmap = Bitmap.createBitmap(fixture.width, fixture.height, Bitmap.Config.ARGB_8888)
            try {
                var painted = false
                times.forEach { elapsed ->
                    render(bitmap, fixture, elapsed)
                    val pixels = IntArray(fixture.width * fixture.height)
                    bitmap.getPixels(pixels, 0, fixture.width, 0, 0, fixture.width, fixture.height)
                    painted = painted || pixels.any { it ushr 24 != 0 }
                    val bytes = ByteBuffer.allocate(pixels.size * Int.SIZE_BYTES)
                    pixels.forEach(bytes::putInt)
                    val digest = MessageDigest.getInstance("SHA-256").digest(bytes.array())
                        .joinToString("") { "%02x".format(it) }
                    val name = "${fixture.name}-$elapsed.png"
                    File(directory, name).outputStream().use {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    manifest += "$name\t${fixture.width}\t${fixture.height}\t$digest"
                }
                assertTrue("Empty celebration: ${fixture.name}", painted)
                if (args.getString("fireworkBenchmark") == "true") benchmark(bitmap, fixture)
            } finally { bitmap.recycle() }
        }
        File(directory, "manifest.tsv").writeText(manifest.joinToString("\n", postfix = "\n"))
        Log.i("FireworkAudit", "Exported ${manifest.size - 1} frames to $directory")
        if (args.getString("victoryCapture") == "true") RealVictoryCapture.export(app, phase)
    }

    private fun render(bitmap: Bitmap, fixture: Fixture, elapsed: Long) {
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        val centers = if (fixture.celebration == VictoryCelebration.POSTCARD) listOf(
            Offset(.15f * fixture.width, .51f * fixture.height),
            Offset(.84f * fixture.width, .51f * fixture.height),
            Offset(.85f * fixture.width, .69f * fixture.height)) else null
        CanvasDrawScope().draw(Density(fixture.density), LayoutDirection.Ltr,
            Canvas(android.graphics.Canvas(bitmap)), Size(fixture.width.toFloat(), fixture.height.toFloat())) {
            drawVictoryFireworks(elapsed, fixture.celebration, centers)
        }
    }

    /** Includes bitmap clearing and software drawing; it is not a GPU/frame-presentation benchmark. */
    private fun benchmark(bitmap: Bitmap, fixture: Fixture) {
        repeat(30) { render(bitmap, fixture, 2400L) }
        repeat(5) { sample ->
            val allocated = runtimeCounter("art.gc.bytes-allocated")
            val collections = runtimeCounter("art.gc.gc-count")
            val start = SystemClock.elapsedRealtimeNanos()
            repeat(60) { render(bitmap, fixture, 2400L) }
            val elapsed = SystemClock.elapsedRealtimeNanos() - start
            val bytes = counterDifference(allocated, runtimeCounter("art.gc.bytes-allocated"))
            val gc = counterDifference(collections, runtimeCounter("art.gc.gc-count"))
            Log.i("FireworkAudit", "${fixture.name} sample=$sample frames=60 totalNanos=$elapsed " +
                "allocatedBytes=$bytes gcCount=$gc")
        }
    }

    /** Approximate process-wide ART counters, including the harness; never renderer-only allocation counts. */
    private fun runtimeCounter(name: String): Long? = android.os.Debug.getRuntimeStat(name)?.toLongOrNull()

    private fun counterDifference(before: Long?, after: Long?): String =
        if (before == null || after == null) "unknown" else (after - before).toString()
}

/** Opt-in evidence from the actual Activity and its production animation clock, without a Compose test clock. */
private object RealVictoryCapture {
    private const val RECORD_SECONDS = 12
    private const val OBSERVE_MILLIS = 12200L
    private const val SAMPLE_MILLIS = 32L
    private const val READY_MILLIS = 5000L
    private const val MICROS_PER_MILLI = 1000L
    private val frameFractions = listOf(0f, .1f, .25f, .45f, .65f, .85f, .98f)

    fun export(application: Application, phase: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(application.getExternalFilesDir(null), "victory-motion/$phase")
        for (laterRound in listOf(false, true)) {
            seed(application, laterRound)
            // startActivitySync/ActivityScenario waits for idle, which a live animation need not reach.
            application.startActivity(android.content.Intent(application, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            val activity = awaitScene(instrumentation)
            try {
                val directory = File(root, if (laterRound) "later-round" else "round")
                check(directory.isDirectory || directory.mkdirs())
                record(instrumentation, activity, directory)
            } finally {
                instrumentation.runOnMainSync { activity.finish() }
                val deadline = SystemClock.elapsedRealtime() + READY_MILLIS
                var destroyed = false
                while (!destroyed && SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync { destroyed = activity.isDestroyed }
                    if (!destroyed) SystemClock.sleep(SAMPLE_MILLIS)
                }
                check(destroyed) { "Recorded Activity did not finish" }
            }
        }
        val postcard = AbsolutePostcard.create(application, "Roy", 1_040_000)
        try {
            File(root, "postcard.png").outputStream().use {
                check(postcard.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { postcard.recycle() }
    }

    private fun seed(application: Application, absolute: Boolean) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = GameViewModel(application, GameState(active = Piece(Tetromino.O), next = Tetromino.T,
                score = if (absolute) 1_039_999L else 79999, completedRounds = if (absolute) 12L else 0L),
                { 1000L }, false)
            model.options.setSound(false)
            model.options.setVibration(false)
            model.options.chooseMusic(MusicSelection.Off)
            model.input.command(GameCommand.SOFT_DROP)
            model.onBackground()
        }
    }

    private fun awaitScene(instrumentation: android.app.Instrumentation): MainActivity {
        val deadline = SystemClock.elapsedRealtime() + READY_MILLIS
        var ready: MainActivity? = null
        while (ready == null && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().firstOrNull()
                if (activity?.hasWindowFocus() == true) {
                    val model = ViewModelProvider(activity)[GameViewModel::class.java]
                    model.navigation.finishLaunchIntro()
                    model.resume()
                    if (model.screen == GameScreen.VICTORY)
                        ready = activity
                }
            }
            if (ready == null) SystemClock.sleep(SAMPLE_MILLIS)
        }
        return checkNotNull(ready) { "Real victory scene did not become ready" }
    }

    private fun record(instrumentation: android.app.Instrumentation, activity: MainActivity,
        directory: File) {
        graphicsStats(instrumentation, File(directory, "gfx-reset.txt"), reset = true)
        val recording = instrumentation.uiAutomation.executeShellCommand(
            "screenrecord --time-limit $RECORD_SECONDS ${File(directory, "motion.mp4").absolutePath}")
        val timestamps = mutableListOf("wallMillis\tphaseMillis")
        val wraps = mutableListOf<Long>()
        val started = SystemClock.elapsedRealtime()
        var previous = 0L
        try {
            while (SystemClock.elapsedRealtime() - started < OBSERVE_MILLIS) {
                var phase = 0L
                instrumentation.runOnMainSync {
                    phase = ViewModelProvider(activity)[GameViewModel::class.java].victoryAnimationMillis
                }
                val elapsed = SystemClock.elapsedRealtime() - started
                if (phase < previous) wraps += elapsed
                previous = phase
                timestamps += "$elapsed\t$phase"
                SystemClock.sleep(SAMPLE_MILLIS)
            }
        } finally { recording.close() }
        File(directory, "timestamps.tsv").writeText(timestamps.joinToString("\n", postfix = "\n"))
        graphicsStats(instrumentation, File(directory, "gfx-frames.txt"), reset = false)
        val duration = decodeRecording(instrumentation, directory)
        val startup = (OBSERVE_MILLIS - duration).coerceAtLeast(0)
        check(wraps.zipWithNext().any { (first, second) -> first > startup && second < duration }) {
            "Recording must contain a complete observed loop: duration=$duration, wraps=$wraps"
        }
    }

    private fun decodeRecording(instrumentation: android.app.Instrumentation, directory: File): Long {
        // screenrecord's shell-owned 0600 output is copied privately, without changing permissions.
        val readable = File(directory, "motion-readable.mp4")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "cat ${File(directory, "motion.mp4").absolutePath}")).use { input ->
            readable.outputStream().use { output -> input.copyTo(output) }
        }
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(readable.absolutePath)
            val duration = checkNotNull(retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
            check(duration >= VictoryMotion.PERIOD_MILLIS * 2) { "Incomplete screen recording: $duration" }
            frameFractions.forEach { fraction -> saveVideoFrame(retriever, directory, (duration * fraction).toLong()) }
            File(directory, "video-metadata.txt").writeText("durationMillis=$duration\n")
            return duration
        } finally { retriever.release() }
    }

    private fun saveVideoFrame(retriever: android.media.MediaMetadataRetriever, directory: File, time: Long) {
        val bitmap = checkNotNull(retriever.getFrameAtTime(time * MICROS_PER_MILLI,
            android.media.MediaMetadataRetriever.OPTION_CLOSEST))
        try {
            check(bitmap.height > bitmap.width) { "Recording must remain portrait" }
            File(directory, "video-$time.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }

    private fun graphicsStats(instrumentation: android.app.Instrumentation, file: File, reset: Boolean) {
        val operation = if (reset) "reset" else "framestats"
        val output = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("dumpsys gfxinfo ru.itoltec.swypetris $operation"))
            .bufferedReader().use { it.readText() }
        file.writeText(output)
    }
}
