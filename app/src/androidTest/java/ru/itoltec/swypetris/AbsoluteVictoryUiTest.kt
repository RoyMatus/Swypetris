package ru.itoltec.swypetris

import android.content.Intent
import android.accessibilityservice.AccessibilityService
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Replaces retired terminal forms with a non-terminal, accessible record-share action. */
class AbsoluteVictoryUiTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun recordShareAndContinueFitPortraitSizesAndLargeFonts() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O), next = Tetromino.T,
                score = 1_039_999, completedRounds = 12), { 1000L }, false)
        var size by mutableStateOf(DpSize(320.dp, 640.dp))
        var scale by mutableStateOf(1f)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)
                then DeviceConfigurationOverride.FontScale(scale)) {
                CompositionLocalProvider(LocalVictoryAnimations provides false) { SwypetrisApp(model) {} }
            }
        }
        compose.runOnIdle { model.input.command(GameCommand.SOFT_DROP) }
        for (width in listOf(320, 393, 600, 800)) for (font in listOf(1f, 2f)) {
            compose.runOnIdle { size = DpSize(width.dp, (width * 2).dp); scale = font }
            compose.onNodeWithTag("nextRound").assertIsDisplayed()
            compose.onNodeWithTag("shareRoundRecord").assertIsDisplayed()
            val next = compose.onNodeWithTag("nextRound").fetchSemanticsNode().boundsInRoot
            val share = compose.onNodeWithTag("shareRoundRecord").fetchSemanticsNode().boundsInRoot
            assertTrue(next.right <= share.left)
            assertTrue(share.height > 0 && share.width > 0)
        }
        compose.onNodeWithTag("nextRound").performClick()
        compose.runOnIdle { assertEquals(GameScreen.PLAYING, model.screen) }
        compose.onNodeWithTag("shareRoundRecord").assertDoesNotExist()
    }

    @Test fun shareButtonOpensNativeAndroidSharesheet() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val model = GameViewModel(app,
            GameState(active = Piece(Tetromino.O), next = Tetromino.T,
                score = 1_039_999, completedRounds = 12), { 1000L }, false)
        compose.setContent {
            CompositionLocalProvider(LocalVictoryAnimations provides false) { SwypetrisApp(model) {} }
        }
        compose.runOnIdle {
            model.input.command(GameCommand.SOFT_DROP)
        }
        // Compose 1.7 filters selectors on the test thread: avoid merged descendant
        // semantics reads racing the same Owner's main-thread draw.
        compose.waitUntil(timeoutMillis = 10000) {
            compose.onAllNodes(hasTestTag("shareRoundRecord"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png"), "Share")
        val resolver = app.packageManager.resolveActivity(chooser, 0)!!.activityInfo.packageName
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val serviceInfo = automation.serviceInfo
        val originalFlags = serviceInfo.flags
        serviceInfo.flags = originalFlags or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = serviceInfo
        try {
            val opened = automation.executeAndWaitForEvent({
                compose.onNodeWithTag("shareRoundRecord", useUnmergedTree = true).performClick()
            }, { event -> event.eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.packageName?.toString() == resolver }, 10000)
            @Suppress("DEPRECATION")
            opened.recycle()
            compose.waitUntil(timeoutMillis = 10000) {
                automation.windows.any { it.root?.packageName?.toString() == resolver }
            }
            automation.waitForIdle(500, 5000)
        } finally {
            try {
                // Global Back is asynchronous: wait for this host before the next test launches.
                val dismissed = automation.executeAndWaitForEvent({
                    assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
                }, { event ->
                    event.eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                        event.packageName?.toString() == app.packageName
                }, 10000)
                @Suppress("DEPRECATION")
                dismissed.recycle()
                // Cancelling external sharing retains the celebration and its continuation action.
                compose.onNodeWithTag("nextRound").assertIsDisplayed()
                compose.onNodeWithTag("shareRoundRecord").assertIsDisplayed()
                compose.runOnIdle { assertEquals(GameScreen.VICTORY, model.screen) }
            } finally {
                serviceInfo.flags = originalFlags
                automation.serviceInfo = serviceInfo
            }
        }
    }

}
