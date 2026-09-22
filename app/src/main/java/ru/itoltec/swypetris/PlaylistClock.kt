package ru.itoltec.swypetris

import kotlin.random.Random

/** Часы межтрековой паузы без Android: учитывают только разрешённое воспроизведение. */
internal class PlaylistClock(private val count: Int, private val random: Random = Random.Default,
    private val clock: () -> Long) {
    private var queue = newRound()
    private var position = 0
    private var single: Int? = null
    var index = queue.first()
        private set
    var occurrence = 0L
        private set
    var remainingGap: Long? = null
        private set
    private var active = false
    private var lastTime = clock()

    init { require(count > 0) }

    /** Начинает выбранную песню либо новую случайную очередь с нулевой позиции. */
    fun select(track: Int?) {
        require(track == null || track in 0 until count)
        single = track
        queue = newRound()
        position = 0
        index = track ?: queue.first()
        occurrence++
        remainingGap = null
        lastTime = clock()
    }

    /** Завершение композиции начинает единственную паузу 1,5 секунды. */
    fun completed() {
        if (remainingGap != null) return
        remainingGap = 1500L
        lastTime = clock()
    }

    /** Замораживает или продолжает остаток паузы, не расходуя время в фоне. */
    fun setActive(value: Boolean) {
        advance()
        active = value
        lastTime = clock()
    }

    /** Продвигает часы и возвращает true ровно при переходе к следующей композиции. */
    fun advance(): Boolean {
        val now = clock()
        val gap = remainingGap
        val next = if (active && gap != null) (gap - (now - lastTime).coerceAtLeast(0)).coerceAtLeast(0) else gap
        lastTime = now
        remainingGap = next
        if (next == 0L) {
            if (single != null) index = single!! else {
                position++
                if (position == count) {
                    queue = newRound()
                    position = 0
                }
                index = queue[position]
            }
            occurrence++
            remainingGap = null
            return true
        }
        return false
    }

    /** Каждый круг открывают «Коробейники»; остальные песни идут без повторов в случайном порядке. */
    private fun newRound(): List<Int> {
        require(count > 0)
        return listOf(0) + (1 until count).shuffled(random)
    }
}
