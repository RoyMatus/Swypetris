package ru.itoltec.swypetris

import android.app.Application
import android.os.SystemClock
import android.os.Build
import android.os.Debug
import android.os.Trace
import android.os.Bundle
import android.util.Log
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in, repeatable input workload for before/after device traces; never a power assertion. */
class EnergyScenarioTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun gameplay() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("energyScenario") == "true")
        val seconds = args.getString("energySeconds", "600").toInt()
        lateinit var model: GameViewModel
        compose.runOnUiThread {
            model = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
            model.navigation.finishLaunchIntro()
            model.options.setSound(true)
            model.options.setVibration(true)
            model.options.chooseMusic(MusicSelection.Track(Song.KOROBEINIKI))
            model.newGame()
        }
        compose.setContent { SwypetrisApp(model) {} }
        val start = SystemClock.uptimeMillis()
        val runtimeBefore = Debug.getRuntimeStats()
        if (Build.VERSION.SDK_INT >= 29) Trace.beginAsyncSection("SwypetrisEnergyGameplay", 1)
        Log.i("SwypetrisEnergy", "start duration=$seconds")
        try {
            repeat(seconds) { step ->
                compose.runOnIdle {
                    if (step % 30 == 0 || model.screen != GameScreen.PLAYING) model.newGame()
                    if (step == 0 || step == seconds / 2) model.options.setHints(step >= seconds / 2)
                    model.input.command(when (step % 6) {
                        0, 1 -> GameCommand.LEFT
                        2 -> GameCommand.CLOCKWISE
                        3, 4 -> GameCommand.RIGHT
                        else -> GameCommand.SOFT_DROP
                    })
                }
                val remaining = start + (step + 1) * 1000L - SystemClock.uptimeMillis()
                if (remaining > 0) SystemClock.sleep(remaining)
            }
        } finally {
            if (Build.VERSION.SDK_INT >= 29) Trace.endAsyncSection("SwypetrisEnergyGameplay", 1)
            sendMetrics(runtimeBefore, start)
            compose.runOnIdle { model.pause() }
            Log.i("SwypetrisEnergy", "end elapsed=${SystemClock.uptimeMillis() - start}")
        }
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
