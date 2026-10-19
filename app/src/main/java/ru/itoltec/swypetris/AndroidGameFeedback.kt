package ru.itoltec.swypetris

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibrationAttributes
import java.util.concurrent.ConcurrentHashMap

/** Preloads soft WAV effects and plays them without changing system volume. */
class AndroidGameFeedback(private val context: Context) : GameFeedback {
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val vibrationAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).build()
    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(audioAttributes).build()
    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val streams = mutableListOf<Int>()
    @Suppress("DEPRECATION")
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val samples = mutableMapOf<ThemeSoundPair, Pair<Int, Int>>()
    private var selected = ThemeSounds.forPalette("classic")
    @Volatile private var released = false
    private var vibrationStrength = MAX_VIBRATION_STRENGTH

    override fun setVibrationStrength(percent: Int) {
        require(percent in 0..MAX_VIBRATION_STRENGTH)
        vibrationStrength = percent
        if (percent == 0) stopVibration()
    }

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0 && !released) loaded.add(id) }
        setPalette("classic")
    }

    /** Each visited pair loads once. A pending load never queues or replays an old event. */
    override fun setPalette(id: String) {
        if (released) return
        val next = ThemeSounds.forPalette(id)
        if (next != selected) stopSound()
        selected = next
        samples.getOrPut(next) { pool.load(context, next.drop, 1) to pool.load(context, next.clear, 1) }
    }

    /** Skips sounds not yet loaded and requests vibration with supported Android usage attributes. */
    @Suppress("DEPRECATION")
    override fun play(event: FeedbackEvent, sound: Boolean, vibration: Boolean) {
        if (released) return
        stop()
        val pair = samples.getValue(selected)
        val sample = if (event == FeedbackEvent.DROP) pair.first else pair.second
        if (sound && sample in loaded) {
            val volume = selected.volume(event)
            val stream = pool.play(sample, volume, volume, 1, 0, 1f)
            if (stream != 0) streams.add(stream)
        }
        vibrate(if (event == FeedbackEvent.DROP) HapticPulse.Drop else HapticPulse.Clear, vibration)
    }

    /** Resumes vibration only for the remaining line-clear animation. */
    override fun resumeClear(remainingMillis: Long, vibration: Boolean) {
        vibrate(HapticPulse.clear(remainingMillis), vibration)
    }

    /** Previews vibration when its setting is enabled, without playing sound. */
    override fun previewVibration() = vibrate(HapticPulse.Preview, true)
    override fun holdReady() = vibrate(HapticPulse.HoldReady, true)

    /** Chooses an amplitude supported by the device and respects system vibration settings. */
    @Suppress("DEPRECATION")
    private fun vibrate(pulse: HapticPulse, vibration: Boolean) {
        val motor = vibrator?.takeIf { vibration && it.hasVibrator() } ?: return
        val softened = pulse.softened().scaled(vibrationStrength) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitudeControl = motor.hasAmplitudeControl()
            val amplitude = if (amplitudeControl) softened.amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            val effect = VibrationEffect.createOneShot(softened.durationFor(amplitudeControl), amplitude)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) motor.vibrate(effect,
                VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_MEDIA).build())
            else motor.vibrate(effect, vibrationAttributes)
        } else {
            motor.vibrate(softened.durationFor(false), vibrationAttributes)
        }
    }

    /** Stops active sounds and vibrations; there is no playback queue. */
    override fun stop() {
        stopSound()
        stopVibration()
    }

    /** Stops sound effects while leaving a line-clear vibration running. */
    override fun stopSound() {
        streams.forEach(pool::stop)
        streams.clear()
    }

    /** Cancels only the current motor pulse. */
    override fun stopVibration() { vibrator?.cancel() }

    /** Releases SoundPool and its load listener when the model is destroyed. */
    override fun release() {
        if (released) return
        released = true
        stop()
        pool.setOnLoadCompleteListener(null)
        pool.release()
        loaded.clear()
        samples.clear()
    }
}
