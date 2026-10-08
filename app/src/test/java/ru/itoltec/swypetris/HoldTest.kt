package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HoldTest {
    private val engine = GameEngine(Random(42))

    @Test fun emptyHoldPromotesNextAndConsumesExactlyOneBagEntry() {
        val initial = engine.newGame()
        val bag = engine.remainingBag()
        val held = engine.apply(initial.copy(active = initial.active.copy(y = 7, rotation = 2),
            accelerated = true, lockRemaining = 123, lockResets = 7), GameCommand.HOLD)
        assertEquals(initial.active.type, held.held)
        assertEquals(initial.next, held.active.type)
        assertEquals(0, held.active.rotation)
        assertTrue(held.active.cells().any { it.y < 0 })
        assertEquals(bag.first(), held.next)
        assertEquals(bag.drop(1), engine.remainingBag())
        assertTrue(held.holdUsed)
        assertFalse(held.accelerated)
        assertEquals(500L, held.lockRemaining)
        assertEquals(0, held.lockResets)
        assertEquals(held, engine.apply(held, GameCommand.HOLD))
    }

    @Test fun lockEnablesSwapWithoutDrawingOrChangingNext() {
        val initial = engine.newGame()
        val held = engine.apply(initial, GameCommand.HOLD)
        val placed = engine.apply(held, GameCommand.HARD_DROP)
        assertFalse(placed.holdUsed)
        val bag = engine.remainingBag()
        val swapped = engine.apply(placed, GameCommand.HOLD)
        assertEquals(initial.active.type, swapped.active.type)
        assertEquals(placed.active.type, swapped.held)
        assertEquals(placed.next, swapped.next)
        assertEquals(bag, engine.remainingBag())
        assertEquals(0, swapped.active.rotation)
        assertTrue(swapped.holdUsed)
        assertNull(feedbackEvent(placed.copy(accelerated = true), swapped, GameCommand.HOLD))
    }

    @Test fun blockedHoldSpawnIsBlockOutAndNewGameClearsTheSlot() {
        val start = GameState(active = Piece(Tetromino.I, y = 6), next = Tetromino.L, held = Tetromino.O)
        val board = start.board.map { it.toMutableList() }
        board[BoardGeometry.row(-1)][4] = Tetromino.Z
        val lost = engine.apply(start.copy(board = board), GameCommand.HOLD)
        assertTrue(lost.gameOver)
        assertEquals(TopOut.BLOCK_OUT, lost.topOut)
        assertNull(engine.newGame().held)
        assertFalse(engine.newGame().holdUsed)
    }
}
