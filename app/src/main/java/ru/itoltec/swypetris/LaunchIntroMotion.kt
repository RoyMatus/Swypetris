package ru.itoltec.swypetris

import kotlin.math.sin

/** Воспроизводимые траектории заставки; координаты нормированы и не зависят от частоты кадров. */
internal object LaunchIntroMotion {
    const val DURATION = 3000L

    /** Полосы приезжают поочерёдно с разных сторон и полностью собираются за 1100 мс. */
    fun stripeProgress(elapsed: Long, index: Int, count: Int): Float {
        val delay = if (count <= 1) 0L else 350L * index / (count - 1)
        return easeOut(progress(elapsed, delay, 750L))
    }

    /** Порядок падения кубиков перемешан, но не меняется при пересоздании экрана. */
    fun cubeProgress(elapsed: Long, index: Int): Float =
        progress(elapsed, 450L + seed(index, 17) % 1301, 700L + seed(index, 71) % 201)

    /** Горизонтальная координата и наклон плавно сходятся к месту кубика. */
    fun easeOut(value: Float): Float = 1f - (1f - value) * (1f - value) * (1f - value)

    /** После ускоренного падения кубик слегка подпрыгивает и встаёт точно на место. */
    fun fallProgress(value: Float): Float = if (value < .82f) (value / .82f) * (value / .82f)
        else 1f - .016f * sin((value - .82f) / .18f * Math.PI).toFloat()

    /** Кнопки появляются только после сборки надписи. */
    fun buttonsAlpha(elapsed: Long): Float = progress(elapsed, 2700L, 300L)

    /** Стабильное перемешивание индекса без общей изменяемой очереди Random. */
    fun seed(index: Int, salt: Int): Int = ((index.toLong() * 1103515245L + salt * 12345L) xor
        (index.toLong() * 2654435761L ushr 9)).and(0x7fffffffL).toInt()

    /** Ограничивает локальное время одной стадии анимации. */
    private fun progress(elapsed: Long, delay: Long, duration: Long): Float =
        ((elapsed - delay).toFloat() / duration).coerceIn(0f, 1f)
}
