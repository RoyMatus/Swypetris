package ru.itoltec.swypetris

internal data class MenuMotionFrame(val u: Double, val x: Float, val y: Float,
    val scaleX: Float, val scaleY: Float, val rotation: Float, val alpha: Float, val index: Int)
internal data class MenuFrameEvent(val period: Double, val start: Double, val duration: Double, val index: Int)
internal data class MenuMotionTrack(val name: String, val kind: String, val color: Long,
    val width: Float, val height: Float, val period: Double, val phase: Double,
    val frames: List<MenuMotionFrame>, val stroke: Float? = null, val glow: Boolean = false,
    val bevel: Boolean = false, val sprite: String? = null, val tint: Boolean = false,
    val framePeriod: Double? = null, val frameEvents: List<MenuFrameEvent> = emptyList())

/** Continuous authored geometry with discrete atlas poses and ordered timed overrides. */
internal fun sampleMenuMotion(track: MenuMotionTrack, time: Double): MenuMotionFrame {
    val u = positiveMenuModulo(time + track.phase, track.period) / track.period
    val next = track.frames.indexOfFirst { it.u > u }.coerceAtLeast(1)
    val a = track.frames[next - 1]
    val b = track.frames[next]
    val fraction = ((u - a.u) / (b.u - a.u)).toFloat()
    fun lerp(start: Float, end: Float) = start + (end - start) * fraction
    var index = track.framePeriod?.let {
        kotlin.math.floor(positiveMenuModulo(time, it) / it * 4).toInt()
    } ?: a.index
    track.frameEvents.forEach { event ->
        val phase = positiveMenuModulo(time, event.period)
        if (phase >= event.start && phase < event.start + event.duration) index = event.index
    }
    return MenuMotionFrame(u, lerp(a.x, b.x), lerp(a.y, b.y), lerp(a.scaleX, b.scaleX),
        lerp(a.scaleY, b.scaleY), lerp(a.rotation, b.rotation), lerp(a.alpha, b.alpha).coerceIn(0f, 1f), index)
}

private fun positiveMenuModulo(value: Double, period: Double): Double = ((value % period) + period) % period
