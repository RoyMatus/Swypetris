package ru.itoltec.swypetris

import android.app.Application
import android.app.Instrumentation
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in actual Activity evidence; observed emulator counters are not a device FPS/leak assertion. */
@SdkSuppress(minSdkVersion = 26)
class MenuFrameEvidenceTest {
    @get:Rule val storage = IsolatedStorageRule()

    @Test fun realMenuFrames() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("menuFrames") == "true")
        MenuActivityFrames.record()
    }
}

private const val MENU_FRAME_SETTLE_MILLIS = 500L
private const val MENU_FRAME_SAMPLE_MILLIS = 3000L
private const val MENU_ACTIVITY_READY_MILLIS = 10000L
private const val MENU_ACTIVITY_POLL_MILLIS = 32L
private const val MENU_FRAME_ROUNDS = 2
private const val MENU_FRAME_MILLIS_PER_SECOND = 1000.0

@androidx.annotation.RequiresApi(26)
private object MenuActivityFrames {
    fun record() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.getExternalFilesDir(null), "menu-frames-307").apply { mkdirs() }
        app.startActivity(android.content.Intent(app, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val activity = awaitActivity(instrumentation)
        try {
            val context = prepare(instrumentation, activity, directory)
            recordEnvironment(app, directory)
            File(directory, "samples.csv").writeText("round,theme,settleSeconds,sampleSeconds," +
                "gfxResetToDumpRequestSeconds,gfxFile,memoryFile\n")
            dump(context, "baseline-memory.txt", "dumpsys meminfo ru.itoltec.swypetris")
            repeat(MENU_FRAME_ROUNDS) { round -> recordRound(context, round + 1) }
            verifyLifecycle(context)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun recordEnvironment(app: Application, directory: File) {
        val scale = Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val enabled = ValueAnimator.areAnimatorsEnabled() && scale > 0f
        File(directory, "environment.txt").writeText(
            "model=${Build.MODEL}\napi=${Build.VERSION.SDK_INT}\n" +
                "animatorDurationScale=$scale\nanimatorsEnabled=$enabled\n" +
                "Measurements contain emulator/process-wide overhead; no FPS threshold or leak verdict.\n")
        check(enabled) { "System animations are disabled; this opt-in workload requires enabled authored motion" }
    }

    private fun prepare(instrumentation: Instrumentation, activity: MainActivity, directory: File): FrameContext {
        lateinit var model: GameViewModel
        lateinit var game: GameState
        instrumentation.runOnMainSync {
            model = ViewModelProvider(activity)[GameViewModel::class.java]
            model.navigation.finishLaunchIntro()
            model.options.chooseMusic(MusicSelection.Off)
            model.options.setSound(false)
            model.options.setVibration(false)
            model.newGame()
            model.navigation.menu()
            game = requireNotNull(model.game)
            check(!game.gameOver)
        }
        return FrameContext(instrumentation, activity, model, game, directory)
    }

    private fun recordRound(context: FrameContext, round: Int) {
        GamePalettes.all.forEach { palette -> recordTheme(context, round, palette.id) }
        dump(context, "round$round-memory.txt", "dumpsys meminfo ru.itoltec.swypetris")
    }

    private fun recordTheme(context: FrameContext, round: Int, theme: String) {
        context.instrumentation.runOnMainSync {
            context.model.options.setPalette(theme)
            checkPreservedMenu(context, theme)
        }
        val settleStart = SystemClock.elapsedRealtime()
        SystemClock.sleep(MENU_FRAME_SETTLE_MILLIS)
        val settled = SystemClock.elapsedRealtime()
        val prefix = "round$round-$theme"
        dump(context, "$prefix-gfx-reset.txt", "dumpsys gfxinfo ru.itoltec.swypetris reset")
        val started = SystemClock.elapsedRealtime()
        SystemClock.sleep(MENU_FRAME_SAMPLE_MILLIS)
        val finished = SystemClock.elapsedRealtime()
        context.instrumentation.runOnMainSync { checkPreservedMenu(context, theme) }
        val dumpRequested = SystemClock.elapsedRealtime()
        dump(context, "$prefix-gfx-frames.txt", "dumpsys gfxinfo ru.itoltec.swypetris framestats")
        dump(context, "$prefix-memory.txt", "dumpsys meminfo ru.itoltec.swypetris")
        captureIfRequested(context, "$prefix-real-activity.png")
        File(context.directory, "samples.csv").appendText(listOf(round, theme,
            (settled - settleStart) / MENU_FRAME_MILLIS_PER_SECOND,
            (finished - started) / MENU_FRAME_MILLIS_PER_SECOND,
            (dumpRequested - started) / MENU_FRAME_MILLIS_PER_SECOND,
            "$prefix-gfx-frames.txt", "$prefix-memory.txt").joinToString(",") + "\n")
    }

    private fun verifyLifecycle(context: FrameContext) {
        val theme = GamePalettes.all.first().id
        context.instrumentation.runOnMainSync { context.model.options.setPalette(theme) }
        SystemClock.sleep(MENU_FRAME_SETTLE_MILLIS)
        dump(context, "return-first-theme-memory.txt", "dumpsys meminfo ru.itoltec.swypetris")
        context.instrumentation.runOnMainSync { check(context.activity.moveTaskToBack(true)) }
        awaitBackground(context)
        recordTransition(context, "background")
        context.instrumentation.runOnMainSync { checkPreservedMenu(context, theme) }
        context.activity.startActivity(android.content.Intent(context.activity, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitActivity(context.instrumentation)
        recordTransition(context, "resumed")
        context.instrumentation.runOnMainSync {
            checkPreservedMenu(context, theme)
            context.model.resume()
            check(context.model.screen == GameScreen.PLAYING)
            check(context.model.game === context.game)
            context.model.navigation.menu()
            checkPreservedMenu(context, theme)
        }
        SystemClock.sleep(MENU_FRAME_SETTLE_MILLIS)
        dump(context, "resumed-menu-memory.txt", "dumpsys meminfo ru.itoltec.swypetris")
        captureIfRequested(context, "resumed-menu-real-activity.png")
        File(context.directory, "lifecycle.txt").writeText(
            "Background/resume and immediate game/menu roundtrip preserved the same game object and theme=$theme.\n")
    }

    private fun awaitBackground(context: FrameContext) {
        val deadline = SystemClock.elapsedRealtime() + MENU_ACTIVITY_READY_MILLIS
        var background = false
        while (!background && SystemClock.elapsedRealtime() < deadline) {
            context.instrumentation.runOnMainSync {
                background = !context.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            }
            if (!background) SystemClock.sleep(MENU_ACTIVITY_POLL_MILLIS)
        }
        check(background) { "Measured Activity did not enter the background" }
    }

    private fun recordTransition(context: FrameContext, name: String) {
        dump(context, "$name-gfx-reset.txt", "dumpsys gfxinfo ru.itoltec.swypetris reset")
        val started = SystemClock.elapsedRealtime()
        SystemClock.sleep(MENU_FRAME_SETTLE_MILLIS)
        val dumpRequested = SystemClock.elapsedRealtime()
        dump(context, "$name-gfx-frames.txt", "dumpsys gfxinfo ru.itoltec.swypetris framestats")
        dump(context, "$name-memory.txt", "dumpsys meminfo ru.itoltec.swypetris")
        File(context.directory, "$name-window.txt").writeText(
            "gfxResetToDumpRequestSeconds=${(dumpRequested - started) / MENU_FRAME_MILLIS_PER_SECOND}\n")
    }

    private fun checkPreservedMenu(context: FrameContext, theme: String) {
        check(context.model.screen == GameScreen.MENU)
        check(context.model.paletteId == theme)
        check(context.model.game === context.game) { "Decorative sampling changed the paused game object" }
        check(!context.model.soundEnabled && !context.model.vibrationEnabled)
        check(!context.model.launchIntroPending)
    }

    private fun awaitActivity(instrumentation: Instrumentation): MainActivity {
        val deadline = SystemClock.elapsedRealtime() + MENU_ACTIVITY_READY_MILLIS
        var ready: MainActivity? = null
        while (ready == null && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().firstOrNull()
                if (activity?.hasWindowFocus() == true) ready = activity
            }
            if (ready == null) SystemClock.sleep(MENU_ACTIVITY_POLL_MILLIS)
        }
        return checkNotNull(ready) { "Real menu Activity did not become ready" }
    }

    private fun dump(context: FrameContext, name: String, command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(context.instrumentation.uiAutomation.executeShellCommand(command))
            .use { input -> File(context.directory, name).outputStream().use { input.copyTo(it) } }
    }

    private fun captureIfRequested(context: FrameContext, name: String) {
        if (InstrumentationRegistry.getArguments().getString("menuFrameCapture") != "true") return
        val screenshot = requireNotNull(context.instrumentation.uiAutomation.takeScreenshot())
        try {
            File(context.directory, name).outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            screenshot.recycle()
        }
    }

    private data class FrameContext(val instrumentation: Instrumentation, val activity: MainActivity,
        val model: GameViewModel,
        val game: GameState, val directory: File)
}
