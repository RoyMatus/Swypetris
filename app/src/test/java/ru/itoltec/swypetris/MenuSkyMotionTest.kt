package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuSkyMotionTest {
    @Test fun everyCloudTravelsRightAndRecyclesOnlyOutsideArtwork() {
        MenuSkyMotion.clouds.forEach { cloud ->
            val step = .01
            var previous = cloud.x(0.0)
            for (i in 1..12000) {
                val next = cloud.x(i * step)
                if (next < previous) {
                    assertTrue("Outgoing cloud must be beyond right edge", previous > MenuSkyMotion.WIDTH)
                    assertTrue("Incoming cloud must still be left of artwork", next + cloud.width < 1f)
                } else assertTrue(next > previous)
                previous = next
            }
            assertEquals(cloud.x(0.0), cloud.x(cloud.loopSeconds), .001f)
            assertEquals(cloud.x(13.0), cloud.x(13.0 + cloud.loopSeconds), .001f)
        }
    }

    @Test fun noSharedThirtyTwoSecondResetAndIndependentShortTwinkle() {
        MenuSkyMotion.clouds.drop(1).forEach {
            assertTrue(it.x(32.01) > it.x(31.99))
        }
        assertTrue(MenuSkyMotion.twinkle(0.0, 0) != MenuSkyMotion.twinkle(0.0, 1))
        val brightness = (0..30).map { MenuSkyMotion.twinkle(it / 10.0, 0) }
        assertTrue(brightness.max() - brightness.min() > .6f)
        assertEquals(MenuSkyMotion.towerPulse(0.0), MenuSkyMotion.towerPulse(4.2), .00001f)
    }
}
