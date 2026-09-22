package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test

/** Проверяет победные границы, приоритет победы и сохранение партии между кругами. */
class VictoryRulesTest {
    /** Граница достигается и обычным падением; команды на поздравлении ничего не меняют. */
    @Test fun boundaryAndFrozenVictory() {
        val engine = GameEngine()
        val start = GameState(active = Piece(Tetromino.O), next = Tetromino.T, score = 79998)
        val before = engine.apply(start, GameCommand.TICK)
        assertFalse(before.victoryPending)
        assertEquals(7, before.roundFruits)
        val won = engine.apply(before, GameCommand.TICK)
        assertTrue(won.victoryPending)
        assertEquals(8, won.roundFruits)
        GameCommand.entries.forEach { assertEquals(won, engine.apply(won, it)) }
    }

    /** Завершение очистки может перескочить порог: превышение не теряется и не сдвигает награды. */
    @Test fun overshootAndSecondRound() {
        val engine = GameEngine()
        val pending = GameState(active = Piece(Tetromino.I), next = Tetromino.T,
            score = 79000, lines = 40, clearingRows = listOf(16,17,18,19))
        assertFalse(engine.checkVictory(pending).victoryPending)
        val won = engine.finishClear(pending)
        assertEquals(80500, won.score)
        assertTrue(won.victoryPending)
        val next = engine.nextRound(won)
        assertEquals(80500, next.score)
        assertEquals(44, next.lines)
        assertEquals(won.level, next.level)
        assertEquals(won.gravityMillis, next.gravityMillis)
        assertEquals(1, next.completedRounds)
        assertEquals(0, next.roundFruits)
        assertTrue(next.board.flatten().all { it == null })
        assertEquals(next, engine.nextRound(next))
        assertEquals(0, next.copy(score = 89999).roundFruits)
        assertEquals(1, next.copy(score = 90000).roundFruits)
        assertFalse(engine.checkVictory(next.copy(score = 159999)).victoryPending)
        assertTrue(engine.checkVictory(next.copy(score = 160000)).victoryPending)
    }

    /** Победа подавляет проигрыш при одновременном достижении порога и закрытом входе. */
    @Test fun victoryWinsOverBlockedSpawnAndNewGameResets() {
        val engine = GameEngine()
        val board = List(20) { y -> List<Tetromino?>(10) { x -> if (y == 0 && x == 4) Tetromino.Z else null } }
        val start = GameState(board = board, active = Piece(Tetromino.O, x = 0, y = 17), next = Tetromino.O, score = 79999)
        val won = engine.apply(start, GameCommand.HARD_DROP)
        assertTrue(won.victoryPending)
        assertFalse(won.gameOver)
        val fresh = engine.newGame()
        assertEquals(0, fresh.score)
        assertEquals(0, fresh.completedRounds)
        assertFalse(fresh.victoryPending)
    }
}
