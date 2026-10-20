package com.lumen.studio

import android.graphics.*
import android.test.InstrumentationTestCase
import com.lumen.studio.graphics.*

@Suppress("DEPRECATION")
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class GraphicsPipelineTest: InstrumentationTestCase() {
    fun testMedia3CompositionWithTimedCatalogLayers() {
        val context=instrumentation.targetContext
        val catalog=GraphicsCatalog.install(context.assets)
        val image=java.io.File(context.cacheDir,"graphics-pipeline-source-${java.util.UUID.randomUUID()}.png")
        Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888).also { source ->
            try { source.eraseColor(Color.rgb(80,140,200)); image.outputStream().use { source.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { source.recycle() }
        }
        val done=java.util.concurrent.CountDownLatch(1)
        val failure=java.util.concurrent.atomic.AtomicReference<Exception?>()
        val completed=java.util.concurrent.atomic.AtomicReference<java.io.File?>()
        val renderer=BackgroundCompositionRenderer(context,java.util.concurrent.Executor { it.run() }) { event ->
            when(event) {
                is RenderEvent.Completed -> { completed.set(event.file); done.countDown() }
                is RenderEvent.Failed -> { failure.set(event.cause); done.countDown() }
                else -> Unit
            }
        }
        try {
            val visuals=catalog.effects.mapIndexed { i,preset -> VisualLayer(kind="effect",name=preset.info.id,startMs=i*100L,endMs=(i+1)*100L) }
            val texts=catalog.typography.mapIndexed { i,preset -> TextLayer(text="Lumen",style=preset.info.id,animation=catalog.animations[i%catalog.animations.size].info.id,startMs=i*100L,endMs=(i+1)*100L) }
            renderer.render(EditorState(listOf(EditorClip(android.net.Uri.fromFile(image).toString(),"Catalog fixture",false,imageDurationMs=5000)),ratio="16:9",projectVisuals=visuals,projectTexts=texts),360)
            assertTrue("Media3 render timed out",done.await(120,java.util.concurrent.TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Media3 catalog render failed",it) }
            val file=completed.get() ?: error("No rendered output")
            assertTrue(file.length()>0)
            val metadata=android.media.MediaMetadataRetriever()
            try { metadata.setDataSource(file.absolutePath); assertEquals("640",metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)); assertEquals("360",metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)) } finally { metadata.release() }
        } finally { renderer.close(); completed.get()?.delete(); image.delete() }
    }
    fun testCatalogShadersAndBitmapOrientation() {
        val catalog=GraphicsCatalog.install(instrumentation.targetContext.assets)
        assertTrue(catalog.filters.size>=40); assertTrue(catalog.effects.size>=40)
        assertTrue(catalog.typography.size>=40); assertTrue(catalog.animations.size>=40)
        val input=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        Canvas(input).apply {
            drawColor(Color.GREEN)
            drawRect(0f,0f,32f,32f,Paint().apply { color=Color.RED })
            drawRect(32f,32f,64f,64f,Paint().apply { color=Color.BLUE })
        }
        try {
            BitmapShaderRenderer().use { gpu ->
                val identity=catalog.effects.first().let { it.copy(uniforms=it.uniforms+("uAmount" to listOf(0f))) }
                val output=gpu.render(input,identity,0)
                try {
                    for((x,y) in listOf(8 to 8,48 to 8,8 to 48,48 to 48)) {
                        val expected=input.getPixel(x,y); val actual=output.getPixel(x,y)
                        assertTrue("Red channel/orientation",kotlin.math.abs(Color.red(expected)-Color.red(actual))<=2)
                        assertTrue("Blue channel/orientation",kotlin.math.abs(Color.blue(expected)-Color.blue(actual))<=2)
                        assertTrue("Green channel",kotlin.math.abs(Color.green(expected)-Color.green(actual))<=2)
                    }
                } finally { output.recycle() }
                catalog.effects.forEach { preset -> gpu.render(input,preset,750_000).also { result -> assertEquals(64,result.width); assertEquals(255,Color.alpha(result.getPixel(32,32))); result.recycle() } }
            }
        } finally { input.recycle() }
    }
    fun testAllTextStylesAndAnimationEndpoints() {
        val catalog=GraphicsCatalog.install(instrumentation.targetContext.assets)
        val bitmap=Bitmap.createBitmap(480,270,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(bitmap)
            catalog.typography.forEach { preset ->
                bitmap.eraseColor(Color.TRANSPARENT)
                CatalogTextRenderer.draw(canvas,TextLayer(text="Lumen\nStudio",style=preset.info.id,shadow=true),480,270,1000)
                val pixels=IntArray(480*270); bitmap.getPixels(pixels,0,480,0,0,480,270)
                assertTrue("Empty typography ${preset.info.id}",pixels.any { Color.alpha(it)>0 })
            }
            catalog.animations.forEach { animation ->
                val end=animation.pose(animation.durationMs+100)
                assertTrue(end.alpha in 0f..1f && end.reveal in 0f..1f)
                animation.tracks.forEach { (_,track) -> assertEquals(track.keys.last().value,track.sample(1f)) }
            }
        } finally { bitmap.recycle() }
    }
}
