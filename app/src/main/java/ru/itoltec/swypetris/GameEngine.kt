package ru.itoltec.swypetris



import kotlin.random.Random

/** Координаты клетки: x растёт вправо, y вниз; начало поля находится слева сверху. */
data class Cell(val x: Int, val y: Int)

/** Семь фигур с исходными клетками [shape] внутри квадрата вращения размером [box]. */
enum class Tetromino(val shape: List<Cell>, val box: Int = 3) {
    I(listOf(Cell(0, 1), Cell(1, 1), Cell(2, 1), Cell(3, 1)), 4),
    O(listOf(Cell(0, 0), Cell(1, 0), Cell(0, 1), Cell(1, 1)), 2),
    T(listOf(Cell(1, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1))),
    S(listOf(Cell(1, 0), Cell(2, 0), Cell(0, 1), Cell(1, 1))),
    Z(listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(2, 1))),
    J(listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1))),
    L(listOf(Cell(2, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1)))
}

/** Активная фигура: положение квадрата вращения и число поворотов по часовой от 0 до 3. */
data class Piece(val type: Tetromino, val x: Int = (10 - type.box) / 2, val y: Int = 0, val rotation: Int = 0) {
    /** Переводит исходную форму в координаты поля с учётом положения и вращения. */
    fun cells(): List<Cell> = type.shape.map { original ->
        var cell = original
        if (type != Tetromino.O) repeat(rotation) { cell = Cell(type.box - 1 - cell.y, cell.x) }
        Cell(cell.x + x, cell.y + y)
    }
}

/**
 * Неизменяемый снимок партии; [generation] меняется при появлении следующей фигуры.
 * Непустой [clearingRows] означает, что [active] уже зафиксирована и новые команды заблокированы.
 * [accelerated] запоминает успешный мягкий спуск до появления следующей фигуры.
 */
data class GameState(
    val board: List<List<Tetromino?>> = List(20) { List(10) { null } },
    val active: Piece,
    val next: Tetromino,
    val score: Int = 0,
    val lines: Int = 0,
    val generation: Int = 0,
    val gameOver: Boolean = false,
    val clearingRows: List<Int> = emptyList(),
    val completedClears: Int = 0,
    val accelerated: Boolean = false,
    val completedRounds: Int = 0,
    val victoryPending: Boolean = false
) {
    /** Число заработанных фруктов текущего круга, включая полный набор на поздравлении. */
    val roundFruits: Int get() = (score / GameRules.FRUIT_STEP - completedRounds * 8).coerceIn(0, 8)
    /** Уровень определяется итоговым счётом по общим правилам. */
    val level: Int get() = GameRules.level(score)
    /** Интервал обычного спуска: от 800 мс на первом уровне до минимальных 100 мс. */
    val gravityMillis: Long get() = GameRules.gravityMillis(level)
}

/** Команды движка; PAUSE обрабатывается моделью экрана, а не меняет клетки поля. */
enum class GameCommand { LEFT, RIGHT, CLOCKWISE, COUNTERCLOCKWISE, SOFT_DROP, HARD_DROP, TICK, PAUSE }

/** Правила тетриса без зависимостей Android; [random] можно фиксировать для тестов. */
class GameEngine(private val random: Random = Random.Default) {
    private val bag = ArrayDeque<Tetromino>()
    /** Берёт фигуру из перемешанного набора, пополняя его всеми семью типами. */
    private fun draw(): Tetromino {
        if (bag.isEmpty()) bag.addAll(Tetromino.entries.shuffled(random))
        return bag.removeFirst()
    }

    /** Создаёт пустое поле, первую фигуру и предварительный просмотр следующей. */
    fun newGame(): GameState {
        bag.clear()
        return GameState(active = Piece(draw()), next = draw())
    }

    /** Очищает поле после победы, сохраняя накопленные показатели и скорость. */
    fun nextRound(state: GameState): GameState {
        if (!state.victoryPending) return state
        val fresh = newGame()
        return fresh.copy(score = state.score, lines = state.lines,
            generation = state.generation + 1, completedClears = state.completedClears,
            completedRounds = state.completedRounds + 1)
    }

