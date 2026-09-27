package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test

class GestureControllerTest {
    private val commands = mutableListOf<GameCommand>()
    private val gestures = GestureController(GestureConfig()) { commands += it }

    @Test fun tapStepsDownExactlyOnce() {
        gestures.down(100f, 100f, 0)
        assertTrue(commands.isEmpty())
        gestures.up(101f, 101f, 100)
        gestures.up(101f, 101f, 110)
        assertEquals(listOf(GameCommand.SOFT_DROP), commands)
    }

    @Test fun longStationaryTouchDoesNothing() {
        gestures.down(100f, 100f, 0)
        repeat(50) { gestures.move(100f, 100f, it * 100L) }
        gestures.up(100f, 100f, 6000)
        assertTrue(commands.isEmpty())
    }

    @Test fun jitterDoesNotRotateOrDrop() {
        gestures.down(100f, 100f, 0)
        repeat(50) { gestures.move(100f + it % 3, 100f - it % 4, it * 50L) }
        gestures.up(100f, 100f, 3000)
        assertTrue(commands.isEmpty())
    }

    @Test fun shortDownwardDriftIsNotDropOrTap() {
        gestures.down(0f, 0f, 0)
        gestures.move(0f, 20f, 30)
        gestures.up(0f, 20f, 50)
        assertTrue(commands.isEmpty())
    }

    @Test fun continuousUpwardMovementRotatesOnlyOnce() {
        gestures.down(100f, 100f, 0)
        gestures.move(101f, 75f, 50)
        gestures.move(100f, 50f, 100)
        gestures.up(100f, 50f, 120)
        assertEquals(listOf(GameCommand.CLOCKWISE), commands)
    }

    @Test fun reversalRearmsFromItsBottomWithoutReleasing() {
        gestures.down(100f, 200f, 0)
        gestures.move(100f, 176f, 20)
        gestures.move(100f, 100f, 40)
        gestures.move(100f, 112f, 60)
        gestures.move(100f, 120f, 80)
        gestures.move(100f, 97f, 100)
        assertEquals(1, commands.size)
        gestures.move(100f, 96f, 120)
        assertEquals(listOf(GameCommand.CLOCKWISE, GameCommand.CLOCKWISE), commands)
    }

    @Test fun jitterAndHorizontalMovementDoNotRearmRotation() {
        gestures.down(100f, 200f, 0)
        gestures.move(100f, 176f, 20)
        repeat(5) {
            gestures.move(100f, 180f, 40L + it * 20)
            gestures.move(100f, 170f, 50L + it * 20)
        }
        gestures.move(112f, 170f, 160)
        gestures.move(112f, 100f, 180)
        assertEquals(listOf(GameCommand.CLOCKWISE, GameCommand.RIGHT), commands)
    }

    @Test fun pieceChangeAndClearDoNotRearmContinuousUpwardStroke() {
        gestures.down(100f, 200f, 0)
        gestures.move(100f, 176f, 20)
        gestures.onPieceChanged()
        gestures.move(100f, 140f, 40)
        gestures.setEnabled(false)
        gestures.move(100f, 100f, 60)
        gestures.setEnabled(true)
        gestures.move(100f, 70f, 80)
        assertEquals(listOf(GameCommand.CLOCKWISE), commands)
        gestures.move(100f, 82f, 100)
        gestures.move(100f, 58f, 120)
        assertEquals(2, commands.size)
    }

    @Test fun newTouchRearmsAndReleaseDoesNotAddATap() {
        gestures.down(100f, 200f, 0)
        gestures.up(100f, 150f, 100)
        gestures.down(100f, 200f, 200)
        gestures.up(100f, 150f, 300)
        assertEquals(listOf(GameCommand.CLOCKWISE, GameCommand.CLOCKWISE), commands)
    }

    @Test fun largerSlopAlsoProtectsRotationRearming() {
        val controller = GestureController(GestureConfig(tapSlop = 16f)) { commands += it }
        controller.down(0f, 200f, 0)
        controller.move(0f, 168f, 20)
        controller.move(0f, 180f, 40)
        controller.move(0f, 130f, 60)
        assertEquals(1, commands.size)
        controller.move(0f, 146f, 80)
        controller.move(0f, 114f, 100)
        assertEquals(2, commands.size)
    }

    @Test fun dropAfterLongUpwardStrokeUsesTheLatestPosition() {
        gestures.down(100f, 300f, 0)
        gestures.move(100f, 276f, 20)
        gestures.move(100f, 100f, 40)
        gestures.move(100f, 112f, 60)
        gestures.move(100f, 148f, 80)
        assertEquals(listOf(GameCommand.CLOCKWISE, GameCommand.HARD_DROP), commands)
    }

    @Test fun horizontalAfterLongUpwardStrokeKeepsItsNormalThreshold() {
        gestures.down(100f, 300f, 0)
        gestures.move(100f, 276f, 20)
        gestures.move(100f, 100f, 40)
        gestures.move(112f, 99f, 60)
        assertEquals(listOf(GameCommand.CLOCKWISE, GameCommand.RIGHT), commands)
    }

