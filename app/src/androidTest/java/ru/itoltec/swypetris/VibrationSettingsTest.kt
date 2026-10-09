package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercise accessible slider input and settings reachability across screen/font sizes. */
class VibrationSettingsTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun strengthSliderPersistsAndFitsNarrowLandscapeAndLargeFontScreens() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = GameViewModel(app, null, { 1000L }, false)
        model.navigation.settings()
        var viewport by mutableStateOf(DpSize(320.dp, 640.dp))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport) then
                DeviceConfigurationOverride.FontScale(2f)) { SwypetrisApp(model) {} }
        }
        for (size in listOf(DpSize(320.dp, 640.dp), DpSize(393.dp, 873.dp), DpSize(600.dp, 400.dp))) {
            viewport = size
            compose.waitForIdle()
            val slider = compose.onNodeWithTag("vibrationStrength").performScrollTo().assertIsDisplayed()
            for (percent in listOf(0, 50, 100)) {
                slider.performSemanticsAction(SemanticsActions.SetProgress) { it(percent.toFloat()) }
                compose.onNodeWithText("Сила вибрации: $percent%").assertTextEquals("Сила вибрации: $percent%")
                compose.runOnIdle {
                    assertEquals(percent, model.vibrationStrength)
                    assertEquals(percent, GameViewModel(app, null, { 1000L }, false).vibrationStrength)
                }
            }
            compose.onNodeWithText("Сила вибрации: 100%").performScrollTo().assertIsDisplayed()
            val directory = File(app.getExternalFilesDir(null), "vibration-screenshots").apply { mkdirs() }
            File(directory, "strength-${size.width.value}-${size.height.value}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
