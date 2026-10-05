package ru.itoltec.swypetris

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class MarathonTimingTest {
    @Test fun curveMatchesRepresentativeLevelsAndCapsAt20G() {
        val expected = mapOf(1 to 1_000_000_000L, 5 to 355_196_928L, 10 to 64_151_585L,
            15 to 7_058_616L, 18 to 1_457_139L, 19 to 833_334L)
        expected.forEach { (level, nanos) -> assertEquals(nanos, GameRules.gravityNanos(level)) }
        for (level in listOf(20, 50, Int.MAX_VALUE))
            assertEquals(GameRules.MIN_GRAVITY_NANOS, GameRules.gravityNanos(level))
        assertTrue(1_000_000_000.0 / GameRules.MIN_GRAVITY_NANOS <= 1200)
        for (start in 1..15) assertEquals(GameRules.gravityNanos(start), GameEngine().newGame(start).gravityNanos)
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
        for (level in listOf(1, 5, 10, 15, 19, 100)) {
            val initial = GameState(active = Piece(Tetromino.O, y = -1), next = Tetromino.T,
                lines = (level - 1) * 10)
            assertEquals(run(initial, listOf(2000)), run(initial, List(2000) { 1 }))
            assertEquals(run(initial, listOf(2000)), run(initial, listOf(217, 33, 750, 1000)))
        }
    }

    @Test fun landingConsumesOnlySubsequentLockTimeAndResetsFractionOnMovement() {
        val engine = GameEngine(Random(42))
        var state = GameState(active = Piece(Tetromino.O, y = 17), next = Tetromino.T, lines = 180)
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
            next = Tetromino.T, lines = 179), GameCommand.HARD_DROP)
        assertTrue(initial.clearingRows.isNotEmpty())
        assertEquals(run(initial, listOf(1100)), run(initial, List(1100) { 1 }))
        val result = run(initial, listOf(617))
        assertEquals(19, result.state.level)
        assertEquals(1, result.state.generation)
        assertTrue(result.state.active.y > 0)
    }

    @Test fun pauseFreezesDueEventsAndRetainsFractionalLandingTime() {
        val engine = GameEngine(Random(42))
        val initial = GameState(active = Piece(Tetromino.O, y = 17), next = Tetromino.T, lines = 180)
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
}
