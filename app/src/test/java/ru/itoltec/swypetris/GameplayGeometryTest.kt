package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Test

class GameplayGeometryTest {
    @Test fun visibleSpawnRowStartsAtSafeTopAndBottomVisibleRowStaysAtBottom() {
        for (height in listOf(400f, 640f, 900f)) for (top in listOf(0f, 24f, 52f, 90f)) {
            val geometry = gameplayGeometry(height, top)
            assertEquals(top, geometry.gridTop + geometry.cellHeight, .001f)
            assertEquals(height, geometry.gridTop + 22 * geometry.cellHeight, .001f)
        }
    }

    @Test fun noAvailableHeightNeverProducesNegativeCells() {
        for (height in listOf(0f, 24f)) {
            val geometry = gameplayGeometry(height, 52f)
            assertEquals(height, geometry.safeTop, 0f)
            assertEquals(0f, geometry.cellHeight, 0f)
        }
    }
}
