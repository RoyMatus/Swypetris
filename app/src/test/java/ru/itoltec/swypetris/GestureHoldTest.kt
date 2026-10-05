package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test

class GestureHoldTest {
    private val commands = mutableListOf<GameCommand>()
    private var ready = 0
    private val gestures = GestureController(GestureConfig()) { commands += it }.also { it.onHoldReady = { ready++ } }

    @Test fun readinessAtThreeHundredThenUpFiresOnceUntilRelease() {
        gestures.down(100f, 100f, 1000)
        assertEquals(1300L, gestures.holdDeadline)
        gestures.advanceTime(1299)
        assertEquals(0, ready)
        gestures.advanceTime(1300)
        gestures.advanceTime(1400)
        assertEquals(1, ready)
        assertTrue(commands.isEmpty())
        gestures.move(100f, 76f, 1400)
        gestures.move(200f, 0f, 1500)
        gestures.up(200f, 0f, 1600)
        assertEquals(listOf(GameCommand.HOLD), commands)
        gestures.down(100f, 100f, 1700)
        gestures.move(100f, 76f, 1750)
        assertEquals(listOf(GameCommand.HOLD, GameCommand.CLOCKWISE), commands)
    }

    @Test fun ordinaryUpBeforeReadinessRemainsRotation() {
        gestures.down(100f, 100f, 0)
        gestures.move(100f, 76f, 299)
        gestures.advanceTime(3000)
        assertEquals(0, ready)
        assertEquals(listOf(GameCommand.CLOCKWISE), commands)
    }

    @Test fun releaseCancelAndPieceChangeNeverAddSoftDrop() {
        gestures.down(0f, 0f, 0)
        gestures.advanceTime(300)
        gestures.up(0f, 0f, 301)
        assertTrue(commands.isEmpty())
        gestures.down(0f, 0f, 1000)
        gestures.cancel()
        gestures.advanceTime(1400)
        assertEquals(1, ready)
        gestures.down(0f, 0f, 2000)
        gestures.onPieceChanged()
        gestures.move(0f, -24f, 2500)
        assertEquals(listOf(GameCommand.CLOCKWISE), commands)
    }

    @Test fun earlyMovementDisarmsHoldButSmallJitterDoesNot() {
        gestures.down(0f, 0f, 0)
        gestures.move(9f, 0f, 100)
        gestures.move(9f, -24f, 500)
        assertEquals(0, ready)
        assertEquals(listOf(GameCommand.CLOCKWISE), commands)
        gestures.down(100f, 100f, 1000)
        gestures.move(102f, 103f, 1100)
        gestures.advanceTime(1300)
        gestures.move(102f, 76f, 1350)
        assertEquals(1, ready)
        assertEquals(GameCommand.HOLD, commands.last())
    }
}
