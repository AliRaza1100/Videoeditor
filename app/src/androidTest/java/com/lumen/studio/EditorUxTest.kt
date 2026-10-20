package com.lumen.studio

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.test.InstrumentationTestCase
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.util.UUID

@Suppress("DEPRECATION")
class EditorUxTest : InstrumentationTestCase() {
    private val context get()=instrumentation.targetContext
    private fun views(view: View): List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).mapNotNull { view.getChildAt(it) }.flatMap { views(it) } else emptyList()
    private fun root(activity: Activity)=activity.findViewById<View>(android.R.id.content)
    private fun start(name: String,intent: Intent=Intent()): Activity = instrumentation.startActivitySync(intent.setClassName(context.packageName,"com.lumen.studio.$name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun waitFor(check: ()->Boolean) { repeat(400) { if(check()) return; Thread.sleep(100) }; fail("UI did not become ready") }
    private fun find(activity: Activity,value: String)=views(root(activity)).firstOrNull { it.isShown && it.isClickable && ((it is TextView && it.text.toString()==value) || it.contentDescription?.toString()==value) }
    private fun click(activity: Activity,value: String) {
        waitFor { find(activity,value)?.let { it.width>0 && it.height>0 && it.isAttachedToWindow }==true }
        val target=find(activity,value) ?: error("Missing control: $value")
        instrumentation.runOnMainSync { target.requestRectangleOnScreen(Rect(0,0,target.width,target.height),true) }
        instrumentation.waitForIdleSync()
        val bounds=Rect(); val screen=Rect(); root(activity).getGlobalVisibleRect(screen)
        val visible=target.getGlobalVisibleRect(bounds) && screen.contains(bounds)
        if(!visible) capture(activity,"outside-$value")
        assertTrue("Control outside screen: $value bounds=$bounds screen=$screen size=${target.width}x${target.height}",visible)
        assertTrue(target.isEnabled)
        instrumentation.runOnMainSync { target.performClick() }; instrumentation.waitForIdleSync()
    }
    private fun capture(activity: Activity,name: String) {
        val image=instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        val file=File(context.filesDir,"ux-checks/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
    }
    private fun ready(editor: EditorActivity) { waitFor { editor.previewRendered } }
    fun testStableWorkbenchPresetsRotationAndResolution() {
        AppPreferences.setLanguage(context,"en"); AppPreferences.setLight(context,false)
        val photo=File(context.filesDir,"ux-portrait.png")
        Bitmap.createBitmap(180,320,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE); photo.outputStream().use { compress(Bitmap.CompressFormat.PNG,100,it) }; recycle() }
        val id="new-ux-${UUID.randomUUID()}"
        val editor=start("EditorActivity",Intent().putExtra("project_id",id).putStringArrayListExtra("uris",arrayListOf(Uri.fromFile(photo).toString())).putStringArrayListExtra("names",arrayListOf("Portrait"))) as EditorActivity
        try {
            ready(editor); val surface=views(root(editor)).filterIsInstance<EditorPreviewView>().single()
            val original=Rect(); surface.getGlobalVisibleRect(original)
            assertNotNull(find(editor,"Edit")); assertNotNull(find(editor,"Sound")); assertNotNull(find(editor,"Crop"))
            click(editor,"Filters"); Thread.sleep(300)
            val fitted=Rect(); surface.getGlobalVisibleRect(fitted); assertEquals("Tool panel resized video",original,fitted)
            val golden=views(root(editor)).first { it.contentDescription=="Golden" && it.isClickable }
            instrumentation.runOnMainSync { golden.performClick() }; ready(editor)
            assertEquals("Golden",ProjectStore.load(context,id)!!.state.clips[0].filter)
            assertNull("Filters should not require Apply",find(editor,"Apply")); click(editor,"Back to tools")
            click(editor,"Edit"); click(editor,"Rotate"); click(editor,"Rotate preset: 90"); ready(editor); click(editor,"Back to tools")
            assertEquals(90f,ProjectStore.load(context,id)!!.state.clips[0].rotation)
            click(editor,"Effects")
            val effect=views(root(editor)).first { it.contentDescription=="Vignette" && it.isClickable }
            instrumentation.runOnMainSync { effect.performClick() }; ready(editor)
            assertEquals("Vignette",ProjectStore.load(context,id)!!.state.clips[0].effect)
            click(editor,"None"); ready(editor); assertEquals("None",ProjectStore.load(context,id)!!.state.clips[0].effect)
            click(editor,"Back to tools"); click(editor,"Ratio"); click(editor,"Ratio 16:9"); ready(editor)
            assertTrue(kotlin.math.abs(surface.width.toFloat()/surface.height-16f/9)<.04f)
            click(editor,"Back to tools"); click(editor,"Back"); assertNotNull(find(editor,"Crop"))
            capture(editor,"workbench-v102")
            click(editor,"EXPORT"); click(editor,"Export video (entire timeline)")
            for(resolution in listOf("360p","480p","720p","1020p","2000p")) assertNotNull("Missing resolution $resolution",find(editor,resolution))
            capture(editor,"export-resolutions-v102")
        } finally { instrumentation.runOnMainSync { editor.finish() } }
    }
    fun testEditorPlaybackInlineToolsUndoAndBounds() {
        AppPreferences.setLanguage(context,"en"); AppPreferences.setLight(context,false)
        val source=File(context.filesDir,"render-tests/source.mp4"); assertTrue("Run RenderPipelineTest first",source.exists())
        val photo=File(context.filesDir,"ux-portrait.png")
        Bitmap.createBitmap(180,320,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE); photo.outputStream().use { compress(Bitmap.CompressFormat.PNG,100,it) }; recycle() }
        val id="ux-${UUID.randomUUID()}"
        val editor=start("EditorActivity",Intent().putExtra("project_id",id).putStringArrayListExtra("uris",arrayListOf(Uri.fromFile(source).toString(),Uri.fromFile(photo).toString())).putStringArrayListExtra("names",arrayListOf("Source video","Portrait photo"))) as EditorActivity
        ready(editor)
        val surface=views(root(editor)).filterIsInstance<EditorPreviewView>().single()
        val bounds=Rect(); val screen=Rect(); root(editor).getGlobalVisibleRect(screen); assertTrue(surface.getGlobalVisibleRect(bounds) && screen.contains(bounds))
        assertTrue("Preview stretched",kotlin.math.abs(surface.width.toFloat()/surface.height-4f/3)<.03f)
        capture(editor,"editor-fitted")
        click(editor,"Play or pause")
        waitFor { views(root(editor)).filterIsInstance<SeekBar>().first().progress>350 }
        click(editor,"Play or pause")
        click(editor,"Clip 1: Source video")
        assertNull(editor.toolsDialog)
        click(editor,"Crop")
        waitFor { views(root(editor)).filterIsInstance<CropEditorView>().single().image!=null }
        click(editor,"1:1"); ready(editor); click(editor,"Back to tools")
        val cropped=ProjectStore.load(context,id)!!.state.clips[0]
        assertTrue(cropped.cropLeft> -1f && cropped.cropRight<1f)
        click(editor,"Undo"); ready(editor); assertEquals(-1f,ProjectStore.load(context,id)!!.state.clips[0].cropLeft)
        click(editor,"Redo"); ready(editor); assertTrue(ProjectStore.load(context,id)!!.state.clips[0].cropLeft> -1f)
        click(editor,"Filters"); instrumentation.runOnMainSync { views(root(editor)).first { it.contentDescription=="Warm" && it.isClickable }.performClick() }; ready(editor)
        assertEquals("Warm",ProjectStore.load(context,id)!!.state.clips[0].filter)
        assertEquals("Original",ProjectStore.load(context,id)!!.state.clips[1].filter)
        click(editor,"Back to tools"); click(editor,"Edit")
        click(editor,"Ratio"); click(editor,"Ratio 9:16"); ready(editor)
        assertTrue("Portrait ratio stretched",kotlin.math.abs(surface.width.toFloat()/surface.height-9f/16)<.03f)
        capture(editor,"editor-inline-ratio")
        click(editor,"Back to tools")
        instrumentation.runOnMainSync { editor.finish() }
    }
    fun testFilterLongPressDragAppliesToSelectedClip() {
        AppPreferences.setLanguage(context,"en"); AppPreferences.setLight(context,false)
        val source=File(context.filesDir,"render-tests/source.mp4"); val photo=File(context.filesDir,"ux-portrait.png")
        assertTrue(source.exists() && photo.exists())
        val id="drag-${UUID.randomUUID()}"
        val editor=start("EditorActivity",Intent().putExtra("project_id",id).putStringArrayListExtra("uris",arrayListOf(Uri.fromFile(source).toString(),Uri.fromFile(photo).toString())).putStringArrayListExtra("names",arrayListOf("Drag video","Photo"))) as EditorActivity
        ready(editor); click(editor,"Filters")
        val chip=views(root(editor)).first { it.contentDescription=="Warm" && it.isClickable }; val clip=find(editor,"Clip 1: Drag video")!!
        val a=Rect(); val b=Rect()
        waitFor { chip.width>0 && chip.height>0 && chip.getGlobalVisibleRect(a) && clip.getGlobalVisibleRect(b) }
        capture(editor,"filter-drag-start")
        android.util.Log.i("LumenDragTest","Source=$a target=$b longClickable=${chip.isLongClickable}")
        val down=android.os.SystemClock.uptimeMillis()
        fun event(action: Int,x: Float,y: Float) {
            val motion=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x,y,0)
            motion.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(instrumentation.uiAutomation.injectInputEvent(motion,true)); motion.recycle()
        }
        event(android.view.MotionEvent.ACTION_DOWN,a.exactCenterX(),a.exactCenterY())
        Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong()+250)
        for(step in 1..16) {
            val fraction=step/16f
            event(android.view.MotionEvent.ACTION_MOVE,a.exactCenterX()+(b.exactCenterX()-a.exactCenterX())*fraction,a.exactCenterY()+(b.exactCenterY()-a.exactCenterY())*fraction)
            Thread.sleep(30)
        }
        event(android.view.MotionEvent.ACTION_UP,b.exactCenterX(),b.exactCenterY())
        try { waitFor { ProjectStore.load(context,id)?.state?.clips?.first()?.filter=="Warm" } } catch(error: Throwable) { capture(editor,"filter-drag-end"); throw error }; ready(editor)
        assertEquals("Original",ProjectStore.load(context,id)!!.state.clips[1].filter)
        click(editor,"Undo"); ready(editor); assertEquals("Original",ProjectStore.load(context,id)!!.state.clips[0].filter)
        instrumentation.runOnMainSync { editor.finish() }
    }
    fun testVideoPreviewVisibleAndContinuousPlayback() {
        AppPreferences.setLanguage(context,"en"); AppPreferences.setLight(context,false)
        val source=File(context.filesDir,"render-tests/source.mp4"); assertTrue(source.exists())
        val editor=start("EditorActivity",Intent().putStringArrayListExtra("uris",arrayListOf(Uri.fromFile(source).toString())).putStringArrayListExtra("names",arrayListOf("Visible video"))) as EditorActivity
        ready(editor)
        val surface=views(root(editor)).filterIsInstance<EditorPreviewView>().single()
        fun visible(): Boolean {
            val rect=Rect(); if(!surface.getGlobalVisibleRect(rect)) return false
            val image=instrumentation.uiAutomation.takeScreenshot() ?: return false
            val color=image.getPixel(rect.centerX(),rect.centerY()); image.recycle()
            return Color.red(color)>180 && Color.blue(color)<80
        }
        click(editor,"Play or pause")
        waitFor { views(root(editor)).filterIsInstance<SeekBar>().first().progress>2000 }
        capture(editor,"video-window-observed")
        val raw=surface.bitmap ?: error("No texture bitmap")
        File(context.filesDir,"ux-checks/video-texture.png").outputStream().use { raw.compress(Bitmap.CompressFormat.PNG,100,it) }
        android.util.Log.i("LumenPreviewTest","Texture center=${Integer.toHexString(raw.getPixel(raw.width/2,raw.height/2))}"); raw.recycle()
        waitFor { visible() }
        capture(editor,"video-visible-playing")
        click(editor,"Play or pause")
        instrumentation.runOnMainSync { editor.finish() }
    }
    fun testInitialPhotoPreviewVisibleWithoutPlayback() {
        AppPreferences.setLanguage(context,"en"); AppPreferences.setLight(context,false)
        val file=File(context.filesDir,"ux-portrait.png"); assertTrue(file.exists())
        val editor=start("EditorActivity",Intent().putStringArrayListExtra("uris",arrayListOf(Uri.fromFile(file).toString())).putStringArrayListExtra("names",arrayListOf("Initial photo"))) as EditorActivity
        ready(editor)
        val surface=views(root(editor)).filterIsInstance<EditorPreviewView>().single()
        waitFor {
            val bounds=Rect(); surface.getGlobalVisibleRect(bounds)
            val screenshot=instrumentation.uiAutomation.takeScreenshot() ?: return@waitFor false
            val color=screenshot.getPixel(bounds.centerX(),bounds.centerY()); screenshot.recycle()
            Color.blue(color)>180 && Color.red(color)<80
        }
        capture(editor,"initial-photo-visible")
        assertEquals(0,views(root(editor)).filterIsInstance<SeekBar>().first().progress)
        click(editor,"Clip 1: Initial photo")
        click(editor,"Enter fullscreen preview"); ready(editor)
        click(editor,"Back"); ready(editor)
        assertNotNull("Fullscreen Back lost the selected clip",find(editor,"Edit"))
        instrumentation.runOnMainSync { editor.finish() }
    }
    fun testExportedVideoActuallyPlays() {
        AppPreferences.setLanguage(context,"en"); AppPreferences.setLight(context,false)
        val file=File(context.filesDir,"render-tests/edited.mp4"); assertTrue(file.exists())
        val player=start("VideoPlayerActivity",Intent().putExtra("video_uri",Uri.fromFile(file).toString())) as VideoPlayerActivity
        val video=views(root(player)).filterIsInstance<VideoView>().single()
        waitFor { player.prepared && video.isPlaying && video.currentPosition>300 }
        val bounds=Rect(); val screen=Rect(); root(player).getGlobalVisibleRect(screen)
        assertTrue(video.getGlobalVisibleRect(bounds) && screen.contains(bounds))
        assertTrue("Export playback stretched",kotlin.math.abs(video.width.toFloat()/video.height-4f/3)<.03f)
        capture(player,"export-playback")
        click(player,"Play or pause")
        assertFalse(video.isPlaying)
        instrumentation.runOnMainSync { player.finish() }
    }
    fun testSoundGalleryExtractionDeselectAndRemove() {
        AppPreferences.setLanguage(context,"en"); AppPreferences.setLight(context,false)
        if(Build.VERSION.SDK_INT>=33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.READ_MEDIA_VIDEO)
        val source=File(context.filesDir,"render-tests/edited.mp4"); assertTrue(source.exists())
        val values=ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME,"Lumen UX sound fixture.mp4"); put(MediaStore.Video.Media.MIME_TYPE,"video/mp4"); put(MediaStore.Video.Media.DURATION,2000); if(Build.VERSION.SDK_INT>=29) put(MediaStore.Video.Media.IS_PENDING,1) }
        val uri=context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)!!
        try {
            context.contentResolver.openOutputStream(uri)!!.use { output -> source.inputStream().use { it.copyTo(output) } }
            if(Build.VERSION.SDK_INT>=29) context.contentResolver.update(uri,ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING,0) },null,null)
            val before=SoundLibrary.list(context).size
            val sounds=start("SoundLibraryActivity")
            val monitor=instrumentation.addMonitor(MediaPickerActivity::class.java.name,null,false)
            click(sounds,"▣  Extract sound from video")
            val gallery=monitor.waitForActivityWithTimeout(15000) ?: error("In-app gallery did not open")
            assertEquals("video",gallery.intent.getStringExtra("mode")); assertEquals(1,gallery.intent.getIntExtra("max_items",0))
            val grid=views(root(gallery)).filterIsInstance<GridView>().single()
            waitFor { grid.count>0 && grid.childCount>0 }
            instrumentation.runOnMainSync { grid.performItemClick(grid.getChildAt(0),0,grid.adapter.getItemId(0)) }
            assertTrue(find(gallery,"Use selected media")!!.isEnabled)
            instrumentation.runOnMainSync { grid.performItemClick(grid.getChildAt(0),0,grid.adapter.getItemId(0)) }
            assertFalse(find(gallery,"Use selected media")!!.isEnabled)
            instrumentation.runOnMainSync { grid.performItemClick(grid.getChildAt(0),0,grid.adapter.getItemId(0)) }
            click(gallery,"Use selected media"); instrumentation.removeMonitor(monitor)
            waitFor { !SoundLibrary.busy && SoundLibrary.list(context).size>before && find(sounds,"○")!=null }
            click(sounds,"○"); assertTrue(find(sounds,"Apply sound  →")!!.isEnabled)
            click(sounds,"●"); assertFalse(find(sounds,"Select a sound to apply")!!.isEnabled)
            click(sounds,"○"); click(sounds,"Clear selection"); assertFalse(find(sounds,"Select a sound to apply")!!.isEnabled)
            capture(sounds,"sound-deselected")
            instrumentation.runOnMainSync { sounds.finish() }
            val sound=SoundLibrary.list(context).last(); assertTrue(sound.extracted && sound.durationMs>0)
            val photo=File(context.filesDir,"ux-portrait.png")
            val id="ux-audio-${UUID.randomUUID()}"
            ProjectStore.save(context,SavedProject(id,"Sound remove",System.currentTimeMillis(),EditorState(listOf(EditorClip(Uri.fromFile(photo).toString(),"Photo",false,sounds=listOf(SoundLayer(uri=Uri.fromFile(File(sound.path)).toString(),name=sound.name)))))))
            val editor=start("EditorActivity",Intent().putExtra("project_id",id)) as EditorActivity; ready(editor)
            click(editor,"AUDIO: ♫ ${sound.name}"); click(editor,"Delete"); ready(editor)
            assertTrue(ProjectStore.load(context,id)!!.state.clips.single().sounds.isEmpty())
            click(editor,"Undo"); ready(editor); assertEquals(1,ProjectStore.load(context,id)!!.state.clips.single().sounds.size)
            instrumentation.runOnMainSync { editor.finish() }
        } finally { context.contentResolver.delete(uri,null,null) }
    }
}
