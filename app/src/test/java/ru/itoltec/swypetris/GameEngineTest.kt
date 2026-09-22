package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Проверяет правила на искусственно заданных полях, без Android и реального времени. */
class GameEngineTest {
    private val engine = GameEngine(Random(42))

    /** Создаёт пустую партию с нужной фигурой для независимости от генератора. */
    private fun state(piece: Piece = Piece(Tetromino.T)) = GameState(active = piece, next = Tetromino.O)

    /** Стены и занятые клетки запрещают горизонтальное движение. */
    @Test fun movementStopsAtWallsAndBlocks() {
        val left = state(Piece(Tetromino.O, x = 0))
        assertEquals(left, engine.apply(left, GameCommand.LEFT))
        val right = state(Piece(Tetromino.O, x = 8))
        assertEquals(right, engine.apply(right, GameCommand.RIGHT))
        val board = left.board.map { it.toMutableList() }
        board[0][2] = Tetromino.I
        val blocked = left.copy(board = board)
        assertEquals(blocked, engine.apply(blocked, GameCommand.RIGHT))
    }

    /** Противоположные повороты взаимно обратны, четыре поворота возвращают исходную форму. */
    @Test fun rotationsAreReversible() {
        Tetromino.entries.forEach { type ->
            val start = state(Piece(type, y = 4))
            assertEquals(start, engine.apply(engine.apply(start, GameCommand.CLOCKWISE), GameCommand.COUNTERCLOCKWISE))
            var rotated = start
            repeat(4) { rotated = engine.apply(rotated, GameCommand.CLOCKWISE) }
            assertEquals(start, rotated)
        }
    }

    /** Горизонтальная поправка позволяет повернуть вертикальную палку у левой стены. */
    @Test fun rotationKicksAwayFromWall() {
        val start = state(Piece(Tetromino.I, x = -2, y = 4, rotation = 1))
        assertTrue(engine.fits(start, start.active))
        val rotated = engine.apply(start, GameCommand.CLOCKWISE)
        assertEquals(2, rotated.active.rotation)
        assertEquals(0, rotated.active.x)
        assertTrue(engine.fits(rotated, rotated.active))
    }

    /** При отсутствии свободного положения поворот ничего не меняет. */
    @Test fun impossibleRotationIsRejected() {
        val start = state(Piece(Tetromino.I, x = 3, y = 18))
        assertEquals(start, engine.apply(start, GameCommand.CLOCKWISE))
    }

    /** Бросок совпадает с тенью, начисляет очки за расстояние и сразу фиксирует блоки. */
    @Test fun hardDropLocksAtGhostAndScores() {
        val start = state(Piece(Tetromino.O))
        val ghost = engine.ghost(start)
        val result = engine.apply(start, GameCommand.HARD_DROP)
        assertEquals(18, ghost.y)
        assertEquals(18, result.score)
        assertEquals(1, result.generation)
        ghost.cells().forEach { assertEquals(Tetromino.O, result.board[it.y][it.x]) }
    }

    /** Достижение опоры не фиксирует фигуру до следующей неудачной попытки спуска. */
    @Test fun softDropAndGravityLockOnNextAttempt() {
        val start = state(Piece(Tetromino.O, y = 17))
        val soft = engine.apply(start, GameCommand.SOFT_DROP)
        assertEquals(1, soft.score)
        assertEquals(0, soft.generation)
        assertEquals(1, engine.apply(soft, GameCommand.TICK).generation)
        assertEquals(1, engine.apply(start, GameCommand.TICK).score)
    }

    /** Одновременное удаление 1–4 строк не зависит от уровня и сохраняет верхние блоки. */
    @Test fun clearsLinesAndUpdatesLevel() {
        for (count in 1..4) {
            val start = state(Piece(Tetromino.I, x = 2, y = 16, rotation = 1))
            val board = start.board.map { it.toMutableList() }
            for (y in 20 - count..19) for (x in 0..9) if (x != 4) board[y][x] = Tetromino.J
            board[10][0] = Tetromino.L
            val pending = engine.apply(start.copy(board = board, lines = 9), GameCommand.TICK)
            assertEquals((20 - count..19).toList(), pending.clearingRows)
            assertEquals(9, pending.lines)
            assertEquals(0, pending.score)
            assertEquals(0, pending.generation)
            assertEquals(start.next, pending.next)
            GameCommand.entries.forEach { assertEquals(pending, engine.apply(pending, it)) }
            val result = engine.finishClear(pending)
            assertTrue(result.clearingRows.isEmpty())
            assertEquals(1, result.generation)
            assertEquals(result, engine.finishClear(result))
            assertEquals(9 + count, result.lines)
            assertEquals(listOf(0, 100, 300, 700, 1500)[count], result.score)
            assertEquals(if (count == 4) 2 else 1, result.level)
            assertEquals(Tetromino.L, result.board[10 + count][0])
        }
    }

    /** Заблокированное место появления следующей фигуры завершает партию. */
    @Test fun blockedSpawnEndsGame() {
        val start = state(Piece(Tetromino.O, x = 0, y = 18))
        val board = start.board.map { it.toMutableList() }
        board[0][4] = Tetromino.Z
        val result = engine.apply(start.copy(board = board), GameCommand.TICK)
        assertTrue(result.gameOver)
        assertEquals(result, engine.apply(result, GameCommand.HARD_DROP))
    }

    /** Каждый последовательный набор содержит все семь фигур ровно по одному разу. */
    @Test fun generatorUsesSevenBags() {
        var current = engine.newGame()
        repeat(3) {
            val types = mutableListOf<Tetromino>()
            repeat(7) {
                types += current.active.type
                current = engine.apply(current.copy(board = List(20) { List(10) { null } }), GameCommand.HARD_DROP)
            }
            assertEquals(Tetromino.entries.toSet(), types.toSet())
        }
    }

    /** Скорость растёт по уровням, но не становится быстрее установленного минимума. */
    @Test fun gravityHasMinimumInterval() {
        assertEquals(800L, state().gravityMillis)
        assertEquals(680L, state().copy(score = 1000).gravityMillis)
        assertEquals(100L, state().copy(score = 1000000).gravityMillis)
    }
}