    @Test fun diagonalDoesNotRotateOrDrop() {
        for (sign in listOf(-1, 1)) {
            gestures.down(100f, 100f, 0)
            gestures.move(160f, 100f + sign * 60, 50)
            gestures.up(160f, 100f + sign * 60, 100)
        }
        assertTrue(commands.isEmpty())
    }

    @Test fun horizontalDriftNeverAccumulatesIntoDrop() {
        gestures.down(0f, 0f, 0)
        repeat(30) { gestures.move(it * 12f, it * 2f, it * 50L) }
        gestures.up(348f, 58f, 1600)
        assertTrue(commands.isNotEmpty())
        assertTrue(commands.all { it == GameCommand.RIGHT })
    }

    @Test fun directionCanReverseWithoutRelease() {
        gestures.down(100f, 100f, 0)
        gestures.move(112f, 100f, 20)
        gestures.move(144f, 100f, 40)
        gestures.move(132f, 100f, 60)
        gestures.up(132f, 100f, 80)
        assertEquals(listOf(GameCommand.RIGHT, GameCommand.RIGHT, GameCommand.LEFT), commands)
    }

    @Test fun holdingAfterHorizontalMovementDoesNotRepeat() {
        gestures.down(0f, 0f, 0)
        gestures.move(12f, 0f, 10)
        gestures.move(12f, 0f, 10000)
        gestures.up(12f, 0f, 11000)
        assertEquals(listOf(GameCommand.RIGHT), commands)
    }

    @Test fun deliberateDownDropsImmediatelyAndReleaseAddsNothing() {
        gestures.down(100f, 100f, 0)
        gestures.move(103f, 148f, 100)
        assertEquals(listOf(GameCommand.HARD_DROP), commands)
        gestures.up(103f, 148f, 120)
        assertEquals(1, commands.size)
    }

    @Test fun slowDeliberateDownAlsoWorks() {
        gestures.down(0f, 0f, 0)
        repeat(8) { gestures.move(0f, (it + 1) * 6f, (it + 1) * 200L) }
        assertEquals(listOf(GameCommand.HARD_DROP), commands)
    }

    @Test fun diagonalBelowDropRatioIsSafe() {
        gestures.down(0f, 0f, 0)
        gestures.move(30f, 50f, 50)
        gestures.up(30f, 50f, 100)
        assertTrue(commands.isEmpty())
    }

    @Test fun horizontalThenRotateThenDrop() {
        gestures.down(100f, 100f, 0)
        gestures.move(112f, 100f, 20)
        gestures.move(112f, 76f, 40)
        gestures.move(112f, 124f, 60)
        assertEquals(listOf(GameCommand.RIGHT, GameCommand.CLOCKWISE, GameCommand.HARD_DROP), commands)
    }

    @Test fun spawnDoesNotActAndReleaseIsNotTap() {
        gestures.down(100f, 100f, 0)
        gestures.onPieceChanged()
        gestures.move(100f, 100f, 50)
        gestures.up(100f, 100f, 100)
        assertTrue(commands.isEmpty())
    }

    @Test fun spawnAllowsSubsequentMovementOfSamePointer() {
        gestures.down(100f, 100f, 0)
        gestures.move(112f, 100f, 20)
        gestures.onPieceChanged()
        gestures.move(124f, 100f, 40)
        assertEquals(listOf(GameCommand.RIGHT, GameCommand.RIGHT), commands)
    }

    @Test fun spawnInsideCallbackStopsRemainingActionsInEvent() {
        lateinit var controller: GestureController
        controller = GestureController(GestureConfig()) {
            commands += it
            controller.onPieceChanged()
        }
        controller.down(0f, 0f, 0)
        controller.move(200f, 0f, 50)
        assertEquals(listOf(GameCommand.RIGHT), commands)
        controller.move(212f, 0f, 100)
        assertEquals(listOf(GameCommand.RIGHT, GameCommand.RIGHT), commands)
    }

    @Test fun clearingTracksPointerWithoutActionsOrTap() {
        gestures.down(0f, 0f, 0)
        gestures.setEnabled(false)
        gestures.move(300f, 400f, 100)
        gestures.setEnabled(true)
        gestures.move(300f, 400f, 150)
        assertTrue(commands.isEmpty())
        gestures.move(312f, 400f, 200)
        assertEquals(listOf(GameCommand.RIGHT), commands)
    }

    @Test fun touchStartingDuringClearNeverBecomesTap() {
        gestures.setEnabled(false)
        gestures.down(0f, 0f, 0)
        gestures.setEnabled(true)
        gestures.up(0f, 0f, 100)
        assertTrue(commands.isEmpty())
    }

    @Test fun cancellationIgnoresRemainingEvents() {
        gestures.down(100f, 100f, 0)
        gestures.cancel()
        gestures.move(100f, 200f, 50)
        gestures.up(100f, 200f, 100)
        assertTrue(commands.isEmpty())
    }

    @Test fun largerSystemSlopRequiresMoreDeliberateDownwardMovement() {
        val controller = GestureController(GestureConfig(tapSlop = 16f)) { commands += it }
        controller.down(0f, 0f, 0)
        controller.move(0f, 48f, 100)
        assertTrue(commands.isEmpty())
        controller.move(0f, 64f, 150)
        assertEquals(listOf(GameCommand.HARD_DROP), commands)
    }
}
