package ru.itoltec.swypetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

class DigitalScoreRenderingTest {
    @get:Rule val compose = createComposeRule()

    @SdkSuppress(minSdkVersion = 26)
    @Test fun cachedDigitsMatchOriginalPixelsAcrossValuesSizesAndColors() {
        var sample by mutableStateOf(ScoreSample("0", 12, Color.White))
        var referenceVisible by mutableStateOf(false)
        compose.setContent {
            if (referenceVisible) ReferenceScore(sample.value, sample.height, sample.ink)
            else DigitalScore(sample.value, sample.height.dp, Modifier.testTag("actual"), sample.ink)
        }
        val samples = listOf(12, 24).flatMap { height -> listOf(Color.White, Color.Black).flatMap { ink ->
            listOf("0", "8", "0123456789", Long.MAX_VALUE.toString(), "-1").map { ScoreSample(it, height, ink) }
        } }
        val actuals = samples.map { next ->
            compose.runOnIdle { sample = next }
            compose.onNodeWithTag("actual").assertTextEquals(next.value)
            compose.onNodeWithTag("actual").captureToImage().asAndroidBitmap()
        }
        // Use the same origin: translated antialiasing can differ by one channel even for identical paths.
        compose.runOnIdle { referenceVisible = true }
        samples.forEachIndexed { index, next ->
            compose.runOnIdle { sample = next }
            val reference = compose.onNodeWithTag("reference").captureToImage().asAndroidBitmap()
            assertSameDigits(actuals[index], reference, next)
        }
    }
}

private data class ScoreSample(val value: String, val height: Int, val ink: Color)

private fun assertSameDigits(actual: android.graphics.Bitmap, reference: android.graphics.Bitmap, sample: ScoreSample) {
    if (!actual.sameAs(reference)) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null), "digital-score-audit").apply { mkdirs() }
        for ((name, bitmap) in listOf("actual" to actual, "reference" to reference)) {
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
    assertTrue("Unchanged digits: $sample", actual.sameAs(reference))
}

/** Original uncached renderer retained as a pixel oracle for geometry and dim inactive segments. */
@Composable
private fun ReferenceScore(value: String, height: Int, ink: Color) {
    val masks = intArrayOf(0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f)
    Canvas(Modifier.size(height.dp * digitalScoreAspect(value.length), height.dp).testTag("reference")) {
        value.forEachIndexed { index, character ->
            val mask = if (character in '0'..'9') masks[character - '0'] else 0x40
            val x = index * (.48f + .14f) * size.height
            val width = .48f * size.height
            val thickness = .075f * size.height
            val middle = size.height / 2
            val segments = listOf(
                Triple(Offset(x + thickness / 2, 0f), width - thickness, thickness),
                Triple(Offset(x + width - thickness, thickness), thickness, middle - thickness * 1.5f),
                Triple(Offset(x + width - thickness, middle + thickness / 2), thickness,
                    middle - thickness * 1.5f),
                Triple(Offset(x + thickness / 2, size.height - thickness), width - thickness, thickness),
                Triple(Offset(x, middle + thickness / 2), thickness, middle - thickness * 1.5f),
                Triple(Offset(x, thickness), thickness, middle - thickness * 1.5f),
                Triple(Offset(x + thickness / 2, middle - thickness / 2), width - thickness, thickness)
            )
            segments.forEachIndexed { segment, bounds ->
                drawPath(referenceSegment(bounds), ink.copy(alpha = if (mask and (1 shl segment) != 0) 1f else .10f))
            }
        }
    }
}

private fun referenceSegment(segment: Triple<Offset, Float, Float>): Path {
    val (at, width, height) = segment
    val bevel = minOf(width, height) / 2
    return Path().apply {
        if (width > height) {
            moveTo(at.x, at.y + height / 2)
            lineTo(at.x + bevel, at.y)
            lineTo(at.x + width - bevel, at.y)
            lineTo(at.x + width, at.y + height / 2)
            lineTo(at.x + width - bevel, at.y + height)
            lineTo(at.x + bevel, at.y + height)
        } else {
            moveTo(at.x + width / 2, at.y)
            lineTo(at.x + width, at.y + bevel)
            lineTo(at.x + width, at.y + height - bevel)
            lineTo(at.x + width / 2, at.y + height)
            lineTo(at.x, at.y + height - bevel)
            lineTo(at.x, at.y + bevel)
        }
        close()
    }
}
