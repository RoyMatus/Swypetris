package ru.itoltec.swypetris

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** One retained owner for enabled state, saved image and the unsaved crop preview. */
internal class CustomBackground(private val store: BackgroundStore, private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    var enabled by mutableStateOf(store.enabled)
        private set
    var image by mutableStateOf<BackgroundImage?>(null)
        private set
    var crop by mutableStateOf(store.crop)
        private set
    var draft by mutableStateOf<BackgroundImage?>(null)
        private set
    var draftCrop by mutableStateOf(BackgroundCrop())
        private set
    var busy by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        scope.launch {
            try { image = withContext(ioDispatcher) { store.restore() } }
            catch (_: IOException) { image = null }
            finally { busy = false }
        }
    }

    fun changeEnabled(value: Boolean) {
        enabled = value
        store.setEnabled(value)
    }

    /** Cancellation leaves the existing image/crop untouched; imported content is read off-main. */
    fun choose(resolver: ContentResolver, uri: Uri?) {
        if (uri == null || busy) return
        busy = true
        error = null
        scope.launch {
            try {
                draft = withContext(ioDispatcher) { store.importImage(resolver, uri) }
                draftCrop = BackgroundCrop()
            } catch (_: IOException) { failed() }
            catch (_: SecurityException) { failed() }
            catch (_: IllegalArgumentException) { failed() }
            catch (_: IllegalStateException) { failed() }
            finally { busy = false }
        }
    }

    fun transform(width: Float, height: Float, gesture: BackgroundTransform) {
        val current = draft ?: return
        if (busy) return
        draftCrop = transformBackground(draftCrop, current.bitmap.width.toFloat(), current.bitmap.height.toFloat(),
            width, height, gesture)
    }

    fun save() {
        val current = draft ?: return
        if (busy) return
        busy = true
        val adjusted = draftCrop
        scope.launch {
            try {
                image = withContext(ioDispatcher) { store.save(current, adjusted) }
                crop = adjusted
                enabled = true
                draft = null
            } catch (_: IOException) { failed() }
            catch (_: IllegalStateException) { failed() }
            finally { busy = false }
        }
    }

    fun cancel() {
        if (busy) return
        val discarded = draft
        draft = null
        scope.launch { withContext(ioDispatcher) { discarded?.file?.delete() } }
    }

    private fun failed() { error = "Не удалось открыть или сохранить изображение. Прежний фон сохранён." }
}
