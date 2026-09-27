package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test

/** Проверяет порядок стадий заставки и точное завершение каждой траектории. */
class LaunchIntroMotionTest {
    /** Полосы заканчивают раньше надписи, а кнопки ждут сборки всех кубиков. */
    @Test fun piecesSettleBeforeButtonsAppear() {
        repeat(40) {
            assertEquals(0f, LaunchIntroMotion.stripeProgress(0, it, 40))
            assertEquals(1f, LaunchIntroMotion.stripeProgress(1100, it, 40))
        }
        repeat(300) {
            assertEquals(0f, LaunchIntroMotion.cubeProgress(449, it))
            assertEquals(1f, LaunchIntroMotion.cubeProgress(2650, it))
        }
        assertEquals(0f, LaunchIntroMotion.buttonsAlpha(2699))
        assertEquals(.5f, LaunchIntroMotion.buttonsAlpha(2850))
        assertEquals(1f, LaunchIntroMotion.buttonsAlpha(3000))
        assertEquals(1f, LaunchIntroMotion.fallProgress(1f))
        assertTrue((0..100).map { LaunchIntroMotion.cubeProgress(1500, it) }.distinct().size > 30)
    }

    /** Clear strength is halved again; drop and settings preview retain their existing pulses. */
    @Test fun lineClearIsHalfStrengthWithShortFallback() {
        assertEquals(128, HapticPulse.Drop.amplitude)
        assertEquals(70L, HapticPulse.Drop.durationFor(true))
        assertEquals(35L, HapticPulse.Drop.durationFor(false))
        assertEquals(HapticPulse(600, 127, 300), HapticPulse.Clear)
        assertEquals(HapticPulse(100, 220), HapticPulse.Preview)
        assertEquals(300L, HapticPulse.Clear.durationFor(false))
        assertEquals(HapticPulse(70, 64, 18), HapticPulse.Drop.softened())
        assertEquals(HapticPulse(600, 64, 150), HapticPulse.Clear.softened())
        assertEquals(HapticPulse(220, 64, 55), HapticPulse.clear(220).softened())
        assertEquals(HapticPulse(100, 110, 50), HapticPulse.Preview.softened())
        assertEquals(HapticPulse(220, 128, 110), HapticPulse(220, 255).softened())
    }
}
