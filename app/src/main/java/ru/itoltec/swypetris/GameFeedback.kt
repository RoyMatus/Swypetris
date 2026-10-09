package ru.itoltec.swypetris

/** Game events that can trigger short sound and vibration effects. */
enum class FeedbackEvent { DROP, CLEAR }

/** Selects one transition effect; a line clear takes priority over a drop. */
fun feedbackEvent(before: GameState, after: GameState, command: GameCommand): FeedbackEvent? = when {
    before.clearingRows.isEmpty() && after.clearingRows.isNotEmpty() -> FeedbackEvent.CLEAR
    command != GameCommand.HOLD && before.clearingRows.isEmpty() && (command == GameCommand.HARD_DROP ||
        before.accelerated) && after.generation != before.generation -> FeedbackEvent.DROP
    else -> null
}

/** Boundary between game rules and device effects; replaceable with a recorder in tests. */
interface GameFeedback {
    /** Applies the global user intensity to every motor effect, including future effects. */
    fun setVibrationStrength(percent: Int) = Unit
    /** Plays only the sound and vibration enabled by user settings. */
    fun play(event: FeedbackEvent, sound: Boolean, vibration: Boolean)
    /** Resumes only the remaining clear vibration without replaying the sound. */
    fun resumeClear(remainingMillis: Long, vibration: Boolean) = Unit
    /** Tests the motor with one pulse when vibration is explicitly enabled. */
    fun previewVibration() = Unit
    fun holdReady() = Unit
    /** Stops vibration without muting any permitted sound. */
    fun stopVibration() = Unit
    /** Stops sound effects without cancelling vibration. */
    fun stopSound() = Unit
    /** Stops effects without scheduling a resume. */
    fun stop()
    /** Releases owned device resources when the model is destroyed. */
    fun release()
}

/** No-op implementation for rule and animation tests. */
object SilentFeedback : GameFeedback {
    /** Does not access the device. */
    override fun play(event: FeedbackEvent, sound: Boolean, vibration: Boolean) = Unit
    /** Owns no active effect to stop. */
    override fun stop() = Unit
    /** Owns no resources to release. */
    override fun release() = Unit
}
