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
    private val drop: Int
    private val clear: Int

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        drop = pool.load(context, R.raw.drop_soft, 1)
        clear = pool.load(context, R.raw.clear_soft, 1)
    }

    /** Skips sounds not yet loaded and requests vibration with supported Android usage attributes. */
    @Suppress("DEPRECATION")
    override fun play(event: FeedbackEvent, sound: Boolean, vibration: Boolean) {
        stop()
        val sample = if (event == FeedbackEvent.DROP) drop else clear
        if (sound && sample in loaded) {
            val stream = pool.play(sample, 0.55f, 0.55f, 1, 0, 1f)
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
    override fun holdReady() = vibrate(HapticPulse(35L, 100, 20L), true)

    /** Chooses an amplitude supported by the device and respects system vibration settings. */
    @Suppress("DEPRECATION")
    private fun vibrate(pulse: HapticPulse, vibration: Boolean) {
        val motor = vibrator ?: return
        if (pulse.duration <= 0) return
        if (!vibration || !motor.hasVibrator()) return
        val softened = pulse.softened()
        if (Build.VERSION.SDK_INT >= 26) {
            val amplitudeControl = motor.hasAmplitudeControl()
            val amplitude = if (amplitudeControl) softened.amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            val effect = VibrationEffect.createOneShot(softened.durationFor(amplitudeControl), amplitude)
            if (Build.VERSION.SDK_INT >= 33) motor.vibrate(effect,
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
        stop()
        pool.setOnLoadCompleteListener(null)
        pool.release()
        loaded.clear()
    }
}
