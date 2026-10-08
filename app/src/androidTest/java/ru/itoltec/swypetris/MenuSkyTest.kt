package ru.itoltec.swypetris

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.height
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class MenuSkyTest {
    @get:Rule val compose = createComposeRule()

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun bothThemesKeepArchitectureAnchoredAcrossPhoneCropsAndCloudLoops() {
        var ratio by mutableStateOf(16.0)
        var theme by mutableStateOf("github_light")
        var time by mutableStateOf(0.0)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(360.dp, (360 * ratio / 9).toFloat().dp).testTag("sky")) {
                    MenuSkyArtwork(GamePalettes.find(theme), 1f) { time }
                }
            }
        }
        for (format in listOf(16.0, 19.5, 20.0)) for (palette in listOf("github_light", "synthwave_84")) {
            compose.runOnIdle { ratio = format; theme = palette; time = 0.0 }
            val before = capture("$palette-$format-0")
            assertEquals(360, before.width)
            assertEquals((360 * format / 9).toInt(), before.height)
            for (t in listOf(8.0, 32.0, 53.0)) {
                compose.runOnIdle { time = t }
                val after = capture("$palette-$format-$t")
                // Everything below the sky is anchored, including all lower foreground pixels.
                var skyChanges = 0
                for (y in 0 until before.height) for (x in 0 until before.width) {
                    val same = before.getPixel(x, y) == after.getPixel(x, y)
                    if (y >= before.height / 2) assertTrue("Foreground moved at $x,$y", same)
                    else if (!same) skyChanges++
                }
                assertTrue("Sky animation must be discernible", skyChanges > 100)
            }
        }
    }

    @Test fun clockFreezesWhileBackgroundedResumesWithoutJumpAndHonorsDisabledAnimation() {
        compose.mainClock.autoAdvance = false
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        var enabled by mutableStateOf(true)
        lateinit var time: State<Double>
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            time = rememberMenuSkyTime(enabled) } }
        compose.mainClock.advanceTimeBy(1000)
        var active = 0.0
        compose.runOnIdle { active = time.value; assertTrue(active > .5);
            owner.registry.currentState = Lifecycle.State.CREATED }
        compose.mainClock.advanceTimeBy(10000)
        compose.runOnIdle { assertEquals(active, time.value, .00001);
            owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertTrue(time.value - active in 0.01..0.15); enabled = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { active = time.value }
        compose.mainClock.advanceTimeBy(5000)
        compose.runOnIdle { assertEquals(active, time.value, .00001) }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun landscapeCropDoesNotRelocateOffscreenStarsOrGlow() {
        var theme by mutableStateOf("github_light")
        var time by mutableStateOf(0.0)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(640.dp, 360.dp).testTag("sky")) {
                    MenuSkyArtwork(GamePalettes.find(theme), 1f) { time }
                }
            }
        }
        for (palette in listOf("github_light", "synthwave_84")) {
            compose.runOnIdle { theme = palette; time = 0.0 }
            val before = capture("$palette-landscape-0")
            assertEquals(640, before.width)
            assertEquals(360, before.height)
            compose.runOnIdle { time = 55.0 }
            val after = capture("$palette-landscape-55")
            // This uniform cover crop contains only foreground: every sky effect is above it.
            // An artificial viewport-centered star or glow would change these pixels.
            assertTrue("Offscreen effects leaked into the foreground crop", before.sameAs(after))
        }
    }

    @androidx.annotation.RequiresApi(26)
    private fun capture(name: String): Bitmap = compose.onNodeWithTag("sky").captureToImage().asAndroidBitmap().also {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "issue-176").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { out -> it.compress(Bitmap.CompressFormat.PNG, 100, out) }
    }
}
