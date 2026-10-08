package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VictoryMotionTest {
    @Test fun burstFadesAndRelaunchesAcrossTheCycle() {
        val burst = 2
        val start = burst * VictoryMotion.STAGGER_MILLIS
        val launch = VictoryMotion.age(start + 300, burst)
        val bright = VictoryMotion.age(start + 1800, burst)
        val fade = VictoryMotion.age(start + 4000, burst)
        assertTrue(VictoryMotion.opacity(VictoryMotion.launch(launch)) > 0f)
        assertTrue(VictoryMotion.opacity(VictoryMotion.expansion(bright)) > 0f)
        assertTrue(VictoryMotion.opacity(VictoryMotion.expansion(fade)) <
            VictoryMotion.opacity(VictoryMotion.expansion(bright)))
        assertEquals(0f, VictoryMotion.opacity(VictoryMotion.launch(0f)))
        assertEquals(0f, VictoryMotion.opacity(VictoryMotion.expansion(1f)))
        assertEquals(VictoryMotion.age(start + 300, burst),
            VictoryMotion.age(start + VictoryMotion.PERIOD_MILLIS + 300, burst))
    }

    @Test fun phaseWrapsWithoutOverflowOrNegativeTime() {
        assertEquals(17L, VictoryMotion.advance(VictoryMotion.PERIOD_MILLIS - 3, 20))
        assertEquals(0L, VictoryMotion.advance(0, -100))
        assertTrue(VictoryMotion.advance(Long.MAX_VALUE, Long.MAX_VALUE) in 0 until VictoryMotion.PERIOD_MILLIS)
    }
}
