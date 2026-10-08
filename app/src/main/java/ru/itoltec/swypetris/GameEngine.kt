package ru.itoltec.swypetris



import kotlin.random.Random

/** Full logical matrix uses hidden rows first; piece coordinates retain visible y=0..19. */
object BoardGeometry {
    const val WIDTH = 10
    const val VISIBLE_ROWS = 20
    const val HIDDEN_ROWS = 20
    const val TOTAL_ROWS = VISIBLE_ROWS + HIDDEN_ROWS
    fun row(y: Int): Int = y + HIDDEN_ROWS
    fun empty(): List<List<Tetromino?>> = List(TOTAL_ROWS) { List(WIDTH) { null } }
}

enum class TopOut { BLOCK_OUT, LOCK_OUT }

object LockRules {
    const val DELAY_MILLIS = 500L
    const val MAX_RESETS = 15
}

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
/** I uses the second hidden row because its spawn shape occupies rotation-box row one. */
internal fun spawnPiece(type: Tetromino): Piece = Piece(type, y = if (type == Tetromino.I) -2 else -1)

data class Piece(val type: Tetromino, val x: Int = (10 - type.box) / 2, val y: Int = 0, val rotation: Int = 0) {
    val orientation: RotationState get() = RotationState.entries[rotation]
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
    val board: List<List<Tetromino?>> = BoardGeometry.empty(),
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
    val topOut: TopOut? = null,
    val lockRemaining: Long = LockRules.DELAY_MILLIS,
    val lockResets: Int = 0,
    val held: Tetromino? = null,
    val holdUsed: Boolean = false,
    val lastRotationKick: Int = -1,
    val softDropCells: Int = 0,
    val hardDropCells: Int = 0,
    val backToBack: Boolean = false,
    val combo: Int = -1,
    val placement: PlacementResult? = null,
    val startingLevel: Int = 1
) {
    init { require(startingLevel in GameRules.MIN_STARTING_LEVEL..GameRules.MAX_STARTING_LEVEL) }
    /** Number of fruits earned in this round, including the complete set on the victory screen. */
    val roundFruits: Int get() = (score / GameRules.FRUIT_STEP - completedRounds * Fruit.entries.size).coerceIn(0,
        Fruit.entries.size)
    /** Cumulative collected quantity for each fruit in display order across all completed/current rounds. */
    val fruitCounts: List<Int> get() {
        val awards = score / GameRules.FRUIT_STEP
        val cycle = Fruit.entries.size
        return Fruit.entries.indices.map { index ->
            if (awards <= index) 0 else ((awards - 1 - index) / cycle) + 1
        }
    }
    /** Derives the current level from cleared lines using the shared rules. */
    val level: Int get() = GameRules.level(lines, startingLevel)
    val gravityNanos: Long get() = GameRules.gravityNanos(level, startingLevel)
    val gravityMillis: Long get() = GameRules.gravityMillis(level, startingLevel)
}

