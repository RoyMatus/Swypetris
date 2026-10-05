package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test

class SpawnPreviewTest {
    @Test fun previewWaitsForOneEntireEmptyRowForEveryShapeAndRotation() {
        for (next in Tetromino.entries) {
            val previewBottom = spawnPiece(next).cells().maxOf { it.y }
            for (active in Tetromino.entries) for (rotation in 0..3) {
                val piece = Piece(active, rotation = rotation)
                val firstRow = piece.cells().minOf { it.y }
                val touching = piece.copy(y = previewBottom + 1 - firstRow)
                val state = GameState(active = touching, next = next)
                assertFalse("$active/$rotation next $next: no empty row", nextSpawnPreviewVisible(state))
                assertTrue("$active/$rotation next $next: one empty row",
                    nextSpawnPreviewVisible(state.copy(active = touching.copy(y = touching.y + 1))))
            }
        }
    }

    @Test fun spawnAndHoldHidePreviewUntilReplacementClearsIt() {
        val engine = GameEngine(kotlin.random.Random(42))
        val state = engine.newGame()
        assertFalse(nextSpawnPreviewVisible(state))
        val descended = state.copy(active = state.active.copy(y = 8))
        assertTrue(nextSpawnPreviewVisible(descended))
        val held = engine.apply(descended, GameCommand.HOLD)
        assertEquals(descended.active.type, held.held)
        assertFalse(nextSpawnPreviewVisible(held))
        assertFalse(nextSpawnPreviewVisible(engine.apply(descended, GameCommand.HARD_DROP)))
    }
}
