package ru.itoltec.swypetris

/** Historical result metadata only; new games use the single Marathon curve. */
enum class Difficulty(val id: String, val title: String) {
    EASY("easy", "Лёгкая"), MEDIUM("medium", "Средняя"), HARD("hard", "Сложная");

    companion object {
        fun find(id: String?): Difficulty? = entries.firstOrNull { it.id == id }
    }
}
