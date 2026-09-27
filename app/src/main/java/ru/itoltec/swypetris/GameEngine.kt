package ru.itoltec.swypetris



import kotlin.random.Random

/** Board cell coordinates: x increases rightward and y downward from the upper-left corner. */
data class Cell(val x: Int, val y: Int)

/** The seven tetrominoes define [shape] inside a [box]-wide rotation square. */
enum class Tetromino(val shape: List<Cell>, val box: Int = 3) {
    I(listOf(Cell(0, 1), Cell(1, 1), Cell(2, 1), Cell(3, 1)), 4),
    O(listOf(Cell(0, 0), Cell(1, 0), Cell(0, 1), Cell(1, 1)), 2),
    T(listOf(Cell(1, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1))),
    S(listOf(Cell(1, 0), Cell(2, 0), Cell(0, 1), Cell(1, 1))),
    Z(listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(2, 1))),
    J(listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1))),
    L(listOf(Cell(2, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1)))
}

/** An active tetromino stores its rotation-square position and clockwise rotation count (0–3). */
data class Piece(val type: Tetromino, val x: Int = (10 - type.box) / 2, val y: Int = 0, val rotation: Int = 0) {
    /** Transforms the original shape into board coordinates using the piece position and rotation. */
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
    val victoryPending: Boolean = false,
    val difficulty: Difficulty = Difficulty.MEDIUM
) {
    /** Number of fruits earned in this round, including the complete set on the victory screen. */
    val roundFruits: Int get() = (score / GameRules.FRUIT_STEP - completedRounds * 8).coerceIn(0, 8)
    /** Derives the current level from the total score using the shared rules. */
    val level: Int get() = GameRules.level(score)
    /** Gravity uses the difficulty selected when this game began. */
    val gravityMillis: Long get() = GameRules.gravityMillis(level, difficulty)
}

/** Engine commands; PAUSE is handled by the screen model and does not change board cells. */
enum class GameCommand { LEFT, RIGHT, CLOCKWISE, COUNTERCLOCKWISE, SOFT_DROP, HARD_DROP, TICK, PAUSE }

/** Android-independent Tetris rules; inject [random] for reproducible pieces in tests. */
class GameEngine(private val random: Random = Random.Default) {
    private val bag = ArrayDeque<Tetromino>()
    /** Snapshots the unconsumed seven-bag order for a resumable session. */
    internal fun remainingBag(): List<Tetromino> = bag.toList()
    /** Restores the unconsumed seven-bag order without drawing another piece. */
    internal fun restoreBag(remaining: List<Tetromino>) {
        require(remaining.size <= Tetromino.entries.size && remaining.distinct().size == remaining.size)
        bag.clear()
        bag.addAll(remaining)
    }
    /** Draws from a shuffled bag and refills it with all seven types when empty. */
    private fun draw(): Tetromino {
        if (bag.isEmpty()) bag.addAll(Tetromino.entries.shuffled(random))
        return bag.removeFirst()
    }

    /** Creates an empty board, its first piece, and the next-piece preview. */
    fun newGame(difficulty: Difficulty = Difficulty.MEDIUM): GameState {
        bag.clear()
        return GameState(active = Piece(draw()), next = draw(), difficulty = difficulty)
    }

    /** Clears the board after victory while keeping score and speed. */
    fun nextRound(state: GameState): GameState {
        if (!state.victoryPending) return state
        val fresh = newGame(state.difficulty)
        return fresh.copy(score = state.score, lines = state.lines,
            generation = state.generation + 1, completedClears = state.completedClears,
            completedRounds = state.completedRounds + 1)
    }

    /** Marks victory after scoring; victory takes precedence over spawn-blocked loss. */
    internal fun checkVictory(state: GameState): GameState =
        if (state.clearingRows.isEmpty() && state.roundFruits == 8)
            state.copy(victoryPending = true, gameOver = false) else state

    /** Checks that every cell of [piece] is inside the board and unoccupied. */
    fun fits(state: GameState, piece: Piece): Boolean = piece.cells().all {
        it.x in 0..9 && it.y in 0..19 && state.board[it.y][it.x] == null
    }

    /** Finds the lowest reachable position without changing the board or awarding points. */
    fun ghost(state: GameState): Piece {
        var piece = state.active
        while (fits(state, piece.copy(y = piece.y + 1))) piece = piece.copy(y = piece.y + 1)
        return piece
    }

    /** Applies one command; invalid movement and commands after loss leave state unchanged. */
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

    /** Locks piece cells; complete rows remain until their removal animation finishes. */
    private fun lock(state: GameState): GameState {
        val board = state.board.map { it.toMutableList() }
        state.active.cells().forEach { board[it.y][it.x] = state.active.type }
        val rows = board.indices.filter { y -> board[y].all { it != null } }
        val locked = state.copy(board = board, clearingRows = rows)
        return if (rows.isEmpty()) spawnNext(locked) else locked
    }

    /** Removes marked rows and awards points exactly once after the animation. */
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

    /** Spawns the next piece after locking without a clear or once a clear finishes. */
    private fun spawnNext(state: GameState): GameState {
        val nextState = state.copy(active = Piece(state.next), next = draw(), generation = state.generation + 1, accelerated = false)
        return nextState.copy(gameOver = !fits(nextState, nextState.active))
    }
}

