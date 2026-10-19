package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gain routing and attenuation safety; these checks cannot assess perceived loudness. */
class ThemeSoundGainTest {
    @Test fun eventUsesItsOwnSampleGain() {
        val pair = ThemeSoundPair(R.raw.drop_soft, R.raw.clear_soft, .4f, .2f)
        assertEquals(.4f, pair.volume(FeedbackEvent.DROP), 0f)
        assertEquals(.2f, pair.volume(FeedbackEvent.CLEAR), 0f)
    }

    @Test fun classicClearOutlierIsAttenuatedMoreThanItsDrop() {
        val pair = ThemeSounds.forPalette("classic")
        assertTrue(pair.volume(FeedbackEvent.CLEAR) < pair.volume(FeedbackEvent.DROP))
        assertTrue(pair.volume(FeedbackEvent.DROP) < .55f)
    }

    @Test fun everyThemeUsesFinitePositiveAttenuationOnly() {
        for (palette in GamePalettes.all) for (event in FeedbackEvent.entries) {
            val volume = ThemeSounds.forPalette(palette.id).volume(event)
            assertTrue("${palette.id}/$event", volume.isFinite() && volume > 0f && volume <= .55f)
        }
    }

    @Test fun unknownPaletteUsesClassicCalibrationForBothEvents() {
        for (event in FeedbackEvent.entries) {
            assertEquals(ThemeSounds.forPalette("classic").volume(event),
                ThemeSounds.forPalette("unknown-palette").volume(event), 0f)
        }
    }

    @Test fun invalidOrAmplifyingCalibrationIsRejected() {
        for (volume in listOf(0f, -.1f, .56f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                ThemeSoundPair(R.raw.drop_soft, R.raw.clear_soft, volume, .2f)
            }
            assertThrows(IllegalArgumentException::class.java) {
                ThemeSoundPair(R.raw.drop_soft, R.raw.clear_soft, .2f, volume)
            }
        }
    }
}
