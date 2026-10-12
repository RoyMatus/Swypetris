package ru.itoltec.swypetris

import android.app.Application
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Optional wall-clock rendering evidence, isolated from the Compose virtual test clock. */
@SdkSuppress(minSdkVersion = 26)
class MaterialFrameEvidenceTest {
    @get:Rule val storage = IsolatedStorageRule()

    @Test fun realActivityFrames() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("materialFrames") == "true")
        MaterialActivityFrames.recordIfRequested()
    }
}
/** Opt-in real Activity frame timings; no Compose test clock or frame-time assertion. */
private object MaterialActivityFrames {
    fun recordIfRequested() {
        if (InstrumentationRegistry.getArguments().getString("materialFrames") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.getExternalFilesDir(null), "materials-189").apply { mkdirs() }
        app.startActivity(android.content.Intent(app, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val activity = awaitActivity(instrumentation)
        try {
            for (palette in GamePalettes.all) {
                instrumentation.runOnMainSync {
                    val model = androidx.lifecycle.ViewModelProvider(activity)[GameViewModel::class.java]
                    model.navigation.finishLaunchIntro()
                    model.options.chooseMusic(MusicSelection.Off)
                    model.options.setSound(false)
                    model.options.setVibration(false)
                    model.newGame()
                    model.options.setPalette(palette.id)
                    replaceFixture(model)
                    check(model.screen == GameScreen.PLAYING)
                }
                SystemClock.sleep(500)
                graphics(instrumentation, File(directory, "${palette.id}-gfx-reset.txt"), "reset")
                val started = SystemClock.elapsedRealtime()
                var sample = 0
                while (SystemClock.elapsedRealtime() - started < 3000) {
                    instrumentation.runOnMainSync {
                        val model = androidx.lifecycle.ViewModelProvider(activity)[GameViewModel::class.java]
                        model.input.command(if (sample % 2 == 0) GameCommand.LEFT else GameCommand.RIGHT)
                    }
                    sample++
                    SystemClock.sleep(32)
                }
                graphics(instrumentation, File(directory, "${palette.id}-gfx-frames.txt"), "framestats")
                val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                File(directory, "${palette.id}-real-activity.png").outputStream().use {
                    check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
                screenshot.recycle()
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun awaitActivity(instrumentation: android.app.Instrumentation): MainActivity {
        val deadline = SystemClock.elapsedRealtime() + 10000
        var ready: MainActivity? = null
        while (ready == null && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync {
                val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<MainActivity>().firstOrNull()
                if (activity?.hasWindowFocus() == true) ready = activity
            }
            if (ready == null) SystemClock.sleep(32)
        }
        return checkNotNull(ready) { "Real gameplay Activity did not become ready" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun replaceFixture(model: GameViewModel) {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        for (row in 10..19) for (column in 0..8) {
            board[BoardGeometry.row(row)][column] = Tetromino.entries[(row + column) % 7]
        }
        val field = GameViewModel::class.java.getDeclaredField("game\$delegate").apply { isAccessible = true }
        val state = field.get(model) as androidx.compose.runtime.MutableState<GameState?>
        state.value = requireNotNull(model.game).copy(board = board, held = Tetromino.L, score = 254430)
    }

    private fun graphics(instrumentation: android.app.Instrumentation, file: File, operation: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "dumpsys gfxinfo ru.itoltec.swypetris $operation")).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
    }
}
