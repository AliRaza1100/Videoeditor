package com.lumen.studio

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun main() {
    val header = waveFileHeader(192000, 48000, 2)
    val bytes = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
    check(header.size == 44)
    check(String(header, 0, 4) == "RIFF" && String(header, 8, 8) == "WAVEfmt ")
    check(bytes.getInt(4) == 192036)
    check(bytes.getShort(20).toInt() == 1 && bytes.getShort(22).toInt() == 2)
    check(bytes.getInt(24) == 48000 && bytes.getInt(28) == 192000)
    check(bytes.getShort(32).toInt() == 4 && bytes.getShort(34).toInt() == 16)
    check(String(header, 36, 4) == "data" && bytes.getInt(40) == 192000)
    check(runCatching { waveFileHeader(3, 48000, 2) }.isFailure)
    check(runCatching { waveFileHeader(10, 0, 1) }.isFailure)
    check(runCatching { waveFileHeader(-1, 48000, 1) }.isFailure)
    println("WAV checks passed: PCM16 stereo header, byte order, sizes, sample rate, alignment and invalid input.")
}
