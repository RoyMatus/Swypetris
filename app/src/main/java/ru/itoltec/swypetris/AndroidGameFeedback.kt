package ru.itoltec.swypetris

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibrationAttributes
import java.util.concurrent.ConcurrentHashMap

/** Предзагружает мягкие WAV и воспроизводит короткие эффекты без изменения системной громкости. */
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

    /** Пропускает незагруженные звуки; вибрацию запрашивает в разрешённой Android категории игры. */
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

    /** Продолжает вибрацию ровно до конца оставшейся анимации. */
    override fun resumeClear(remainingMillis: Long, vibration: Boolean) {
        vibrate(HapticPulse(remainingMillis, 255), vibration)
    }

    /** Даёт отчётливое подтверждение включения вибрации без звука. */
    override fun previewVibration() = vibrate(HapticPulse.Preview, true)

    /** Выбирает поддерживаемую устройством амплитуду и учитывает системные настройки. */
    @Suppress("DEPRECATION")
    private fun vibrate(pulse: HapticPulse, vibration: Boolean) {
        val motor = vibrator ?: return
        if (pulse.duration <= 0) return
        if (!vibration || !motor.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            val amplitudeControl = motor.hasAmplitudeControl()
            val amplitude = if (amplitudeControl) pulse.amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            val effect = VibrationEffect.createOneShot(pulse.durationFor(amplitudeControl), amplitude)
            if (Build.VERSION.SDK_INT >= 33) motor.vibrate(effect,
                VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_MEDIA).build())
            else motor.vibrate(effect, vibrationAttributes)
        } else {
            motor.vibrate(pulse.durationFor(false), vibrationAttributes)
        }
    }

    /** Останавливает уже запущенные звуки и импульсы; очередь воспроизведения отсутствует. */
    override fun stop() {
        stopSound()
        stopVibration()
    }

    /** Останавливает звуки, не прерывая вибрацию очистки. */
    override fun stopSound() {
        streams.forEach(pool::stop)
        streams.clear()
    }

    /** Отменяет только текущий импульс мотора. */
    override fun stopVibration() { vibrator?.cancel() }

    /** Освобождает SoundPool и слушатель загрузки после завершения работы модели. */
    override fun release() {
        stop()
        pool.setOnLoadCompleteListener(null)
        pool.release()
        loaded.clear()
    }
}

