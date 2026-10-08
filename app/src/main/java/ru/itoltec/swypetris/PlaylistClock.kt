package ru.itoltec.swypetris

import kotlin.random.Random

private const val TRACK_GAP_MILLIS = 1500L


/** Android-independent clock for track gaps that advances only while playback is allowed. */
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

    /** Starts a selected track or a fresh shuffled queue from the beginning. */
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

    /** Completing a track starts exactly one 1.5-second gap. */
    fun completed() {
        if (remainingGap != null) return
        remainingGap = TRACK_GAP_MILLIS
        lastTime = clock()
    }

    /** Freezes or resumes the remaining gap without spending time in the background. */
    fun setActive(value: Boolean) {
        advance()
        active = value
        lastTime = clock()
    }

    /** Advances the clock and returns true exactly when the next track should start. */
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

    /** Each round begins with Korobeiniki; other tracks play once in random order. */
    private fun newRound(): List<Int> {
        require(count > 0)
        return listOf(0) + (1 until count).shuffled(random)
    }
}
