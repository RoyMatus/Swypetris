package ru.itoltec.swypetris

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.espresso.Espresso
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import java.io.File
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

class MenuControlSkinTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun actionArtworkUsesActualInteractionPriorityAcrossDarkLightAndCutCornerThemes() {
        var theme by mutableStateOf("classic")
        var enabled by mutableStateOf(true)
        var selected by mutableStateOf(false)
        val source = MutableInteractionSource()
        var clicks = 0
        compose.setContent {
            val palette = GamePalettes.find(theme)
            CompositionLocalProvider(LocalGamePalette provides palette,
                LocalMenuSkin provides rememberMenuSkin(palette)) {
                Box(Modifier.requiredSize(320.dp, 56.dp).testTag("action")
                    .background(Color.Magenta)
                    .menuActionFrame("primary", source, enabled, selected)
                    .clickable(source, indication = null, enabled = enabled) { clicks++ })
            }
        }
        for (id in listOf("classic", "github_light", "synthwave_84")) {
            compose.runOnIdle { theme = id }
            assertFrame(id, "normal")
            compose.runOnIdle { selected = true }
            assertFrame(id, "selected")
            val focus = FocusInteraction.Focus()
            compose.runOnIdle { assertTrue(source.tryEmit(focus)) }
            assertFrame(id, "focused")
            val press = PressInteraction.Press(Offset.Zero)
            compose.runOnIdle { assertTrue(source.tryEmit(press)) }
            assertFrame(id, "pressed")
            compose.runOnIdle { enabled = false }
            compose.onNodeWithTag("action").assertIsNotEnabled()
            assertFrame(id, "disabled")
            compose.runOnIdle {
                source.tryEmit(PressInteraction.Release(press))
                source.tryEmit(FocusInteraction.Unfocus(focus))
                selected = false
                enabled = true
            }
            compose.onNodeWithTag("action").assertIsEnabled().performClick()
        }
        compose.runOnIdle { assertEquals(3, clicks) }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun disabledNativeToggleKeepsSemanticsAndNeverInvokesItsCallback() {
        var enabled by mutableStateOf(false)
        var checked by mutableStateOf(true)
        var callbacks = 0
        compose.setContent {
            val palette = GamePalettes.find("classic")
            CompositionLocalProvider(LocalGamePalette provides palette,
                LocalMenuSkin provides rememberMenuSkin(palette)) {
                MenuSwitch(checked, { checked = it; callbacks++ }, Modifier.testTag("switch"), enabled)
            }
        }
        compose.onNodeWithTag("switch").assertIsNotEnabled()
        val image = compose.onNodeWithTag("switch").captureToImage()
        assertTrue(image.width > 0 && image.height > 0)
        compose.runOnIdle { assertEquals(0, callbacks); enabled = true }
        compose.onNodeWithTag("switch").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, callbacks); assertEquals(false, checked) }
    }

    @Test fun disabledVectorPreservesSeparateFillAndStrokeOpacity() {
        val vector = disabledMenuDrawable(context.resources, R.drawable.menu_classic_radio_on, .46f, .28f)
        val image = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        vector.setBounds(0, 0, 240, 240)
        vector.draw(Canvas(image))
        // Independent authored circle geometry: center fill, outer radius-10 stroke, clear gap.
        assertEquals(117, android.graphics.Color.alpha(image.getPixel(120, 120)))
        assertEquals(71, android.graphics.Color.alpha(image.getPixel(220, 120)))
        assertEquals(0, android.graphics.Color.alpha(image.getPixel(200, 120)))
        // Eight-bit premultiplication can round RGB when alpha is117; opacity stays exact.
        val center = image.getPixel(120, 120)
        assertTrue(abs(android.graphics.Color.red(center) - 124) <= 2)
        assertTrue(abs(android.graphics.Color.green(center) - 221) <= 2)
        assertTrue(abs(android.graphics.Color.blue(center) - 250) <= 2)
    }

    @Test fun themedDialogRetainsDismissAndConfirmationCallbacks() {
        var visible by mutableStateOf(true)
        var confirmed = 0
        compose.setContent {
            val palette = GamePalettes.find("synthwave_84")
            CompositionLocalProvider(LocalGamePalette provides palette,
                LocalMenuSkin provides rememberMenuSkin(palette)) {
                MaterialTheme {
                    if (visible) MenuAlertDialog(onDismissRequest = { visible = false },
                        title = { Text("Title") }, text = { Text("Content") },
                        confirmButton = { TextButton(onClick = { confirmed++; visible = false }) { Text("Confirm") } },
                        dismissButton = { TextButton(onClick = { visible = false }) { Text("Cancel") } })
                }
            }
        }
        val nativePaneTitle = context.getString(androidx.compose.material3.R.string.m3c_dialog)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, nativePaneTitle)).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, confirmed); visible = true }
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle { assertEquals(1, confirmed); assertEquals(false, visible) }
        compose.runOnIdle { visible = true }
        compose.onNode(isDialog()).performTouchInput { click(Offset(10f, center.y)) }
        compose.runOnIdle { assertEquals(1, confirmed); assertEquals(false, visible) }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun dialogUsesTintedScrimAndNativeBackDismissAtLargeFontScale() {
        var visible by mutableStateOf(true)
        var dismissed = 0
        var dialogView: android.view.View? = null
        var baseline by mutableStateOf(true)
        var nativeBaselineAlpha: Float? = null
        compose.setContent {
            val palette = GamePalettes.find("classic")
            val density = LocalDensity.current
            Box(Modifier.fillMaxSize().background(Color.White))
            CompositionLocalProvider(LocalGamePalette provides palette,
                LocalMenuSkin provides rememberMenuSkin(palette),
                LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme {
                    if (baseline) Dialog(onDismissRequest = {}, properties = DialogProperties(
                        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                        val view = LocalView.current
                        SideEffect {
                            nativeBaselineAlpha = (view.parent as androidx.compose.ui.window.DialogWindowProvider)
                                .window.attributes.dimAmount
                        }
                        Text("Native baseline")
                    } else if (visible) MenuAlertDialog(onDismissRequest = { dismissed++; visible = false },
                        modifier = Modifier.testTag("dialogPanel"),
                        title = { Text("A larger title") }, text = {
                            val view = LocalView.current
                            SideEffect { dialogView = view }
                            Text("Menu dialog body. ".repeat(8))
                        },
                        confirmButton = { TextButton(onClick = { visible = false }) { Text("Accept") } })
                }
            }
        }
        compose.onNodeWithText("Native baseline").assertIsDisplayed()
        val baselineAlpha = checkNotNull(nativeBaselineAlpha)
        compose.runOnIdle { baseline = false }
        compose.onNodeWithText("Accept").assertIsDisplayed()
        compose.waitForIdle()
        val panel = compose.onNodeWithTag("dialogPanel").fetchSemanticsNode().boundsInWindow
        assertTrue("Scrim sample must be outside actual panel: $panel", panel.left > 10f)
        assertNativeScrim(checkNotNull(dialogView), panel, baselineAlpha)
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(1, dismissed); assertEquals(false, visible) }
    }

    @androidx.annotation.RequiresApi(26)
    private fun assertNativeScrim(view: android.view.View, panel: androidx.compose.ui.geometry.Rect,
        baselineAlpha: Float) {
        val location = IntArray(2)
        compose.runOnIdle { view.getLocationOnScreen(location) }
        val sampleX = location[0] + 10
        val sampleY = location[1] + view.height / 2
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var screen = automation.takeScreenshot()
        fun blend(channel: Int) = (255 * (1f - baselineAlpha) + channel * baselineAlpha).roundToInt()
        fun matchesScrim(pixel: Int) = abs(android.graphics.Color.red(pixel) - blend(3)) <= 2 &&
            abs(android.graphics.Color.green(pixel) - blend(5)) <= 2 &&
            abs(android.graphics.Color.blue(pixel) - blend(17)) <= 2
        try {
            // Native window fade can continue after Compose becomes idle. Await its final color.
            compose.waitUntil(3000) {
                screen = automation.takeScreenshot()
                matchesScrim(screen.getPixel(sampleX, sampleY))
            }
        } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
            // Preserve the real frame and window state before the strict hue assertion below.
        }
        val pixel = screen.getPixel(sampleX, sampleY)
        val provider = view.parent as androidx.compose.ui.window.DialogWindowProvider
        val attributes = provider.window.attributes
        val diagnostic = "Scrim pixel=${Integer.toHexString(pixel)} panel=$panel " +
            "sample=$sampleX,$sampleY viewLocation=${location.toList()} view=${view.width}x${view.height} " +
            "window=${attributes.width}x${attributes.height},baselineDim=$baselineAlpha," +
            "dim=${attributes.dimAmount},flags=${attributes.flags}"
        val directory = File(context.getExternalFilesDir(null), "menu-controls-307-failures").apply { mkdirs() }
        File(directory, "scrim-window.txt").writeText(diagnostic)
        File(directory, "scrim-screen.png").outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val dialogFrame = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        File(directory, "scrim-dialog.png").outputStream().use {
            dialogFrame.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue(diagnostic, abs(android.graphics.Color.red(pixel) - blend(3)) <= 2)
        assertTrue(diagnostic, abs(android.graphics.Color.green(pixel) - blend(5)) <= 2)
        assertTrue(diagnostic, abs(android.graphics.Color.blue(pixel) - blend(17)) <= 2)
    }

    @Test fun nullSkinUsesNativeToggleCallbacksAndSemantics() {
        var selected by mutableStateOf(false)
        var callbacks = 0
        compose.setContent {
            CompositionLocalProvider(LocalMenuSkin provides null) {
                MenuCheckbox(selected, { selected = it; callbacks++ }, Modifier.testTag("native"))
            }
        }
        compose.onNodeWithTag("native").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(selected); assertEquals(1, callbacks) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun sliderTrackReplacesSampleSegmentWithActualValueAndMirrorsRtl() {
        val state = SliderState(0f, valueRange = 0f..100f)
        var rtl by mutableStateOf(false)
        compose.setContent {
            val palette = GamePalettes.find("classic")
            CompositionLocalProvider(LocalGamePalette provides palette,
                LocalMenuSkin provides rememberMenuSkin(palette),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.requiredSize(240.dp, 12.dp).testTag("track")) { MenuSliderTrack(state) }
            }
        }
        for (direction in listOf(false, true)) {
            compose.runOnIdle { rtl = direction }
            for (value in listOf(0f, 25f, 100f)) {
                compose.runOnIdle { state.value = value }
                val image = compose.onNodeWithTag("track").captureToImage().asAndroidBitmap()
                for (fraction in listOf(.2f, .8f)) {
                    val position = if (direction) 1f - fraction else fraction
                    val x = ((6f + 228f * position) * image.width / 240).toInt()
                    val expected = if (fraction < value / 100) 0x7CDDFA else 0x132342
                    assertEquals("$direction/$value/$fraction", expected,
                        image.getPixel(x, image.height / 2) and 0xFFFFFF)
                }
            }
        }
    }

    @androidx.annotation.RequiresApi(26)
    private fun assertFrame(theme: String, state: String) {
        compose.waitForIdle()
        val actual = compose.onNodeWithTag("action").captureToImage().asAndroidBitmap()
        val expected = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
        val resource = context.resources.getIdentifier("menu_${theme}_primary_$state", "drawable", context.packageName)
        val vector = checkNotNull(ContextCompat.getDrawable(context, resource))
        vector.setBounds(0, 0, expected.width, expected.height)
        val canvas = Canvas(expected)
        canvas.drawColor(android.graphics.Color.MAGENTA)
        vector.draw(canvas)
        var differing = 0
        for (y in 0 until actual.height) for (x in 0 until actual.width) {
            val a = actual.getPixel(x, y)
            val e = expected.getPixel(x, y)
            if (listOf(0, 8, 16, 24).any { abs((a ushr it and 255) - (e ushr it and 255)) > 3 }) differing++
        }
        assertTrue("$theme/$state: $differing different pixels", differing <= actual.width * actual.height / 50)
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun progressKeepsLiveSemanticsAndUsesAuthoredContainerAcrossLtrAndRtl() {
        var progress by mutableStateOf(0f)
        var rtl by mutableStateOf(false)
        compose.setContent {
            val palette = GamePalettes.find("classic")
            CompositionLocalProvider(LocalGamePalette provides palette,
                LocalMenuSkin provides rememberMenuSkin(palette),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.requiredSize(240.dp, 12.dp).testTag("progressPixels")) {
                    MenuProgressIndicator(progress, Modifier.testTag("progress"))
                }
            }
        }
        for (direction in listOf(false, true)) {
            for (value in listOf(0f, .25f, 1f)) {
                compose.runOnIdle { rtl = direction; progress = value }
                val info = compose.onNodeWithTag("progress").fetchSemanticsNode()
                    .config[SemanticsProperties.ProgressBarRangeInfo]
                assertEquals(ProgressBarRangeInfo(value, 0f..1f), info)
                val image = compose.onNodeWithTag("progressPixels").captureToImage().asAndroidBitmap()
                assertEquals("Authored panel container", 0x132342,
                    image.getPixel(image.width / 2, image.height / 4) and 0xFFFFFF)
                for (fraction in listOf(.2f, .8f)) {
                    val position = if (direction) 1f - fraction else fraction
                    val x = ((6f + 228f * position) * image.width / 240).toInt()
                    assertEquals("$direction/$value/$fraction", if (fraction < value) 0x7CDDFA else 0x132342,
                        image.getPixel(x, image.height / 2) and 0xFFFFFF)
                }
            }
        }
    }
}
