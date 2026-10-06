package ru.itoltec.swypetris

import android.app.ActivityManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
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

    @Test fun navigationRemainsVisibleTransparentAndOutsideGameplay() {
        for (screen in listOf(GameScreen.MENU, GameScreen.PLAYING, GameScreen.SETTINGS, GameScreen.HELP)) {
            compose.runOnIdle {
                model().finishLaunchIntro()
                when (screen) {
                    GameScreen.PLAYING -> model().newGame()
                    GameScreen.SETTINGS -> model().settings()
                    GameScreen.HELP -> model().help()
                    else -> model().menu()
                }
            }
            compose.waitUntil(5000) {
                ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()) == true
            }
            compose.runOnIdle {
                @Suppress("DEPRECATION")
                assertEquals(android.graphics.Color.TRANSPARENT, compose.activity.window.navigationBarColor)
                if (android.os.Build.VERSION.SDK_INT >= 29)
                    assertFalse(compose.activity.window.isNavigationBarContrastEnforced)
            }
            if (screen == GameScreen.PLAYING) {
                val safe = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
                    .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                val area = compose.onNodeWithTag("gameArea").fetchSemanticsNode().boundsInRoot
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                assertTrue(area.left >= safe.left && area.right <= root.right - safe.right)
                assertEquals(root.bottom - safe.bottom, area.bottom, 1f)
            }
        }
    }

    @Test fun visibleStatusBarAndNotificationShadePausesWithoutAutoResume() {
        compose.runOnIdle { model().finishLaunchIntro(); model().newGame() }
        compose.onNodeWithTag("board").assertIsDisplayed()
        compose.waitUntil(5000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.statusBars()) == true
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

    @Test fun focusLossKeepsStatusBarVisibleAndRequiresContinue() {
        compose.runOnIdle { model().finishLaunchIntro(); model().newGame() }
        compose.waitUntil(5000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.statusBars()) == true
        }
        compose.runOnIdle { compose.activity.onWindowFocusChanged(false) }
        compose.onNodeWithTag("resumeGame").assertIsDisplayed()
        val saved = model().game
        compose.runOnIdle {
            assertTrue(ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
                .isVisible(WindowInsetsCompat.Type.statusBars()))
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
            val area = compose.onNodeWithTag("gameArea").fetchSemanticsNode().boundsInRoot
            insets.isVisible(WindowInsetsCompat.Type.statusBars()) &&
                kotlin.math.abs(area.top - safe.top) <= 1f &&
                score.top >= area.top &&
                kotlin.math.abs(score.left - safe.left - 20 * density) <= 1f
        }
        val area = compose.onNodeWithTag("gameArea").fetchSemanticsNode().boundsInRoot
        assertTrue(area.top > 0f)
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
