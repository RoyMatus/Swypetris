package ru.itoltec.swypetris

import kotlin.random.Random
import kotlin.math.pow
import kotlin.math.roundToLong
import org.junit.Assert.*
import org.junit.Test

class MarathonTimingTest {
    @Test fun curveMatchesRepresentativeLevelsAndCapsAt20G() {
        val expected = mapOf(1 to 1_000_000_000L, 13 to 355_196_928L, 28 to 64_151_585L,
            43 to 7_058_616L, 52 to 1_457_139L, 55 to 833_334L)
        expected.forEach { (level, nanos) -> assertEquals(nanos, GameRules.gravityNanos(level)) }
        for (level in listOf(56, 100, Int.MAX_VALUE))
            assertEquals(GameRules.MIN_GRAVITY_NANOS, GameRules.gravityNanos(level))
        assertTrue(1_000_000_000.0 / GameRules.MIN_GRAVITY_NANOS <= 1200)
        for (start in 1..15) {
            val steps = start - 1
            val original = ((0.8 - steps * 0.007).pow(steps) * 1_000_000_000).roundToLong()
            val initial = GameEngine().newGame(start)
            assertEquals(original, initial.gravityNanos)
            assertEquals(original, initial.copy(lines = start * 10 - 1).gravityNanos)
            for (advance in 1..19 - start) {
                val oldSteps = steps + advance
                val oldNanos = if (oldSteps >= 18) GameRules.MIN_GRAVITY_NANOS else
                    ((0.8 - oldSteps * 0.007).pow(oldSteps) * 1_000_000_000).roundToLong()
                assertEquals(oldNanos, initial.copy(lines = (start + advance * 3 - 1) * 10).gravityNanos)
            }
        }
        assertEquals(500L, LockRules.DELAY_MILLIS)
        assertEquals(15, LockRules.MAX_RESETS)
    }

    private data class Snapshot(val state: GameState, val bag: List<Tetromino>, val gravity: Long,
        val fraction: Long, val clear: Long)

    private fun run(initial: GameState, frames: List<Long>): Snapshot {
        val engine = GameEngine(Random(42))
        val timeline = GameTimeline(initial.gravityNanos)
        var state = initial
        frames.forEach { state = timeline.advance(state, it, engine) { _, _ -> } }
        return Snapshot(state, engine.remainingBag(), timeline.gravityRemainingNanos,
            timeline.lockFractionNanos, timeline.clearElapsedNanos)
    }

    @Test fun callbackPartitionsPreserveBoardBagAndAllClockRemainders() {
        for (level in listOf(1, 5, 10, 15, 19, 55, 100)) {
            val initial = GameState(active = Piece(Tetromino.O, y = -1), next = Tetromino.T,
                lines = (level - 1) * 10)
            assertEquals(run(initial, listOf(2000)), run(initial, List(2000) { 1 }))
            assertEquals(run(initial, listOf(2000)), run(initial, listOf(217, 33, 750, 1000)))
        }
    }

    @Test fun landingConsumesOnlySubsequentLockTimeAndResetsFractionOnMovement() {
        val engine = GameEngine(Random(42))
        var state = GameState(active = Piece(Tetromino.O, y = 17), next = Tetromino.T, lines = 540)
        val timeline = GameTimeline(state.gravityNanos)
        state = timeline.advance(state, 1, engine) { _, _ -> }
        assertEquals(18, state.active.y)
        assertEquals(500L, state.lockRemaining)
        assertEquals(166_666L, timeline.lockFractionNanos)
        assertEquals(499_833_334L, timeline.nextEventNanos(state, engine))
        val moved = engine.apply(state, GameCommand.LEFT)
        timeline.observe(state, moved)
        assertEquals(0L, timeline.lockFractionNanos)
        assertEquals(500_000_000L, timeline.nextEventNanos(moved, engine))
        val beforeLock = timeline.advance(moved, 499, engine) { _, _ -> }
        assertEquals(0, beforeLock.generation)
        assertEquals(1, timeline.advance(beforeLock, 1, engine) { _, _ -> }.generation)
    }

    @Test fun longCallbackCompletesClearThenSpendsRemainderOnNextPiece() {
        val engine = GameEngine(Random(42))
        val board = BoardGeometry.empty().map { it.toMutableList() }
        for (x in 0..7) board[BoardGeometry.row(19)][x] = Tetromino.J
        val initial = engine.apply(GameState(board = board, active = Piece(Tetromino.O, x = 8, y = 18),
            next = Tetromino.T, lines = 539), GameCommand.HARD_DROP)
        assertTrue(initial.clearingRows.isNotEmpty())
        assertEquals(run(initial, listOf(1100)), run(initial, List(1100) { 1 }))
        val result = run(initial, listOf(617))
        assertEquals(55, result.state.level)
        assertEquals(1, result.state.generation)
        assertTrue(result.state.active.y > 0)
    }

    @Test fun pauseFreezesDueEventsAndRetainsFractionalLandingTime() {
        val engine = GameEngine(Random(42))
        val initial = GameState(active = Piece(Tetromino.O, y = 17), next = Tetromino.T, lines = 540)
        val timeline = GameTimeline(initial.gravityNanos)
        val landed = timeline.advance(initial, 1, engine) { _, _ -> }
        val frozen = timeline.freeze(landed, 217, engine)
        assertEquals(283L, frozen.lockRemaining)
        assertEquals(166_666L, timeline.lockFractionNanos)
        val remaining = timeline.advance(frozen, 282, engine) { _, _ -> }
        assertEquals(0, remaining.generation)
        assertEquals(1, timeline.advance(remaining, 1, engine) { _, _ -> }.generation)
        val due = GameTimeline(GameRules.gravityNanos(1))
        val airborne = initial.copy(active = Piece(Tetromino.O), lines = 0)
        assertEquals(airborne, due.freeze(airborne, 1100, engine))
        assertEquals(1, due.advance(airborne, 0, engine) { _, _ -> }.active.y)
    }

    @Test fun gameOverConsumesOnlyTimeBeforeItsLogicalDeadline() {
        val board = BoardGeometry.empty().map { it.toMutableList() }
        board[BoardGeometry.row(0)][4] = Tetromino.Z
        val initial = GameState(board = board, active = Piece(Tetromino.O, x = 0, y = 18), next = Tetromino.T)
        for (frames in listOf(listOf(1000L), List(1000) { 1L })) {
            val engine = GameEngine(Random(42))
            val timeline = GameTimeline(initial.gravityNanos)
            var state = initial
            var elapsed = 0L
            frames.forEach {
                state = timeline.advance(state, it, engine) { _, _ -> }
                elapsed += timeline.advancedNanos
            }
            assertTrue(state.gameOver)
            assertEquals(500_000_000L, elapsed)
        }
    }
}
