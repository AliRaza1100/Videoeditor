package com.lumen.studio

import android.graphics.Bitmap
import android.graphics.Color
import android.media.*
import android.net.Uri
import android.opengl.*
import android.test.InstrumentationTestCase
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Suppress("DEPRECATION")
@UnstableApi
class RenderPipelineTest : InstrumentationTestCase() {
    fun testRotatedImageExportResolutionAndPresets() {
        val context=instrumentation.targetContext
        val folder=File(context.filesDir,"render-tests").apply { mkdirs() }
        val image=File(folder,"rotation.png")
        Bitmap.createBitmap(120,240,Bitmap.Config.ARGB_8888).apply {
            val canvas=android.graphics.Canvas(this); canvas.drawColor(Color.RED)
            canvas.drawRect(60f,0f,120f,240f,android.graphics.Paint().apply { color=Color.BLUE })
            image.outputStream().use { compress(Bitmap.CompressFormat.PNG,100,it) }; recycle()
        }
        val clip=EditorClip(Uri.fromFile(image).toString(),"Rotation",false,imageDurationMs=700,rotation=90f)
        val state=EditorState(listOf(clip))
        for(resolution in listOf(360,480,720,1020,2000)) {
            val plan=TimelineEngine.build(context,state,exportResolution=resolution)
            assertEquals(resolution,plan.width); assertEquals(resolution*2,plan.height)
        }
        val output=File(folder,"rotated-360.mp4")
        instrumentation.runOnMainSync { VideoExport.start(context,"rotate-360",state,Uri.fromFile(output),360) }
        val deadline=System.currentTimeMillis()+120000
        while(VideoExport.busy && System.currentTimeMillis()<deadline) Thread.sleep(100)
        assertFalse("Export timed out",VideoExport.busy); assertEquals("Video exported successfully.",VideoExport.message)
        val metadata=MediaMetadataRetriever()
        try {
            metadata.setDataSource(output.absolutePath)
            val width=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
            val height=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
            assertEquals(360,minOf(width,height)); assertEquals(720,maxOf(width,height))
            val frame=metadata.getFrameAtTime(200000)!!
            val top=frame.getPixel(180,320); val bottom=frame.getPixel(180,400)
            assertTrue("Rotation did not turn side-by-side colors into top/bottom",kotlin.math.abs(Color.red(top)-Color.red(bottom))>100)
            frame.recycle()
        } finally { metadata.release() }
        assertTrue(EditorPresets.filters.values.flatten().distinct().size>=25)
        assertTrue(EditorPresets.stickers.size>=30)
        assertTrue(TimelineEngine.color(clip.copy(filter="Golden")).array.toList()!=TimelineEngine.color(clip.copy(filter="Arctic")).array.toList())
    }
    fun testTrimSplitLayersAudioExportAndReopen() {
        val context=instrumentation.targetContext
        val folder=File(context.filesDir,"render-tests").apply { mkdirs() }
        val source=File(folder,"source.mp4")
        createVideo(source)
        val overlay=File(folder,"overlay.png")
        Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE); overlay.outputStream().use { compress(Bitmap.CompressFormat.PNG,100,it) }; recycle() }
        val audio=File(folder,"sound.wav")
        val pcm=ByteBuffer.allocate(48000*2*2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(96000) { pcm.putShort((kotlin.math.sin(it*2*Math.PI*440/48000)*5000).toInt().toShort()) }
        audio.outputStream().use { it.write(waveFileHeader(pcm.array().size,48000,1)); it.write(pcm.array()) }
        val original=EditorClip(Uri.fromFile(source).toString(),"Source",true,sourceDurationMs=3000)
        val trimmed=ClipEdits.trim(original,500,2500)
        val split=ClipEdits.split(EditorState(listOf(trimmed)),1500)
        val first=split.clips[0].copy(filter="Mono",muted=true,texts=listOf(TextLayer(text="LUMEN TEST",size=20f)))
        val second=split.clips[1].copy(overlays=listOf(ImageLayer(uri=Uri.fromFile(overlay).toString())),effect="Warm glow",
            sounds=listOf(SoundLayer(uri=Uri.fromFile(audio).toString(),name="Tone",volume=.5f,startMs=100)))
        val state=EditorState(listOf(first,second))
        val projectId="render-${System.currentTimeMillis()}"
        ProjectStore.save(context,SavedProject(projectId,"Render Test",System.currentTimeMillis(),state))
        assertEquals(state,ProjectStore.load(context,projectId)!!.state)
        val output=File(folder,"edited.mp4")
        instrumentation.runOnMainSync { VideoExport.start(context,projectId,state,Uri.fromFile(output)) }
        val deadline=System.currentTimeMillis()+120000
        while(VideoExport.busy && System.currentTimeMillis() < deadline) Thread.sleep(200)
        assertFalse("Export timed out",VideoExport.busy)
        assertEquals("Video exported successfully.",VideoExport.message)
        assertTrue(output.length() > 1000)
        val metadata=MediaMetadataRetriever()
        try {
            metadata.setDataSource(output.absolutePath)
            val duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("Trimmed export duration=$duration",duration in 1800..2300)
            assertEquals("yes",metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            val mono=metadata.getFrameAtTime(400000,MediaMetadataRetriever.OPTION_CLOSEST)!!
            val pixel=mono.getPixel(20,20)
            assertTrue("Mono filter not encoded",kotlin.math.abs(Color.red(pixel)-Color.green(pixel)) < 25 && kotlin.math.abs(Color.blue(pixel)-Color.green(pixel)) < 25)
            val withOverlay=metadata.getFrameAtTime(1600000,MediaMetadataRetriever.OPTION_CLOSEST)!!
            val blue=withOverlay.getPixel((withOverlay.width*.8).toInt(),(withOverlay.height*.2).toInt())
            assertTrue("Image overlay not encoded",Color.blue(blue) > Color.red(blue)+50)
            mono.recycle(); withOverlay.recycle()
        } finally { metadata.release() }
        assertTrue("Soundtrack was silent",hasNonSilentAudio(output))
    }
    fun testCaptionTimingAndCurveExport() {
        androidx.media3.effect.DebugTraceUtil.enableTracing=true
        val context=instrumentation.targetContext
        val folder=File(context.filesDir,"render-tests").apply { mkdirs() }
        val source=File(folder,"caption-source.mp4"); createVideo(source)
        val captions=CaptionParser.parse("1\n00:00:00,500 --> 00:00:01,500\nCAPTION\n",3000)
        assertEquals(500L,captions.single().startMs)
        val clip=EditorClip(Uri.fromFile(source).toString(),"Caption test",true,sourceDurationMs=3000,
            texts=captions.map { it.copy(y=0f,size=40f,animation="Fade") },speedCurve=listOf(1f,1.5f,2f,1.5f,1f))
        val expected=ClipEdits.duration(clip)
        assertTrue(expected in 1900..2300)
        assertTrue(kotlin.math.abs(ClipEdits.sourcePosition(clip,ClipEdits.outputPosition(clip,1500.0).toLong())-1500)<5)
        val state=EditorState(listOf(clip),ratio="4:5")
        val output=File(folder,"caption-curve.mp4")
        instrumentation.runOnMainSync { VideoExport.start(context,"caption-test",state,Uri.fromFile(output)) }
        val deadline=System.currentTimeMillis()+120000
        while(VideoExport.busy && System.currentTimeMillis()<deadline) Thread.sleep(200)
        assertFalse(VideoExport.busy); assertEquals("Video exported successfully.",VideoExport.message)
        val retriever=MediaMetadataRetriever()
        try {
            retriever.setDataSource(output.absolutePath)
            val duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("Curve duration expected=$expected actual=$duration",kotlin.math.abs(duration-expected)<300)
            val before=retriever.getFrameAtTime(200000,MediaMetadataRetriever.OPTION_CLOSEST)!!
            val during=retriever.getFrameAtTime(1000000,MediaMetadataRetriever.OPTION_CLOSEST)!!
            fun whites(bitmap: Bitmap): Int { var total=0; for(y in bitmap.height/3 until bitmap.height*2/3 step 3) for(x in 0 until bitmap.width step 3) { val pixel=bitmap.getPixel(x,y); if(Color.red(pixel)>180 && Color.green(pixel)>180 && Color.blue(pixel)>180) total++ }; return total }
            assertTrue("Timed caption must appear only inside cue",whites(during)>whites(before)+20)
            assertTrue("4:5 ratio",kotlin.math.abs(during.width.toFloat()/during.height-.8f)<.01f)
            before.recycle(); during.recycle()
        } finally { retriever.release() }
    }
    fun testDraftsAreNeverSilentlyEvictedAndNeverDeleteOriginal() {
        val context=instrumentation.targetContext
        ProjectStore.list(context).forEach { ProjectStore.delete(context,it.id) }
        val original=File(context.filesDir,"retention-original.png").apply { writeText("original") }
        val prefix="fifo-${System.currentTimeMillis()}"
        repeat(11) { index -> ProjectStore.save(context,SavedProject("$prefix-$index","Project $index",index.toLong(),EditorState(listOf(EditorClip(Uri.fromFile(original).toString(),"Original",false))))) }
        val projects=ProjectStore.list(context)
        assertEquals(11,projects.size); assertNotNull(ProjectStore.load(context,"$prefix-0")); assertNotNull(ProjectStore.load(context,"$prefix-10"))
        assertTrue(original.exists())
        // Explicitly deleted projects cannot be recreated by a late callback.
        ProjectStore.delete(context,"$prefix-0")
        ProjectStore.save(context,SavedProject("$prefix-0","Old callback",999,EditorState(emptyList())))
        assertNull(ProjectStore.load(context,"$prefix-0")); assertEquals(10,ProjectStore.list(context).size)
    }
    private fun hasNonSilentAudio(file: File): Boolean {
        val extractor=MediaExtractor(); var decoder: MediaCodec?=null
        try {
            extractor.setDataSource(file.absolutePath)
            val index=(0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") }
            extractor.selectTrack(index); val format=extractor.getTrackFormat(index)
            val codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!); decoder=codec
            codec.configure(format,null,null,0); codec.start()
            var inputEnded=false; val info=MediaCodec.BufferInfo(); val deadline=System.currentTimeMillis()+15000
            while(System.currentTimeMillis() < deadline) {
                if(!inputEnded) { val slot=codec.dequeueInputBuffer(10000); if(slot >= 0) {
                    val data=codec.getInputBuffer(slot)!!; data.clear(); val size=extractor.readSampleData(data,0)
                    if(size < 0) { codec.queueInputBuffer(slot,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded=true }
                    else { codec.queueInputBuffer(slot,0,size,extractor.sampleTime,0); extractor.advance() }
                } }
                val slot=codec.dequeueOutputBuffer(info,10000)
                if(slot >= 0) {
                    val data=codec.getOutputBuffer(slot)!!.order(ByteOrder.nativeOrder()); data.position(info.offset); data.limit(info.offset+info.size)
                    while(data.remaining() >= 2) if(kotlin.math.abs(data.short.toInt()) > 300) return true
                    codec.releaseOutputBuffer(slot,false)
                    if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            return false
        } finally { try { decoder?.stop() } catch(_: Exception) {}; decoder?.release(); extractor.release() }
    }
    internal fun createVideo(file: File,changingColors: Boolean=false) {
        val codec=MediaCodec.createEncoderByType("video/avc")
        val format=MediaFormat.createVideoFormat("video/avc",320,240).apply { setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface); setInteger(MediaFormat.KEY_BIT_RATE,400000); setInteger(MediaFormat.KEY_FRAME_RATE,10); setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1) }
        codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface=codec.createInputSurface(); codec.start()
        val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY); val versions=IntArray(2); EGL14.eglInitialize(display,versions,0,versions,1)
        val configs=arrayOfNulls<EGLConfig>(1); val count=IntArray(1)
        val attributes=intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,0x3142,1,EGL14.EGL_NONE)
        check(EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0))
        val eglContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        val eglSurface=EGL14.eglCreateWindowSurface(display,configs[0],surface,intArrayOf(EGL14.EGL_NONE),0)
        check(EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext))
        val muxer=MediaMuxer(file.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track=-1; var muxing=false; val info=MediaCodec.BufferInfo()
        fun drain(ended: Boolean) {
            val deadline=System.currentTimeMillis()+15000
            while(System.currentTimeMillis() < deadline) {
                val index=codec.dequeueOutputBuffer(info,if(ended) 10000 else 0)
                if(index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track=muxer.addTrack(codec.outputFormat); muxer.start(); muxing=true }
                else if(index >= 0) {
                    val data=codec.getOutputBuffer(index)!!
                    if(info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) { data.position(info.offset); data.limit(info.offset+info.size); muxer.writeSampleData(track,data,info) }
                    codec.releaseOutputBuffer(index,false)
                    if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                } else if(!ended) return
            }
            error("Fixture encoder timed out")
        }
        try {
            repeat(30) { frame -> GLES20.glViewport(0,0,320,240); GLES20.glClearColor(if(!changingColors || frame<10) 1f else 0f,if(changingColors && frame in 10..19) 1f else 0f,if(changingColors && frame>=20) 1f else 0f,1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                EGLExt.eglPresentationTimeANDROID(display,eglSurface,frame*100000000L); check(EGL14.eglSwapBuffers(display,eglSurface)); drain(false) }
            codec.signalEndOfInputStream(); drain(true)
        } finally {
            codec.stop(); codec.release(); if(muxing) muxer.stop(); muxer.release()
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT); EGL14.eglDestroySurface(display,eglSurface); EGL14.eglDestroyContext(display,eglContext); EGL14.eglTerminate(display); surface.release()
        }
    }
}

