package ru.itoltec.swypetris

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val RIFF_HEADER_BYTES = 12
private const val RIFF_SIZE_OFFSET = 4
private const val RIFF_SIZE_BIAS = 8
private const val WAVE_TAG_OFFSET = 8
private const val CHUNK_HEADER_BYTES = 8
private const val CHUNK_SIZE_OFFSET = 4
private const val SAMPLE_BYTES = 3
private const val SAMPLE_NORMALIZATION = 8_388_608f
private const val UNSIGNED_BYTE_MASK = 255
private const val TAG_BYTES = 4
private const val EXTENSIBLE_FORMAT_BYTES = 40
private const val UNSIGNED_SHORT_MASK = 65535
private const val WAVE_FORMAT_EXTENSIBLE = 65534
private const val CHANNELS_OFFSET = 2
private const val SAMPLE_RATE_OFFSET = 4
private const val BYTE_RATE_OFFSET = 8
private const val BLOCK_ALIGNMENT_OFFSET = 12
private const val BLOCK_ALIGNMENT_BYTES = 6
private const val BITS_PER_SAMPLE_OFFSET = 14
private const val SAMPLE_BITS = 24
private const val EXTRA_SIZE_OFFSET = 16
private const val EXTENSIBLE_EXTRA_BYTES = 22
private const val VALID_BITS_OFFSET = 18
private const val CHANNEL_MASK_OFFSET = 20
private const val STEREO_CHANNEL_MASK = 3
private const val SUBTYPE_START_OFFSET = 24
private const val SUBTYPE_END_OFFSET = 40


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
        require(wave.size >= RIFF_HEADER_BYTES && tag(wave, 0) == "RIFF" && tag(wave, WAVE_TAG_OFFSET) == "WAVE")
        require(bytes.getInt(RIFF_SIZE_OFFSET).toLong() + RIFF_SIZE_BIAS == wave.size.toLong())
        var cursor = RIFF_HEADER_BYTES
        var formatFound = false
        while (cursor + CHUNK_HEADER_BYTES <= wave.size) {
            val size = bytes.getInt(cursor + CHUNK_SIZE_OFFSET)
            val start = cursor + CHUNK_HEADER_BYTES
            require(size >= 0 && size <= wave.size - start)
            when (tag(wave, cursor)) {
                "fmt " -> {
                    validateFormat(bytes, wave, start, size)
                    formatFound = true
                }
                "data" -> {
                    require(formatFound && size == FRAME_COUNT * CHANNELS * SAMPLE_BYTES)
                    return FloatArray(size / SAMPLE_BYTES) { index ->
                        val offset = start + index * SAMPLE_BYTES
                        val sample = (wave[offset].toInt() and UNSIGNED_BYTE_MASK) or
                            ((wave[offset + 1].toInt() and UNSIGNED_BYTE_MASK) shl Byte.SIZE_BITS) or (
                                wave[offset + 2].toInt() shl Short.SIZE_BITS)
                        sample / SAMPLE_NORMALIZATION
                    }
                }
            }
            cursor = start + size + size % 2
        }
        error("Missing menu music PCM data")
    }

    private fun tag(wave: ByteArray, offset: Int) = String(wave, offset, TAG_BYTES, Charsets.US_ASCII)

    private fun validateFormat(bytes: ByteBuffer, wave: ByteArray, start: Int, size: Int) {
        require(size >= EXTENSIBLE_FORMAT_BYTES &&
            (bytes.getShort(start).toInt() and UNSIGNED_SHORT_MASK) == WAVE_FORMAT_EXTENSIBLE)
        require(bytes.getShort(start + CHANNELS_OFFSET).toInt() == CHANNELS &&
            bytes.getInt(start + SAMPLE_RATE_OFFSET) == SAMPLE_RATE)
        require(bytes.getInt(start + BYTE_RATE_OFFSET) == SAMPLE_RATE * CHANNELS * SAMPLE_BYTES &&
            bytes.getShort(start + BLOCK_ALIGNMENT_OFFSET).toInt() == BLOCK_ALIGNMENT_BYTES)
        require(bytes.getShort(start + BITS_PER_SAMPLE_OFFSET).toInt() == SAMPLE_BITS &&
            bytes.getShort(start + EXTRA_SIZE_OFFSET).toInt() >= EXTENSIBLE_EXTRA_BYTES)
        require(bytes.getShort(start + VALID_BITS_OFFSET).toInt() == SAMPLE_BITS &&
            bytes.getInt(start + CHANNEL_MASK_OFFSET) == STEREO_CHANNEL_MASK)
        require(wave.copyOfRange(start + SUBTYPE_START_OFFSET, start + SUBTYPE_END_OFFSET).contentEquals(pcmSubtype))
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
            var initialized = false
            try {
                check(track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING) == samples.size) {
                    "Unable to initialize the menu music buffer"
                }
                check(track.setLoopPoints(0, samples.size / MenuTheme.CHANNELS, -1) == AudioTrack.SUCCESS)
                check(track.setVolume(MenuTheme.GAIN) == AudioTrack.SUCCESS)
                val player = MenuThemePlayer(track)
                initialized = true
                return player
            } finally {
                if (!initialized) track.release()
            }
        }
    }
}
