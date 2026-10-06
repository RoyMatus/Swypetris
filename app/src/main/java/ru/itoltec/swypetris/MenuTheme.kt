package ru.itoltec.swypetris

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The prepared lossless stereo loop, without resampling, normalization or codec padding. */
internal object MenuTheme {
    const val SAMPLE_RATE = 48_000
    const val FRAME_COUNT = 1_417_069
    const val CHANNELS = 2
    const val GAIN = 1f
    private val pcmSubtype = byteArrayOf(1, 0, 0, 0, 0, 0, 16, 0, -128, 0, 0, -86, 0, 56, -101, 113)

    fun render(context: Context): FloatArray =
        context.resources.openRawResource(R.raw.menu_melody_loop).use { decode(it.readBytes()) }

    /** All signed 24-bit PCM values are represented exactly by a normalized float. */
    internal fun decode(wave: ByteArray): FloatArray {
        val bytes = ByteBuffer.wrap(wave).order(ByteOrder.LITTLE_ENDIAN)
        require(wave.size >= 12 && tag(wave, 0) == "RIFF" && tag(wave, 8) == "WAVE")
        require(bytes.getInt(4).toLong() + 8 == wave.size.toLong())
        var cursor = 12
        var formatFound = false
        while (cursor + 8 <= wave.size) {
            val size = bytes.getInt(cursor + 4)
            val start = cursor + 8
            require(size >= 0 && size <= wave.size - start)
            when (tag(wave, cursor)) {
                "fmt " -> {
                    validateFormat(bytes, wave, start, size)
                    formatFound = true
                }
                "data" -> {
                    require(formatFound && size == FRAME_COUNT * CHANNELS * 3)
                    return FloatArray(size / 3) { index ->
                        val offset = start + index * 3
                        val sample = (wave[offset].toInt() and 255) or
                            ((wave[offset + 1].toInt() and 255) shl 8) or (wave[offset + 2].toInt() shl 16)
                        sample / 8_388_608f
                    }
                }
            }
            cursor = start + size + size % 2
        }
        error("Missing menu music PCM data")
    }

    private fun tag(wave: ByteArray, offset: Int) = String(wave, offset, 4, Charsets.US_ASCII)

    private fun validateFormat(bytes: ByteBuffer, wave: ByteArray, start: Int, size: Int) {
        require(size >= 40 && (bytes.getShort(start).toInt() and 65535) == 65534)
        require(bytes.getShort(start + 2).toInt() == CHANNELS && bytes.getInt(start + 4) == SAMPLE_RATE)
        require(bytes.getInt(start + 8) == SAMPLE_RATE * CHANNELS * 3 && bytes.getShort(start + 12).toInt() == 6)
        require(bytes.getShort(start + 14).toInt() == 24 && bytes.getShort(start + 16).toInt() >= 22)
        require(bytes.getShort(start + 18).toInt() == 24 && bytes.getInt(start + 20) == 3)
        require(wave.copyOfRange(start + 24, start + 40).contentEquals(pcmSubtype))
    }
}

/** Static PCM loop owned by GameMusic; pause/play retain the current loop position. */
internal class MenuThemePlayer private constructor(private val track: AudioTrack) {
    fun play() {
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
    }

    fun pause() {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
    }

    fun release() = track.release()

    companion object {
        fun create(context: Context, attributes: AudioAttributes): MenuThemePlayer {
            val samples = MenuTheme.render(context)
            val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(MenuTheme.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
            val track = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * Float.SIZE_BYTES).build()
            try {
                check(track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING) == samples.size) {
                    "Unable to initialize the menu music buffer"
                }
                check(track.setLoopPoints(0, samples.size / MenuTheme.CHANNELS, -1) == AudioTrack.SUCCESS)
                check(track.setVolume(MenuTheme.GAIN) == AudioTrack.SUCCESS)
                return MenuThemePlayer(track)
            } catch (failure: Throwable) {
                track.release()
                throw failure
            }
        }
    }
}
