package ru.itoltec.swypetris

/** Игровые события, для которых предусмотрены короткие звук и вибрация. */
enum class FeedbackEvent { DROP, CLEAR }

/** Выбирает один эффект перехода; очистка имеет приоритет над броском. */
fun feedbackEvent(before: GameState, after: GameState, command: GameCommand): FeedbackEvent? = when {
    before.clearingRows.isEmpty() && after.clearingRows.isNotEmpty() -> FeedbackEvent.CLEAR
    before.clearingRows.isEmpty() && (command == GameCommand.HARD_DROP || before.accelerated) && after.generation != before.generation -> FeedbackEvent.DROP
    else -> null
}

/** Граница между правилами игры и устройством; заменяется записывающей реализацией в тестах. */
interface GameFeedback {
    /** Воспроизводит разрешённые настройками части эффекта. */
    fun play(event: FeedbackEvent, sound: Boolean, vibration: Boolean)
    /** Возобновляет только вибрацию оставшейся части очистки, не повторяя звук. */
    fun resumeClear(remainingMillis: Long, vibration: Boolean) = Unit
    /** Проверяет мотор одним импульсом при явном включении настройки. */
    fun previewVibration() = Unit
    /** Останавливает только вибрацию, сохраняя разрешённый звук. */
    fun stopVibration() = Unit
    /** Останавливает только звуковые эффекты. */
    fun stopSound() = Unit
    /** Останавливает эффекты без последующего возобновления. */
    fun stop()
    /** Освобождает ресурсы при уничтожении модели. */
    fun release()
}

/** Тихая реализация для тестов правил и анимации. */
object SilentFeedback : GameFeedback {
    /** Не обращается к устройству. */
    override fun play(event: FeedbackEvent, sound: Boolean, vibration: Boolean) = Unit
    /** Не требует остановки. */
    override fun stop() = Unit
    /** Не владеет ресурсами. */
    override fun release() = Unit
}

