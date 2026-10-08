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

/** Audio mode: silence, menu theme, game playlist, or a one-shot record fanfare. */
enum class MusicMode { SILENT, MENU, GAME, RECORD }

/** Music control separated from Android playback so mode transitions can be tested without a speaker. */
interface MusicPlayback {
    /** Changes the persisted selection; repeating it does not restart playback. */
    fun select(selection: MusicSelection) = Unit
    /** Pauses or resumes the playlist for the current session. */
    fun setPlaying(enabled: Boolean)
    /** Chooses the playlist, one-shot fanfare, or silence. */
    fun setMode(next: MusicMode)
    /** Releases players and listeners. */
    fun release()
}

/** Plays eight full tracks with gaps, saved position, and audio-focus handling. */
class GameMusic(private val context: Context) : MusicPlayback {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val resources = Song.entries.map { it.resource }
    private val playlist = PlaylistClock(resources.size, clock = SystemClock::uptimeMillis)
    private var menuPlayer: MenuThemePlayer? = null
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
        /** Checks for the end of a gap only while playback is allowed. */
        override fun run() { synchronizePlayback() }
    }
    private val noisyReceiver = object : BroadcastReceiver() {
        /** Prevents sound from moving to the speaker automatically after headphones disconnect. */
        override fun onReceive(context: Context?, intent: Intent?) {
            blockedByHeadphones = true
            synchronizePlayback()
        }
    }

    init {
        ContextCompat.registerReceiver(context, noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Resumes the previous track or the remainder of an inter-track gap. */
    override fun setPlaying(enabled: Boolean) = setMode(if (enabled &&
        selection != MusicSelection.Off) MusicMode.GAME else MusicMode.SILENT)

    /** Releases the old player and starts the newly selected mode from the beginning. */
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

    /** Entering record-fanfare mode once does not reset the game's playlist position. */
    @Suppress("DEPRECATION")
    override fun setMode(next: MusicMode) {
        val target = if (selection == MusicSelection.Off) MusicMode.SILENT else next
        if (released || target == mode) return
        playlist.setActive(false)
        menuPlayer?.pause()
        gamePlayer?.pause()
        recordPlayer?.pause()
        mode = target
        if (target == MusicMode.SILENT) {
            hasFocus = false
            if (Build.VERSION.SDK_INT >= 26) focusRequest?.let(audio::abandonAudioFocusRequest)
            else audio.abandonAudioFocus(listener)
        } else {
            blockedByHeadphones = false
            if (target == MusicMode.RECORD) {
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

    /** Coordinates the timer, audio focus, and players so at most one plays at a time. */
    private fun synchronizePlayback() {
        if (released) return
        handler.removeCallbacks(timer)
        val allowed = hasFocus && !blockedByHeadphones && selection != MusicSelection.Off
        playlist.setActive(allowed && mode == MusicMode.GAME)
        playlist.advance()
        if (mode != MusicMode.MENU || !allowed) menuPlayer?.pause()
        if (mode != MusicMode.GAME || !allowed) gamePlayer?.pause()
        if (mode != MusicMode.RECORD || !allowed) recordPlayer?.pause()
        if (!allowed) return
        if (mode == MusicMode.MENU) {
            if (menuPlayer == null) menuPlayer = MenuThemePlayer.create(context, attributes)
            menuPlayer?.play()
            return
        }
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

    /** Loads a bundled recording at moderate volume without internal looping. */
    private fun createPlayer(resource: Int): MediaPlayer? =
        MediaPlayer.create(context, resource, attributes, 0)?.apply {
            isLooping = false
            setVolume(.4f, .4f)
        }

    /** Stops the timer and releases both players at the end of the session. */
    override fun release() {
        if (released) return
        setMode(MusicMode.SILENT)
        released = true
        handler.removeCallbacks(timer)
        context.unregisterReceiver(noisyReceiver)
        menuPlayer?.release()
        gamePlayer?.release()
        recordPlayer?.release()
        menuPlayer = null
        gamePlayer = null
        recordPlayer = null
    }
}
