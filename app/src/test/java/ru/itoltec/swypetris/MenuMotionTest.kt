package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuMotionTest {
    private fun track(phase: Double = 0.0, framePeriod: Double? = null,
        events: List<MenuFrameEvent> = emptyList()) = MenuMotionTrack("test", "sprite", 0xffffffff,
        10f, 10f, 10.0, phase, listOf(
            MenuMotionFrame(0.0, 0f, 10f, 1f, 2f, 0f, 0f, 1),
            MenuMotionFrame(0.5, 100f, 20f, 2f, 3f, 90f, 1f, 2),
            MenuMotionFrame(1.0, 0f, 10f, 1f, 2f, 0f, 0f, 1)),
        framePeriod = framePeriod, frameEvents = events)

    @Test fun interpolatesContinuousCoordinatesAndKeepsAtlasDiscrete() {
        val pose = sampleMenuMotion(track(), 2.5)
        assertEquals(50f, pose.x, .0001f)
        assertEquals(15f, pose.y, .0001f)
        assertEquals(1.5f, pose.scaleX, .0001f)
        assertEquals(2.5f, pose.scaleY, .0001f)
        assertEquals(45f, pose.rotation, .0001f)
        assertEquals(.5f, pose.alpha, .0001f)
        assertEquals(1, pose.index)
    }

    @Test fun wrapsNegativePhaseAndPeriodBoundaryDeterministically() {
        assertEquals(50f, sampleMenuMotion(track(-2.5), 0.0).x, .0001f)
        assertEquals(sampleMenuMotion(track(), 0.0), sampleMenuMotion(track(), 10.0))
    }

    @Test fun atlasCycleAndOrderedEventsOverrideOnlyThePoseIndex() {
        val motion = track(framePeriod = .36, events = listOf(
            MenuFrameEvent(23.0, 3.0, .35, 2), MenuFrameEvent(29.0, 14.0, .55, 3)))
        assertEquals(3, sampleMenuMotion(motion, .3).index)
        assertEquals(2, sampleMenuMotion(motion, 3.1).index)
        assertEquals(3, sampleMenuMotion(motion, 14.2).index)
        assertEquals(sampleMenuMotion(track(), 3.1).x, sampleMenuMotion(motion, 3.1).x, .0001f)
        assertEquals(1, sampleMenuMotion(motion, 3.35).index)
    }
}
