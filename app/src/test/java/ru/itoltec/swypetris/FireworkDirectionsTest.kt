package ru.itoltec.swypetris

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class FireworkDirectionsTest {
    @Test fun cachedDirectionsPreserveOriginalFloatBitsForEverySpark() {
        assertEquals(24, FireworkDirections.RAYS)
        assertEquals(9, FireworkDirections.SPARKS_PER_RAY)
        // Independent pre-cache formula: converting the angular offset to Double earlier changes rounding.
        repeat(24) { ray ->
            val angle = ray * 2.0 * PI / 24
            repeat(9) { spark ->
                val trailAngle = angle + spark * .035f * if (ray % 2 == 0) 1 else -1
                val actual = FireworkDirections[ray, spark]
                assertEquals("cos ray=$ray spark=$spark", cos(trailAngle).toFloat().toRawBits(),
                    actual.x.toRawBits())
                assertEquals("sin ray=$ray spark=$spark", sin(trailAngle).toFloat().toRawBits(),
                    actual.y.toRawBits())
            }
        }
    }
}
