package com.lumen.studio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Standard little-endian WAV header for interleaved signed PCM16 samples. */
internal fun waveFileHeader(bytes: Int, rate: Int, channels: Int): ByteArray {
    require(bytes >= 0 && bytes <= Int.MAX_VALUE - 36)
    require(rate in 1..384000 && channels in 1..32)
    require(bytes % (channels * 2) == 0)
    return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(bytes + 36); put("WAVEfmt ".toByteArray()); putInt(16)
        putShort(1); putShort(channels.toShort()); putInt(rate); putInt(rate * channels * 2)
        putShort((channels * 2).toShort()); putShort(16); put("data".toByteArray()); putInt(bytes)
    }.array()
}
