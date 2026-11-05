package ru.itoltec.swypetris

import android.os.SystemClock
import android.os.Build
import android.os.Debug
import android.os.Trace
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import android.app.Instrumentation
import android.content.Intent
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in, repeatable input workload for before/after device traces; never a power assertion. */
class EnergyScenarioTest {
    @get:Rule val storage = IsolatedStorageRule()

    @Test fun gameplay() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("energyScenario") == "true")
        val seconds = args.getString("energySeconds", "600").toInt()
        require(seconds in 5..3600)
        val menu = args.getString("energyMenu") == "true"
        val seededClears = args.getString("energySeededClears") == "true"
        val palette = paletteArgument(args)
        val marker = if (menu) "SwypetrisEnergyMenu" else "SwypetrisEnergyGameplay"
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val activity = awaitActivity(instrumentation)
        try {
            instrumentation.runOnMainSync {
                val model = ViewModelProvider(activity)[GameViewModel::class.java]
                model.navigation.finishLaunchIntro()
                model.options.setSound(true)
                model.options.setVibration(true)
                model.options.chooseMusic(MusicSelection.Track(Song.KOROBEINIKI))
                palette?.let(model.options::setPalette)
                if (args.getString("energyMusic") == "false") model.options.chooseMusic(MusicSelection.Off)
                if (!menu) model.newGame()
            }
            // Use the actual Activity clock: Compose test rules suppress infinite animations.
            SystemClock.sleep(1000)
            val start = SystemClock.uptimeMillis()
            val runtimeBefore = Debug.getRuntimeStats()
            if (Build.VERSION.SDK_INT >= 29) Trace.beginAsyncSection(marker, 1)
            Log.i("SwypetrisEnergy", "start duration=$seconds")
            try {
                repeat(seconds) { step ->
                    instrumentation.runOnMainSync {
                        check(activity.hasWindowFocus()) { "Energy workload lost Activity focus" }
                        val model = ViewModelProvider(activity)[GameViewModel::class.java]
                        if (menu) check(model.screen == GameScreen.MENU) { "Energy workload left the menu" }
                        else advanceWorkload(model, step, seconds, seededClears)
                    }
                    val remaining = start + (step + 1) * 1000L - SystemClock.uptimeMillis()
                    if (remaining > 0) SystemClock.sleep(remaining)
                }
            } finally {
                if (Build.VERSION.SDK_INT >= 29) Trace.endAsyncSection(marker, 1)
                sendMetrics(runtimeBefore, start)
                instrumentation.runOnMainSync { ViewModelProvider(activity)[GameViewModel::class.java].pause() }
                Log.i("SwypetrisEnergy", "end elapsed=${SystemClock.uptimeMillis() - start}")
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun paletteArgument(arguments: Bundle): String? = arguments.getString("energyPalette")?.also { id ->
        require(GamePalettes.all.any { it.id == id }) { "Unknown energy palette: $id" }
    }

    /** Bounded readiness matches the existing real-Activity frame evidence fixtures. */
    private fun awaitActivity(instrumentation: Instrumentation): MainActivity {
        val deadline = SystemClock.elapsedRealtime() + 10000
        var ready: MainActivity? = null
        while (ready == null && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync {
                ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().firstOrNull { it.hasWindowFocus() }
            }
            if (ready == null) SystemClock.sleep(32)
        }
        return checkNotNull(ready) { "Energy Activity did not become ready" }
    }

    private fun advanceWorkload(model: GameViewModel, step: Int, seconds: Int, seededClears: Boolean) {
        if (step % 30 == 0 || model.screen != GameScreen.PLAYING) {
            model.newGame()
            if (seededClears) seedClear(model)
        }
        if (step == 0 || step == seconds / 2) model.options.setHints(step >= seconds / 2)
        model.input.command(when (step % 6) {
            0, 1 -> GameCommand.LEFT
            2 -> GameCommand.CLOCKWISE
            3, 4 -> GameCommand.RIGHT
            else -> GameCommand.SOFT_DROP
        })
    }

    /** Optional fixture guarantees line-clear work instead of assuming random inputs produce it. */
    private fun seedClear(model: GameViewModel) {
        val before = requireNotNull(model.game)
        val board = BoardGeometry.empty().map { it.toMutableList() }
        for (y in 18..19) for (x in 0 until BoardGeometry.WIDTH) {
            if (x !in 4..5) board[BoardGeometry.row(y)][x] = Tetromino.T
        }
        model.simulation.acceptState(before, before.copy(board = board, active = spawnPiece(Tetromino.O)),
            SystemClock.uptimeMillis())
        model.input.command(GameCommand.HARD_DROP)
        check(requireNotNull(model.game).clearingRows.size == 2) { "Energy fixture did not clear two rows" }
    }

    private fun sendMetrics(runtimeBefore: Map<String, String>, start: Long) {
    val metrics = Bundle()
    Debug.getRuntimeStats().forEach { (key, value) ->
        val before = runtimeBefore[key]?.toLongOrNull()
        val after = value?.toLongOrNull()
        if (before != null && after != null) metrics.putString("energy.$key", (after - before).toString())
    }
    metrics.putString("energy.elapsedMillis", (SystemClock.uptimeMillis() - start).toString())
    InstrumentationRegistry.getInstrumentation().sendStatus(2, metrics)
    }
}
