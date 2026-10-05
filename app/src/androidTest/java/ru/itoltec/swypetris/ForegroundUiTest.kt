package ru.itoltec.swypetris

import android.app.ActivityManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ForegroundUiTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(compose.activity)[GameViewModel::class.java]

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }

    @Test fun statusBarVisibleAndNotificationShadePausesWithoutAutoResume() {
        compose.runOnIdle { model().finishLaunchIntro(); model().newGame() }
        compose.onNodeWithTag("board").assertIsDisplayed()
        compose.runOnIdle {
            val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
            assertTrue(insets.isVisible(WindowInsetsCompat.Type.statusBars()))
        }
        try {
            shell("cmd statusbar expand-notifications")
            compose.waitUntil(5000) { model().screen == GameScreen.MENU }
            val saved = model().game
            compose.runOnIdle { model().advanceFrame(android.os.SystemClock.uptimeMillis() + 10000) }
            assertEquals(saved, model().game)
            shell("cmd statusbar collapse")
            compose.waitUntil(5000) { compose.activity.hasWindowFocus() }
            compose.runOnIdle {
                assertEquals(GameScreen.MENU, model().screen)
                assertEquals(saved, model().game)
            }
            compose.onNodeWithTag("resumeGame").assertIsDisplayed()
        } finally { shell("cmd statusbar collapse") }
    }

    @Test fun launcherRelaunchReusesActivityTaskAndState() {
        compose.runOnIdle { model().finishLaunchIntro(); model().newGame(); model().pause() }
        val previousActivity = compose.activity
        val previousModel = model()
        val game = previousModel.game
        val taskId = previousActivity.taskId
        val intent = previousActivity.packageManager.getLaunchIntentForPackage(previousActivity.packageName)!!
        previousActivity.applicationContext.startActivity(intent)
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(previousActivity, compose.activity)
            assertSame(previousModel, model())
            assertEquals(taskId, compose.activity.taskId)
            assertEquals(game, model().game)
            assertEquals(GameScreen.MENU, model().screen)
            val manager = previousActivity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            assertEquals(1, manager.appTasks.count { it.taskInfo.baseIntent.component?.className == MainActivity::class.java.name })
        }
    }

    @Test fun lifecycleReturnRequiresContinueAndScoreRespectsInsets() {
        compose.runOnIdle { model().finishLaunchIntro(); model().newGame() }
        val area = compose.onNodeWithTag("gameArea").fetchSemanticsNode().boundsInRoot
        val score = compose.onNodeWithTag("score").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        val safeLeft = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).left
        assertEquals(safeLeft + 4 * density, score.left, 1f)
        val safeTop = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).top
        assertEquals(safeTop + 3 * density, score.top, 1f)
        val statusTop = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
            .getInsets(WindowInsetsCompat.Type.statusBars()).top
        assertEquals(0f, area.top, 1f)
        assertTrue(score.top >= statusTop)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val saved = model().game
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle {
            assertEquals(GameScreen.MENU, model().screen)
            assertEquals(saved, model().game)
        }
        compose.onNodeWithTag("resumeGame").assertIsDisplayed()
    }
}
