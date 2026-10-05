package ru.itoltec.swypetris

/** Persisted orientation values remain 0, 1, 2, 3 for 0, R, 2, L. */
enum class RotationState(val value: Int) {
    SPAWN(0), RIGHT(1), REVERSE(2), LEFT(3);
    fun turn(clockwise: Boolean): RotationState = entries[(value + if (clockwise) 1 else 3) % 4]
}

/** Ordered SRS tests in board coordinates: positive y points downward. */
internal object Srs {
    private fun offsets(vararg xy: Pair<Int, Int>) = xy.map { Cell(it.first, it.second) }
    private val jlstz = mapOf(
        (0 to 1) to offsets(0 to 0, -1 to 0, -1 to -1, 0 to 2, -1 to 2),
        (1 to 0) to offsets(0 to 0, 1 to 0, 1 to 1, 0 to -2, 1 to -2),
        (1 to 2) to offsets(0 to 0, 1 to 0, 1 to 1, 0 to -2, 1 to -2),
        (2 to 1) to offsets(0 to 0, -1 to 0, -1 to -1, 0 to 2, -1 to 2),
        (2 to 3) to offsets(0 to 0, 1 to 0, 1 to -1, 0 to 2, 1 to 2),
        (3 to 2) to offsets(0 to 0, -1 to 0, -1 to 1, 0 to -2, -1 to -2),
        (3 to 0) to offsets(0 to 0, -1 to 0, -1 to 1, 0 to -2, -1 to -2),
        (0 to 3) to offsets(0 to 0, 1 to 0, 1 to -1, 0 to 2, 1 to 2)
    )
    private val i = mapOf(
        (0 to 1) to offsets(0 to 0, -2 to 0, 1 to 0, -2 to 1, 1 to -2),
        (1 to 0) to offsets(0 to 0, 2 to 0, -1 to 0, 2 to -1, -1 to 2),
        (1 to 2) to offsets(0 to 0, -1 to 0, 2 to 0, -1 to -2, 2 to 1),
        (2 to 1) to offsets(0 to 0, 1 to 0, -2 to 0, 1 to 2, -2 to -1),
        (2 to 3) to offsets(0 to 0, 2 to 0, -1 to 0, 2 to -1, -1 to 2),
        (3 to 2) to offsets(0 to 0, -2 to 0, 1 to 0, -2 to 1, 1 to -2),
        (3 to 0) to offsets(0 to 0, 1 to 0, -2 to 0, 1 to 2, -2 to -1),
        (0 to 3) to offsets(0 to 0, -1 to 0, 2 to 0, -1 to -2, 2 to 1)
    )
    fun kicks(type: Tetromino, from: RotationState, to: RotationState): List<Cell> = when (type) {
        Tetromino.O -> listOf(Cell(0, 0))
        Tetromino.I -> i.getValue(from.value to to.value)
        else -> jlstz.getValue(from.value to to.value)
    }
}
