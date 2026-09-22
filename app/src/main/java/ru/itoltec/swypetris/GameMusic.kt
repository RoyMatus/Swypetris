package ru.itoltec.swypetris

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

/** Режим сопровождения: тишина, последовательный плейлист или однократные фанфары. */
enum class MusicMode { SILENT, GAME, RECORD }

/** Управление музыкой отдельно от устройства для проверки переходов без динамика. */
interface MusicPlayback {
    /** Меняет сохраняемый выбор; повтор того же значения не сбрасывает запись. */
    fun select(selection: MusicSelection) = Unit
    /** Приостанавливает или продолжает плейлист текущего сеанса. */
    fun setPlaying(enabled: Boolean)
    /** Выбирает плейлист, однократные фанфары или тишину. */
    fun setMode(next: MusicMode)
    /** Освобождает проигрыватели и подписки. */
    fun release()
}

/** Восемь полных тем с паузами, сохранением позиции и корректным аудиофокусом. */
class GameMusic(private val context: Context) : MusicPlayback {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val resources = Song.entries.map { it.resource }
    private val playlist = PlaylistClock(resources.size, clock = SystemClock::uptimeMillis)
    private var gamePlayer: MediaPlayer? = null
    private var gameOccurrence = -1L
    private var selection: MusicSelection = MusicSelection.ShuffleAll
    private var recordPlayer: MediaPlayer? = null
    private var mode = MusicMode.SILENT
    private var blockedByHeadphones = false
    private var hasFocus = false
    private var released = false
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        hasFocus = change == AudioManager.AUDIOFOCUS_GAIN
        synchronizePlayback()
    }
    private val focusRequest = if (Build.VERSION.SDK_INT >= 26) AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener(listener, handler).build() else null
    private val timer = object : Runnable {
        /** Проверяет окончание паузы, пока приложение действительно может воспроизводить музыку. */
        override fun run() { synchronizePlayback() }
    }
    private val noisyReceiver = object : BroadcastReceiver() {
        /** После отключения наушников звук не переносится на динамик автоматически. */
        override fun onReceive(context: Context?, intent: Intent?) {
            blockedByHeadphones = true
            synchronizePlayback()
        }
    }

    init {
        ContextCompat.registerReceiver(context, noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Возобновляет прежнюю композицию или остаток межтрековой паузы. */
    override fun setPlaying(enabled: Boolean) = setMode(if (enabled && selection != MusicSelection.Off) MusicMode.GAME else MusicMode.SILENT)

    /** Смена выбора освобождает старую запись и начинает выбранный режим сначала. */
    override fun select(selection: MusicSelection) {
        if (released || this.selection == selection) return
        this.selection = selection
        gamePlayer?.release()
        gamePlayer = null
        gameOccurrence = -1L
        playlist.select((selection as? MusicSelection.Track)?.song?.ordinal)
        if (selection == MusicSelection.Off) setMode(MusicMode.SILENT)
        else synchronizePlayback()
    }

    /** Однократный вход в режим фанфар не сбрасывает позицию игрового плейлиста. */
    @Suppress("DEPRECATION")
    override fun setMode(next: MusicMode) {
        if (released || next == mode) return
        playlist.setActive(false)
        gamePlayer?.pause()
        recordPlayer?.pause()
        mode = next
        if (next == MusicMode.SILENT) {
            hasFocus = false
            if (Build.VERSION.SDK_INT >= 26) focusRequest?.let(audio::abandonAudioFocusRequest)
            else audio.abandonAudioFocus(listener)
        } else {
            blockedByHeadphones = false
            if (next == MusicMode.RECORD) {
                if (recordPlayer == null) recordPlayer = createPlayer(R.raw.record_fanfare)?.apply {
                    setOnCompletionListener { if (mode == MusicMode.RECORD) setMode(MusicMode.SILENT) }
                }
                recordPlayer?.seekTo(0)
            }
            val result = if (Build.VERSION.SDK_INT >= 26) audio.requestAudioFocus(focusRequest!!)
                else audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        synchronizePlayback()
    }

    /** Согласует таймер, фокус и оба проигрывателя; одновременно звучит максимум один. */
    private fun synchronizePlayback() {
        if (released) return
        handler.removeCallbacks(timer)
        val allowed = hasFocus && !blockedByHeadphones
        playlist.setActive(allowed && mode == MusicMode.GAME)
        playlist.advance()
        if (mode != MusicMode.GAME || !allowed) gamePlayer?.pause()
        if (mode != MusicMode.RECORD || !allowed) recordPlayer?.pause()
        if (!allowed) return
        if (mode == MusicMode.RECORD) { recordPlayer?.start(); return }
        if (mode != MusicMode.GAME) return
        if (playlist.remainingGap != null) {
            handler.postDelayed(timer, playlist.remainingGap!!.coerceAtLeast(1L))
            return
        }
        if (gameOccurrence != playlist.occurrence) {
            gamePlayer?.release()
            gameOccurrence = playlist.occurrence
            gamePlayer = createPlayer(resources[playlist.index])?.apply {
                setOnCompletionListener { player ->
                    if (player === gamePlayer) { playlist.completed(); synchronizePlayback() }
                }
                setOnErrorListener { player, _, _ ->
                    if (player === gamePlayer) {
                        gamePlayer?.release(); gamePlayer = null
                        playlist.completed(); synchronizePlayback()
                    }
                    true
                }
            }
            if (gamePlayer == null) { playlist.completed(); synchronizePlayback(); return }
        }
        gamePlayer?.start()
    }

    /** Загружает собственную запись с умеренной громкостью без внутреннего зацикливания. */
    private fun createPlayer(resource: Int): MediaPlayer? =
        MediaPlayer.create(context, resource, attributes, 0)?.apply {
            isLooping = false
            setVolume(.4f, .4f)
        }

    /** Останавливает таймер и освобождает оба проигрывателя при завершении сеанса. */
    override fun release() {
        if (released) return
        setMode(MusicMode.SILENT)
        released = true
        handler.removeCallbacks(timer)
        context.unregisterReceiver(noisyReceiver)
        gamePlayer?.release()
        recordPlayer?.release()
        gamePlayer = null
        recordPlayer = null
    }
}
