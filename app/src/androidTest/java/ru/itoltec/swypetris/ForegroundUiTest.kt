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

    @Test fun immersiveGameplayAndNotificationShadePausesWithoutAutoResume() {
        compose.runOnIdle { model().finishLaunchIntro(); model().newGame() }
        compose.onNodeWithTag("board").assertIsDisplayed()
        compose.waitUntil(5000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.systemBars()) == false
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
            compose.waitUntil(5000) {
                ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.statusBars()) == true
            }
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

    @Test fun focusLossKeepsImmersiveOverlayOpenUntilFocusReturns() {
        compose.runOnIdle { model().finishLaunchIntro(); model().newGame() }
        compose.waitUntil(5000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.systemBars()) == false
        }
        compose.runOnIdle { compose.activity.onWindowFocusChanged(false) }
        compose.onNodeWithTag("resumeGame").assertIsDisplayed()
        val saved = model().game
        compose.runOnIdle {
            assertFalse(ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
                .isVisible(WindowInsetsCompat.Type.systemBars()))
            assertEquals(GameScreen.MENU, model().screen)
        }
        compose.runOnIdle { compose.activity.onWindowFocusChanged(true) }
        compose.waitUntil(5000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.statusBars()) == true
        }
        compose.runOnIdle {
            assertEquals(saved, model().game)
            assertEquals(GameScreen.MENU, model().screen)
        }
    }

    @Test fun lifecycleReturnRequiresContinueAndScoreRespectsInsets() {
        compose.runOnIdle { model().finishLaunchIntro(); model().newGame() }
        val density = compose.activity.resources.displayMetrics.density
        // Platform bar visibility and Compose inset padding update asynchronously.
        compose.waitUntil(5000) {
            val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            val safe = insets?.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()) ?: return@waitUntil false
            val score = compose.onNodeWithTag("score").fetchSemanticsNode().boundsInRoot
            !insets.isVisible(WindowInsetsCompat.Type.systemBars()) &&
                kotlin.math.abs(score.top - safe.top - 3 * density) <= 1f &&
                kotlin.math.abs(score.left - safe.left - 4 * density) <= 1f
        }
        val area = compose.onNodeWithTag("gameArea").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, area.top, 1f)
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
