package ru.itoltec.swypetris

import android.os.Handler
import android.os.Looper

/** One pending game event; callbacks always run on the model's main thread. */
internal interface GameTimer {
    fun schedule(delayMillis: Long, action: () -> Unit)
    fun cancel()
}

internal class AndroidGameTimer : GameTimer {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    override fun schedule(delayMillis: Long, action: () -> Unit) {
        cancel()
        val callback = Runnable { pending = null; action() }
        pending = callback
        handler.postDelayed(callback, delayMillis.coerceAtLeast(0))
    }

    override fun cancel() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }
}
