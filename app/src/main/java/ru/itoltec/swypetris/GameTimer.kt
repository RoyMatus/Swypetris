package ru.itoltec.swypetris

import android.os.Handler
import android.os.Looper

/** One pending game event; callbacks always run on the model's main thread. */
internal interface GameTimer {
    /** Replaces any pending callback with [action] after [delayMillis] on the main thread. */
    fun schedule(delayMillis: Long, action: () -> Unit)
    /** Removes the pending callback, if any, without invoking it. */
    fun cancel()
}

/** Handler-backed game timer whose single pending callback runs on Android's main looper. */
internal class AndroidGameTimer : GameTimer {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /** Cancels the previous callback and schedules [action] after a non-negative delay. */
    override fun schedule(delayMillis: Long, action: () -> Unit) {
        cancel()
        val callback = Runnable { pending = null; action() }
        pending = callback
        handler.postDelayed(callback, delayMillis.coerceAtLeast(0))
    }

    /** Removes the scheduled Runnable and clears its reference. */
    override fun cancel() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }
}
