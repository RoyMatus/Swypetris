package ru.itoltec.swypetris

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Проверяет настоящий жизненный цикл Activity с сохраняемой моделью заставки. */
class LaunchIntroRecreationTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    /** Пересоздание и фон сохраняют прогресс; после завершения повторного запуска заставки нет. */
    @Test fun recreateAndBackgroundPreserveIntro() {
        compose.mainClock.autoAdvance = false
        lateinit var original: GameViewModel
        compose.activityRule.scenario.onActivity {
            original = ViewModelProvider(it)[GameViewModel::class.java]
            assertTrue(original.launchIntroPending)
            original.advanceLaunchIntro(1200)
        }
        compose.mainClock.advanceTimeByFrame()
        val before = original.launchIntroMillis
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity {
            assertSame(original, ViewModelProvider(it)[GameViewModel::class.java])
            assertEquals(before, original.launchIntroMillis)
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.mainClock.advanceTimeBy(10000)
        assertEquals(before, original.launchIntroMillis)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeBy(4000)
        compose.onNodeWithTag("newGame").assertIsDisplayed()
        assertFalse(original.launchIntroPending)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("launchIntro").assertDoesNotExist()
        compose.onNodeWithTag("newGame").assertIsDisplayed()
    }
}
