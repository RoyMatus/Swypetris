package ru.itoltec.swypetris

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Original Swypetris main-menu arrangement.
 *
 * The sparse lead cell is derived from the public-domain Korobeiniki melody while the harmony,
 * voicing, pacing, synthesis, and arrangement are original to Swypetris. PCM is rendered locally
 * so the menu track is independent from every gameplay recording.
 */
internal object MenuTheme {
    const val SAMPLE_RATE = 16_000
    const val DURATION_SECONDS = 72
    const val FRAME_COUNT = SAMPLE_RATE * DURATION_SECONDS

    private const val BPM = 80.0
    private const val TABLE_SIZE = 4096
    private val beatSeconds = 60.0 / BPM
    private val sineTable = DoubleArray(TABLE_SIZE) { index ->
        sin(2.0 * PI * index / TABLE_SIZE)
    }
    private val chords = arrayOf(
        intArrayOf(52, 59, 64), // E3 B3 E4
        intArrayOf(48, 55, 64), // C3 G3 E4
        intArrayOf(43, 50, 59), // G2 D3 B3
        intArrayOf(50, 57, 62)  // D3 A3 D4
    )
    private val motif = intArrayOf(76, 71, 72, 74, 72, 71, 69, 72)

    /** Renders one exact 60-second loop at conservative amplitude. */
    fun render(): ShortArray {
        val output = ShortArray(FRAME_COUNT)
        for (frame in output.indices) {
            val seconds = frame.toDouble() / SAMPLE_RATE
            val beat = seconds / beatSeconds
            val bar = (beat / 4.0).toInt()
            val chord = chords[bar % chords.size]

            var sample = 0.0
            chord.forEachIndexed { index, midi ->
                val hz = midiToHz(midi)
                val base = sine(hz, seconds)
                val overtone = sine(hz * 2.0, seconds)
                sample += base * if (index == 0) 0.075 else 0.052
                sample += overtone * 0.012
            }

            // A quiet two-beat pulse keeps the menu alive without arcade-style percussion.
            val beatPhase = beat - beat.toInt()
            val beatInBar = beat.toInt() % 4
            if ((beatInBar == 0 || beatInBar == 2) && beatPhase < 0.42) {
                val fade = 1.0 - beatPhase / 0.42
                sample += triangle(midiToHz(chord[0]), seconds) * 0.035 * fade
            }

            // One transformed Korobeiniki-derived cell every eight bars.
            val barInCycle = bar % 8
            if (barInCycle == 2) {
                val localBeat = beat - bar * 4.0
                val noteIndex = (localBeat / 0.5).toInt()
                if (noteIndex in motif.indices) {
                    val notePhase = (localBeat - noteIndex * 0.5) / 0.5
                    val envelope = 1.0 - kotlin.math.abs(notePhase * 2.0 - 1.0)
                    sample += triangle(midiToHz(motif[noteIndex]), seconds) * 0.075 * envelope
                }
            }

            // Slow amplitude movement replaces drums and keeps the loop unobtrusive.
            sample *= 0.90 + 0.10 * sine(1.0 / 18.0, seconds)
            output[frame] = (sample.coerceIn(-0.82, 0.82) * Short.MAX_VALUE).toInt().toShort()
        }

        // A short zero-crossing envelope removes clicks without a perceptible restart.
        val edgeFrames = SAMPLE_RATE / 20
        for (i in 0 until edgeFrames) {
            val gain = i.toDouble() / edgeFrames
            output[i] = (output[i] * gain).toInt().toShort()
            val tail = output.lastIndex - i
            output[tail] = (output[tail] * gain).toInt().toShort()
        }
        return output
    }

    private fun midiToHz(note: Int): Double = 440.0 * Math.pow(2.0, (note - 69) / 12.0)

    private fun sine(hz: Double, seconds: Double): Double {
        val phase = (hz * seconds) % 1.0
        return sineTable[(phase * TABLE_SIZE).toInt().coerceIn(0, TABLE_SIZE - 1)]
    }

    private fun triangle(hz: Double, seconds: Double): Double {
        val phase = (hz * seconds) % 1.0
        return 1.0 - 4.0 * kotlin.math.abs(phase - 0.5)
    }
}

/** Static PCM loop owned by [GameMusic]; it never acquires audio focus on its own. */
internal class MenuThemePlayer private constructor(private val track: AudioTrack) {
    fun play() {
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
    }

    fun pause() {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
    }

    fun release() = track.release()

    companion object {
        fun create(attributes: AudioAttributes): MenuThemePlayer {
            val samples = MenuTheme.render()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(MenuTheme.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
                .build()
            val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            check(written == samples.size) { "Unable to initialize the menu music buffer" }
            track.setLoopPoints(0, samples.size, -1)
            track.setVolume(.32f)
            return MenuThemePlayer(track)
        }
    }
}
