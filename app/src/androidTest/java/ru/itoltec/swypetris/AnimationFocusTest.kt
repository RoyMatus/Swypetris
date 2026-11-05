package ru.itoltec.swypetris

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnimationFocusTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val focused = mutableStateOf(true)

    @Test fun introFreezesWithoutWindowFocusAndResumesFromItsPhase() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(), null, { 1000L }, false,
            showLaunchIntro = true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FocusWindow(focused) {
                CompositionLocalProvider(LocalLaunchIntroAnimations provides true) { LaunchIntroClock(model) }
            }
        }
        verifyClock({ model.launchIntroMillis })
    }

    @Test fun celebrationFreezesWithoutWindowFocusAndResumesFromItsPhase() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 79999, lines = 100),
            { 1000L }, false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FocusWindow(focused) {
                CompositionLocalProvider(LocalVictoryAnimations provides true) { VictoryScene(model) {} }
            }
        }
        compose.runOnIdle { model.input.command(GameCommand.SOFT_DROP) }
        verifyClock({ model.victoryAnimationMillis })
    }

    private fun verifyClock(time: () -> Long) {
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertTrue(time() > 0); focused.value = false }
        compose.mainClock.advanceTimeByFrame()
        var frozen = 0L
        compose.runOnIdle { frozen = time() }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(frozen, time()); focused.value = true }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertTrue(time() > frozen && time() < frozen + 200) }
    }
}

@Composable
private fun FocusWindow(focused: State<Boolean>, content: @Composable () -> Unit) {
    val host = LocalWindowInfo.current
    val window = remember(host, focused) { object : WindowInfo by host {
        override val isWindowFocused: Boolean get() = focused.value
    } }
    CompositionLocalProvider(LocalWindowInfo provides window, content = content)
}
