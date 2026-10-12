package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FireworkParticlesTest {
    @Test fun upwardSparksTurnAndFallWhileOutwardTravelSlows() {
        val spark = (0 until FireworkParticles.COUNT).map { FireworkParticles.spark(0, it) }
            .minBy { it.velocity.y }
        val origin = FireworkParticles.position(spark, 0f)
        val rising = FireworkParticles.position(spark, .15f)
        val apex = FireworkParticles.position(spark, .65f)
        val falling = FireworkParticles.position(spark, 1.8f)
        assertEquals(0f, origin.x, .000001f)
        assertEquals(0f, origin.y, .000001f)
        assertTrue(rising.y < origin.y)
        assertTrue(apex.y < rising.y)
        assertTrue(falling.y > apex.y)
        val side = (0 until FireworkParticles.COUNT).map { FireworkParticles.spark(0, it) }
            .maxBy { it.velocity.x }
        val early = FireworkParticles.position(side, .2f).x - FireworkParticles.position(side, .1f).x
        val late = FireworkParticles.position(side, 1.1f).x - FireworkParticles.position(side, 1f).x
        assertTrue(early > late && late > 0f)
        assertTrue(FireworkParticles.position(side, 1.1f).y > FireworkParticles.position(side, 1f).y)
    }

    @Test fun particlesVaryButTheirIdentityAndHistoricalTrailsAreStable() {
        val particles = (0 until FireworkParticles.COUNT).map { FireworkParticles.spark(1, it) }
        assertTrue(particles.map { it.lifetime }.distinct().size > 1)
        assertTrue(particles.map { it.drag }.distinct().size > 1)
        assertTrue(particles.map { it.velocity }.distinct().size == particles.size)
        particles.forEachIndexed { index, spark ->
            assertSame(spark, FireworkParticles.spark(1, index))
            var previous = 1f
            repeat(spark.trailSamples) { sample ->
                val time = FireworkParticles.trailTime(1f, sample)
                assertTrue(time <= previous)
                assertTrue(time >= 0f)
                previous = time
            }
            assertTrue(spark.trailSamples in 2..FireworkParticles.TRAIL_SAMPLES)
            assertTrue(FireworkParticles.opacity(spark, .1f) > 0f)
            assertTrue(FireworkParticles.opacity(spark, spark.lifetime - .1f) < .2f)
            assertEquals(0f, FireworkParticles.opacity(spark, spark.lifetime))
            assertEquals(0f, FireworkParticles.opacity(spark, spark.lifetime + 1f))
        }
    }

    @Test fun framePartitioningAndLoopWrapDoNotChangeParticlePositions() {
        val spark = FireworkParticles.spark(0, 7)
        var fine = VictoryMotion.PERIOD_MILLIS - 100
        var coarse = fine
        repeat(100) { fine = VictoryMotion.advance(fine, 10) }
        repeat(10) { coarse = VictoryMotion.advance(coarse, 100) }
        assertEquals(fine, coarse)
        assertEquals(FireworkParticles.position(spark, VictoryMotion.burstSeconds(VictoryMotion.age(fine, 0))),
            FireworkParticles.position(spark, VictoryMotion.burstSeconds(VictoryMotion.age(coarse, 0))))
    }
}
