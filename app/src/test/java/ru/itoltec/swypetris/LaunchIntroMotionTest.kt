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

    /** Только бросок ослаблен; очистка и подтверждение настройки сохраняют прежние параметры. */
    @Test fun dropIsHalfStrengthWithShortFallback() {
        assertEquals(128, HapticPulse.Drop.amplitude)
        assertEquals(70L, HapticPulse.Drop.durationFor(true))
        assertEquals(35L, HapticPulse.Drop.durationFor(false))
        assertEquals(HapticPulse(600, 255), HapticPulse.Clear)
        assertEquals(HapticPulse(100, 220), HapticPulse.Preview)
        assertEquals(600L, HapticPulse.Clear.durationFor(false))
    }
}
