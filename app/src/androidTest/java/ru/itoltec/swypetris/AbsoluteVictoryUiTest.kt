package ru.itoltec.swypetris

import android.graphics.Bitmap
import android.content.Intent
import android.accessibilityservice.AccessibilityService
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Verify controls at narrow, tall and landscape sizes, including larger accessible fonts. */
class AbsoluteVictoryUiTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun nameEntryAndPostcardFitMultipleSizesAndFontScales() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active = Piece(Tetromino.O), next = Tetromino.T,
                score = GameRules.MAX_SCORE - 1, completedRounds = 12), { 1000L }, false)
        var viewport by mutableStateOf(DpSize(320.dp, 640.dp))
        var fontScale by mutableStateOf(1.5f)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport) then
                DeviceConfigurationOverride.FontScale(fontScale)) {
                CompositionLocalProvider(LocalVictoryAnimations provides false) { SwypetrisApp(model) {} }
            }
        }
        compose.runOnIdle { model.input.command(GameCommand.SOFT_DROP) }
        for (size in viewports()) {
            viewport = size
            compose.waitForIdle()
            compose.onNodeWithTag("absoluteName").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("preparePostcard").performScrollTo().assertIsDisplayed()
            screenshot("entry-${size.width.value}-${size.height.value}.png")
        }
        compose.onNodeWithTag("preparePostcard").performClick()
        compose.runOnIdle { assertEquals(true, model.absoluteNameError) }
        compose.onNodeWithTag("absoluteName").performScrollTo().performTextInput("Александра")
        compose.onNodeWithTag("preparePostcard").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10000) { model.postcardReady }
        compose.waitUntil(timeoutMillis = 10000) {
            compose.onAllNodes(hasTestTag("postcardPreview")).fetchSemanticsNodes().isNotEmpty()
        }
        for (size in viewports()) {
            viewport = size
            fontScale = 2f
            compose.waitForIdle()
            compose.onNodeWithTag("absoluteVictoryPage").performScrollToNode(hasTestTag("postcardPreview"))
            compose.onNodeWithTag("postcardPreview").assertIsDisplayed()
            screenshot("postcard-${size.width.value}-${size.height.value}.png")
            compose.onNodeWithTag("sharePostcard").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("nextRound").assertDoesNotExist()
    }

    private fun viewports() = listOf(DpSize(320.dp, 640.dp), DpSize(393.dp, 873.dp),
        DpSize(600.dp, 400.dp), DpSize(800.dp, 480.dp))

    @Test fun shareButtonOpensNativeAndroidSharesheet() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val model = GameViewModel(app,
            GameState(active = Piece(Tetromino.O), next = Tetromino.T,
                score = GameRules.MAX_SCORE, completedRounds = 12), { 1000L }, false)
        compose.setContent {
            CompositionLocalProvider(LocalVictoryAnimations provides false) { SwypetrisApp(model) {} }
        }
        compose.runOnIdle {
            model.absolute.changeName("Roy")
            model.absolute.preparePostcard()
        }
        compose.waitUntil(timeoutMillis = 10000) {
            compose.onAllNodes(hasTestTag("sharePostcard")).fetchSemanticsNodes().isNotEmpty()
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
                compose.onNodeWithTag("sharePostcard").performScrollTo().performClick()
            }, { event -> event.eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.packageName?.toString() == resolver }, 10000)
            @Suppress("DEPRECATION")
            opened.recycle()
            compose.waitUntil(timeoutMillis = 10000) {
                automation.windows.any { it.root?.packageName?.toString() == resolver }
            }
            automation.waitForIdle(500, 5000)
            val directory = File(app.getExternalFilesDir(null), "absolute-victory-screenshots").apply { mkdirs() }
            val screenshot = automation.takeScreenshot()
            try {
                File(directory, "native-sharesheet.png").outputStream().use {
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { screenshot.recycle() }
        } finally {
            automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            serviceInfo.flags = originalFlags
            automation.serviceInfo = serviceInfo
        }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    private fun screenshot(name: String) {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val directory = File(context.getExternalFilesDir(null), "absolute-victory-screenshots").apply { mkdirs() }
        File(directory, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
