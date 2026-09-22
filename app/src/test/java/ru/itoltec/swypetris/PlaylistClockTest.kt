package ru.itoltec.swypetris

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Проверяет межтрековые паузы независимо от длительности реальных записей. */
class PlaylistClockTest {
    /** Каждый круг содержит все восемь песен без повторов, включая границы кругов. */
    @Test fun shuffledBagsAndExactGaps() {
        var now = 0L
        val clock = PlaylistClock(8, Random(92)) { now }
        clock.setActive(true)
        val heard = mutableListOf<Int>()
        repeat(80) {
            heard += clock.index
            clock.completed()
            now += 1499
            assertFalse(clock.advance())
            assertEquals(1L, clock.remainingGap)
            now++
            assertTrue(clock.advance())
            assertNull(clock.remainingGap)
            assertFalse(clock.advance())
        }
        heard.chunked(8).forEach {
            assertEquals(0, it.first())
            assertEquals((0..7).toSet(), it.toSet())
        }
        heard.zipWithNext().forEach { (a,b) -> assertNotEquals(a,b) }
        assertTrue(heard.chunked(8).distinct().size > 1)
    }

    /** Пауза, фанфары и потеря фокуса не расходуют оставшееся время тишины. */
    @Test fun pauseDuringGapAndRepeatedCompletion() {
        var now = 1000L
        val clock = PlaylistClock(8) { now }
        val first = clock.index
        clock.setActive(true)
        clock.completed()
        now += 600
        clock.setActive(false)
        assertEquals(900L, clock.remainingGap)
        now += 60000
        clock.completed()
        clock.advance()
        assertEquals(900L, clock.remainingGap)
        clock.setActive(true)
        now += 899
        assertFalse(clock.advance())
        now++
        assertTrue(clock.advance())
        assertNotEquals(first, clock.index)
    }

    /** Одна песня получает новое воспроизведение после паузы; смена режима сбрасывает паузу. */
    @Test fun singleTrackRepeatsAndSelectionResets() {
        var now = 0L
        val clock = PlaylistClock(8, Random(4)) { now }
        clock.select(6)
        clock.setActive(true)
        repeat(3) {
            val occurrence = clock.occurrence
            clock.completed(); now += 1500; assertTrue(clock.advance())
            assertEquals(6, clock.index)
            assertEquals(occurrence+1, clock.occurrence)
        }
        clock.completed(); now += 700; clock.advance()
        clock.select(3)
        assertNull(clock.remainingGap)
        assertEquals(3, clock.index)
        clock.select(null)
        assertEquals(0, clock.index)
        val heard = mutableSetOf<Int>()
        repeat(8) { heard += clock.index; clock.completed(); now += 1500; clock.advance() }
        assertEquals(8, heard.size)
    }

    /** Старая галка сохраняет смысл, а новая настройка имеет приоритет. */
    @Test fun legacyMusicSelection() {
        assertEquals(MusicSelection.Off, MusicSelection.restore(null,false))
        assertEquals(MusicSelection.ShuffleAll, MusicSelection.restore(null,true))
        assertEquals(MusicSelection.Track(Song.TREPAK), MusicSelection.restore("trepak",false))
        assertEquals(10, MusicSelection.all.size)
        assertEquals(10, MusicSelection.all.map { it.id }.toSet().size)
    }
}