/** Engine commands; PAUSE is handled by the screen model and does not change board cells. */
enum class GameCommand { LEFT, RIGHT, CLOCKWISE, COUNTERCLOCKWISE, SOFT_DROP, HARD_DROP, HOLD, TICK, PAUSE }

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

    /** Creates a tetromino at the shared logical spawn position. */
    private fun spawn(type: Tetromino): Piece = spawnPiece(type)

    /** Creates an empty board, its first piece, and the next-piece preview. */
    fun newGame(startingLevel: Int = 1): GameState {
        bag.clear()
        return GameState(active = spawn(draw()), next = draw(), startingLevel = startingLevel)
    }

    /** Clears the board after victory while keeping score and speed. */
    fun nextRound(state: GameState): GameState {
        if (!state.victoryPending) return state
        val fresh = newGame(state.startingLevel)
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
        it.x in 0 until BoardGeometry.WIDTH && BoardGeometry.row(it.y) in state.board.indices &&
            state.board[BoardGeometry.row(it.y)][it.x] == null
    }

    /** Finds the lowest reachable position without changing the board or awarding points. */
    fun ghost(state: GameState): Piece {
        var piece = state.active
        while (fits(state, piece.copy(y = piece.y + 1))) piece = piece.copy(y = piece.y + 1)
        return piece
    }

    fun grounded(state: GameState): Boolean = !fits(state, state.active.copy(y = state.active.y + 1))

    /** Airborne time never consumes the clock or replenishes its reset budget. */
    fun advanceLock(state: GameState, elapsed: Long, allowLock: Boolean = true): GameState {
        if (state.gameOver || state.victoryPending || state.clearingRows.isNotEmpty() || !grounded(state)) return state
        val timed = state.copy(lockRemaining = (state.lockRemaining - elapsed.coerceAtLeast(0)).coerceAtLeast(0))
        return if (allowLock && timed.lockRemaining == 0L) checkVictory(lock(timed)) else timed
    }

    private fun moved(state: GameState, piece: Piece): GameState {
        val reset = grounded(state) && state.lockResets < LockRules.MAX_RESETS
        return state.copy(active = piece,
            lockRemaining = if (reset) LockRules.DELAY_MILLIS else state.lockRemaining,
            lockResets = state.lockResets + if (reset) 1 else 0)
    }

    /** Applies one command; invalid movement and commands after loss leave state unchanged. */
    fun apply(state: GameState, command: GameCommand): GameState {
        if (state.gameOver || state.victoryPending || state.clearingRows.isNotEmpty()) return state
        val piece = state.active
        return checkVictory(when (command) {
            GameCommand.LEFT, GameCommand.RIGHT -> {
                val moved = piece.copy(x = piece.x + if (command == GameCommand.LEFT) -1 else 1)
                if (fits(state, moved)) moved(state, moved).copy(lastRotationKick = -1) else state
            }
            GameCommand.CLOCKWISE, GameCommand.COUNTERCLOCKWISE -> {
                val target = piece.orientation.turn(command == GameCommand.CLOCKWISE)
                val rotated = piece.copy(rotation = target.value)
                val valid = Srs.kicks(piece.type, piece.orientation, target)
                    .withIndex().firstOrNull { (_, offset) ->
                        fits(state, rotated.copy(x = rotated.x + offset.x, y = rotated.y + offset.y))
                    }
                if (valid == null) state else moved(state,
                    rotated.copy(x = rotated.x + valid.value.x, y = rotated.y + valid.value.y))
                    .copy(lastRotationKick = valid.index)
            }
            GameCommand.TICK, GameCommand.SOFT_DROP -> {
                val moved = piece.copy(y = piece.y + 1)
                if (fits(state, moved)) state.copy(active = moved, score = GameRules.add(state.score,
                    GameRules.dropScore(command, 1)),
                    accelerated = state.accelerated || command == GameCommand.SOFT_DROP,
                    lastRotationKick = -1,
                    softDropCells = state.softDropCells + if (command == GameCommand.SOFT_DROP) 1 else 0)
                else state
            }
            GameCommand.HARD_DROP -> {
                val landed = ghost(state)
                val distance = landed.y - piece.y
                lock(state.copy(active = landed, score = GameRules.add(state.score, GameRules.dropScore(command,
                    distance)),
                    hardDropCells = distance, lastRotationKick = if (distance == 0) state.lastRotationKick else -1))
            }
            GameCommand.HOLD -> {
                if (state.holdUsed) state else {
                    val swapped = state.copy(active = spawn(state.held ?: state.next), held = piece.type,
                        next = if (state.held == null) draw() else state.next, holdUsed = true,
                        generation = state.generation + 1, accelerated = false,
                        lockRemaining = LockRules.DELAY_MILLIS, lockResets = 0,
                        lastRotationKick = -1, softDropCells = 0, hardDropCells = 0)
                    val blocked = !fits(swapped, swapped.active)
                    swapped.copy(gameOver = blocked, topOut = if (blocked) TopOut.BLOCK_OUT else null)
                }
            }
            GameCommand.PAUSE -> state
        })
    }

    /** Locks piece cells; complete rows remain until their removal animation finishes. */
    private fun lock(state: GameState): GameState {
        val board = state.board.map { it.toMutableList() }
        state.active.cells().forEach { board[BoardGeometry.row(it.y)][it.x] = state.active.type }
        val rows = board.indices.filter { y -> board[y].all { it != null } }
        // Complete lock-out: all four cells are above the visible field. Partial lock-out is allowed.
        if (state.active.cells().all { it.y < 0 })
            return state.copy(board = board, gameOver = true, topOut = TopOut.LOCK_OUT)
        val spin = SpinRecognition.classify(state)
        val combo = if (rows.isEmpty()) -1 else (state.combo.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val difficult = rows.isNotEmpty() && (rows.size == 4 || spin != Spin.NONE)
        val event = PlacementResult(rows.size, spin, difficult && state.backToBack, combo,
            softDropCells = state.softDropCells, hardDropCells = state.hardDropCells, level = state.level)
        val locked = state.copy(board = board, clearingRows = rows, combo = combo, placement = event,
            backToBack = if (rows.isEmpty()) state.backToBack else difficult,
            score = if (rows.isEmpty()) GameRules.add(state.score, GameRules.placementScore(event)) else state.score)
        return if (rows.isEmpty()) spawnNext(locked) else locked
    }

    /** Removes marked rows and awards points exactly once after the animation. */
    fun finishClear(state: GameState): GameState {
        if (state.clearingRows.isEmpty()) return state
        val cleared = state.clearingRows.size
        val remaining = state.board.filterIndexed { index, _ -> index !in state.clearingRows }
        val board = List(cleared) { List<Tetromino?>(10) { null } } + remaining
        val event = (state.placement ?: PlacementResult(cleared, Spin.NONE, false, state.combo, level = state.level))
            .copy(perfectClear = board.all { row -> row.all { it == null } })
        return checkVictory(spawnNext(state.copy(
            board = board, placement = event,
            score = GameRules.add(state.score, GameRules.placementScore(event)),
            lines = GameRules.add(state.lines, cleared), clearingRows = emptyList(),
                completedClears = state.completedClears + 1
        )))
    }

    /** Spawns the next piece after locking without a clear or once a clear finishes. */
    private fun spawnNext(state: GameState): GameState {
        val nextState = state.copy(active = spawn(state.next), next = draw(), generation = state.generation + 1,
            accelerated = false, lockRemaining = LockRules.DELAY_MILLIS, lockResets = 0, holdUsed = false,
            lastRotationKick = -1, softDropCells = 0, hardDropCells = 0)
        val blocked = !fits(nextState, nextState.active)
        return nextState.copy(gameOver = blocked, topOut = if (blocked) TopOut.BLOCK_OUT else null)
    }
}
