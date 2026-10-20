package com.lumen.studio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.Executors

data class LibrarySound(val id: String, val name: String, val path: String, val durationMs: Long, val extracted: Boolean)

/** Private copies remain usable after the source picker permission expires. */
object SoundLibrary {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile var busy = false
        private set
    @Volatile var status = ""
        private set
    @Volatile var revision = 0L
        private set
    @Volatile var progress = -1
        private set
    @Volatile var importingName = "Selected sound"
        private set
    private fun reportProgress(value: Int) {
        val next=value.coerceIn(0,99)
        if(next!=progress) { progress=next; revision++ }
    }
    private fun directory(context: Context) = File(context.filesDir, "sounds").apply { mkdirs() }
    private fun index(context: Context) = AtomicFile(File(directory(context), "library.json"))

    @Synchronized fun list(context: Context): List<LibrarySound> {
        val store = index(context)
        if (!store.baseFile.exists()) return emptyList()
        val array = JSONArray(store.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        return (0 until array.length()).map { i -> array.getJSONObject(i).let {
            LibrarySound(it.getString("id"), it.getString("name"), it.getString("path"), it.getLong("duration"), it.getBoolean("extracted"))
        } }.filter { File(it.path).isFile }
    }

    @Synchronized private fun save(context: Context, sound: LibrarySound) {
        val array = JSONArray()
        (list(context) + sound).forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("path", it.path)
                .put("duration", it.durationMs).put("extracted", it.extracted))
        }
        val store = index(context)
        val stream = store.startWrite()
        try { stream.write(array.toString().toByteArray(Charsets.UTF_8)); store.finishWrite(stream) }
        catch (error: Exception) { store.failWrite(stream); throw error }
    }

    @Synchronized fun import(context: Context, uri: Uri, extract: Boolean,callback: ((LibrarySound?)->Unit)?=null) {
        if (busy) return
        busy = true; progress=-1; importingName="Selected sound"; status = if (extract) "Extracting sound…" else "Importing audio…"; revision++
        val app = context.applicationContext
        executor.execute {
            val id = UUID.randomUUID().toString()
            val partial = File(directory(app), "$id.part")
            var completed: File? = null
            var result: LibrarySound?=null
            try {
                val name = runCatching { app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: if (extract) "Extracted sound" else "Imported audio"
                importingName=name; revision++
                if (extract) {
                    extractToWave(app,uri,partial)
                } else {
                    val input=app.contentResolver.openInputStream(uri) ?: error("Unable to read the selected audio.")
                    val expected=runCatching { app.contentResolver.openAssetFileDescriptor(uri,"r")?.use { it.length } ?: -1 }.getOrDefault(-1)
                    input.use {
                        partial.outputStream().use { output ->
                            val buffer=ByteArray(65536)
                            var total=0L
                            while(true) {
                                val count=input.read(buffer)
                                if(count<0) { break }
                                total+=count
                                require(total<=1024L*1024*1024) { "Audio is too large to import (1 GB maximum)." }
                                output.write(buffer,0,count)
                                if(expected>0) { reportProgress((total*100/expected).toInt()) }
                            }
                        }
                    }
                }
                val metadata = MediaMetadataRetriever()
                val duration = try {
                    metadata.setDataSource(partial.absolutePath)
                    require(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") { "The selected file has no audio track." }
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally { metadata.release() }
                val destination = File(directory(app), "$id.${if (extract) "wav" else "audio"}")
                require(partial.renameTo(destination)) { "Unable to save sound. Check available storage." }
                completed = destination
                result=LibrarySound(id, if (extract) "${name.substringBeforeLast('.')} · extracted" else name,destination.absolutePath,duration,extract)
                save(app,result)
                status = "Sound saved to your library."
            } catch (error: Exception) {
                result=null; partial.delete(); completed?.delete()
                status = error.message ?: "Unable to import this sound."
            } finally { progress=if(result!=null) 100 else -1; busy = false; revision++; val sound=result; if(callback!=null) android.os.Handler(android.os.Looper.getMainLooper()).post { callback(sound) } }
        }
    }

    /** Decode the first audio track to 16-bit PCM WAV; source video is never changed. */
    private fun extractToWave(context: Context, uri: Uri, file: File) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("This video has no audio track.")
            val format = extractor.getTrackFormat(track)
            val totalUs=if(format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            extractor.selectTrack(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio format."))
            codec = decoder
            decoder.configure(format, null, null, 0); decoder.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var inputEnded = false
            var outputEnded = false
            var written = 0L
            var lastProgress = System.currentTimeMillis()
            val info = MediaCodec.BufferInfo()
            RandomAccessFile(file, "rw").use { output ->
                output.setLength(0); output.write(ByteArray(44))
                while (!outputEnded) {
                    if (Thread.currentThread().isInterrupted) error("Extraction cancelled.")
                    if (!inputEnded) {
                        val inputIndex = decoder.dequeueInputBuffer(10000)
                        if (inputIndex >= 0) {
                            val buffer = decoder.getInputBuffer(inputIndex) ?: error("Unable to decode audio.")
                            buffer.clear()
                            val count = extractor.readSampleData(buffer, 0)
                            if (count < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true
                            } else {
                                require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Protected audio cannot be extracted." }
                                decoder.queueInputBuffer(inputIndex, 0, count, extractor.sampleTime.coerceAtLeast(0), 0)
                                extractor.advance()
                            }
                            lastProgress = System.currentTimeMillis()
                        }
                    }
                    val outputIndex = decoder.dequeueOutputBuffer(info, 10000)
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val pcm = decoder.outputFormat
                        rate = pcm.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = pcm.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding = if (pcm.containsKey(MediaFormat.KEY_PCM_ENCODING)) pcm.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT) { "Unsupported decoded audio format." }
                    } else if (outputIndex >= 0) {
                        val buffer = decoder.getOutputBuffer(outputIndex) ?: error("Unable to decode audio.")
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                val floats = buffer.slice().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                                ByteBuffer.allocate(floats.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
                                    while (floats.hasRemaining()) putShort((floats.get().coerceIn(-1f,1f) * 32767).toInt().toShort())
                                }.array()
                            } else ByteArray(info.size).also { buffer.get(it) }
                            written += bytes.size
                            require(written <= 1024L * 1024 * 1024) { "Extracted audio exceeds the 1 GB limit." }
                            output.write(bytes)
                        }
                        if(totalUs>0) reportProgress((info.presentationTimeUs*100/totalUs).toInt())
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                        lastProgress = System.currentTimeMillis()
                    }
                    require(System.currentTimeMillis() - lastProgress < 30000) { "The audio decoder stopped responding." }
                }
                require(written > 0 && channels > 0 && rate > 0) { "No audio could be extracted." }
                output.seek(0); output.write(waveFileHeader(written.toInt(), rate, channels))
            }
        } finally { try { codec?.stop() } catch (_: Exception) {}; codec?.release(); extractor.release() }
    }

}