    /** Фиксирует победу после начисления очков; она имеет приоритет над блокировкой входа. */
    internal fun checkVictory(state: GameState): GameState =
        if (state.clearingRows.isEmpty() && state.roundFruits == 8)
            state.copy(victoryPending = true, gameOver = false) else state

    /** Проверяет, что все клетки фигуры находятся в поле и не заняты другими блоками. */
    fun fits(state: GameState, piece: Piece): Boolean = piece.cells().all {
        it.x in 0..9 && it.y in 0..19 && state.board[it.y][it.x] == null
    }

    /** Находит нижнее достижимое положение без изменения поля и начисления очков. */
    fun ghost(state: GameState): Piece {
        var piece = state.active
        while (fits(state, piece.copy(y = piece.y + 1))) piece = piece.copy(y = piece.y + 1)
        return piece
    }

    /** Возвращает результат команды; запрещённое движение и команды после проигрыша игнорируются. */
    fun apply(state: GameState, command: GameCommand): GameState {
        if (state.gameOver || state.victoryPending || state.clearingRows.isNotEmpty()) return state
        val piece = state.active
        return checkVictory(when (command) {
            GameCommand.LEFT, GameCommand.RIGHT -> {
                val moved = piece.copy(x = piece.x + if (command == GameCommand.LEFT) -1 else 1)
                if (fits(state, moved)) state.copy(active = moved) else state
            }
            GameCommand.CLOCKWISE, GameCommand.COUNTERCLOCKWISE -> {
                if (piece.type == Tetromino.O) state else {
                    val rotated = piece.copy(rotation = (piece.rotation + if (command == GameCommand.CLOCKWISE) 1 else 3) % 4)
                    val valid = listOf(0, -1, 1, -2, 2).map { rotated.copy(x = rotated.x + it) }.firstOrNull { fits(state, it) }
                    if (valid == null) state else state.copy(active = valid)
                }
            }
            GameCommand.TICK, GameCommand.SOFT_DROP -> {
                val moved = piece.copy(y = piece.y + 1)
                if (fits(state, moved)) state.copy(active = moved, score = GameRules.add(state.score, 1),
                    accelerated = state.accelerated || command == GameCommand.SOFT_DROP)
                else lock(state)
            }
            GameCommand.HARD_DROP -> {
                val landed = ghost(state)
                lock(state.copy(active = landed, score = GameRules.add(state.score, landed.y - piece.y)))
            }
            GameCommand.PAUSE -> state
        })
    }

    /** Фиксирует блоки; полные строки остаются на поле до завершения последовательного удаления. */
    private fun lock(state: GameState): GameState {
        val board = state.board.map { it.toMutableList() }
        state.active.cells().forEach { board[it.y][it.x] = state.active.type }
        val rows = board.indices.filter { y -> board[y].all { it != null } }
        val locked = state.copy(board = board, clearingRows = rows)
        return if (rows.isEmpty()) spawnNext(locked) else locked
    }

    /** Удаляет отмеченные строки и начисляет очки ровно один раз после анимации. */
    fun finishClear(state: GameState): GameState {
        if (state.clearingRows.isEmpty()) return state
        val cleared = state.clearingRows.size
        val remaining = state.board.filterIndexed { index, _ -> index !in state.clearingRows }
        return checkVictory(spawnNext(state.copy(
            board = List(cleared) { List<Tetromino?>(10) { null } } + remaining,
            score = GameRules.add(state.score, GameRules.lineScore(cleared)),
            lines = state.lines + cleared, clearingRows = emptyList(), completedClears = state.completedClears + 1
        )))
    }

    /** Создаёт следующую фигуру только после фиксации без линий либо завершения очистки. */
    private fun spawnNext(state: GameState): GameState {
        val nextState = state.copy(active = Piece(state.next), next = draw(), generation = state.generation + 1, accelerated = false)
        return nextState.copy(gameOver = !fits(nextState, nextState.active))
    }
}



