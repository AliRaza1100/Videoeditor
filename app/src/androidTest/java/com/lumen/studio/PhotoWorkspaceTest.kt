package com.lumen.studio

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.test.InstrumentationTestCase
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@Suppress("DEPRECATION")
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class PhotoWorkspaceTest: InstrumentationTestCase() {
    private val context get()=instrumentation.targetContext
    override fun setUp() { super.setUp(); assertTrue(context.packageName.endsWith(".qa")); AppPreferences.setLanguage(context,"en") }
    private fun photo(color: Int): File=File(context.cacheDir,"photo-${UUID.randomUUID()}.png").also { file ->
        Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888).also { bitmap -> try { bitmap.eraseColor(color); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() } }
    }
    private fun views(v: View): List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    fun testCollagePixelsAndPngJpegEncoding() {
        val red=photo(Color.RED); val blue=photo(Color.BLUE)
        val renderer=PhotoRenderer(context)
        try {
            val state=EditorState(listOf(EditorClip(Uri.fromFile(red).toString(),"Red",false),EditorClip(Uri.fromFile(blue).toString(),"Blue",false)),ratio="1:1",background=Color.WHITE,photoEditor=true,photoLayout="columns2",photoGap=0,photoCorner=0)
            val bitmap=renderer.render(state,512)
            try {
                assertEquals(512,bitmap.width); assertEquals(512,bitmap.height)
                assertEquals(Color.RED,bitmap.getPixel(100,256)); assertEquals(Color.BLUE,bitmap.getPixel(400,256))
                for(format in listOf(Bitmap.CompressFormat.PNG,Bitmap.CompressFormat.JPEG)) {
                    val output=ByteArrayOutputStream(); assertTrue(bitmap.compress(format,95,output))
                    val bytes=output.toByteArray(); val decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.size)!!
                    try { assertEquals(512,decoded.width); assertEquals(512,decoded.height) } finally { decoded.recycle() }
                }
            } finally { bitmap.recycle() }
        } finally { renderer.close(); red.delete(); blue.delete() }
    }
    fun testPhotoWorkspaceHasNoVideoControlsAndDraftReopens() {
        val photo=photo(Color.GREEN); val id="photo-workspace-${UUID.randomUUID()}"
        ProjectStore.save(context,SavedProject(id,"Photo workspace",System.currentTimeMillis(),EditorState(listOf(EditorClip(Uri.fromFile(photo).toString(),"Photo",false)),photoEditor=true,photoLayout="single")))
        try {
            val activity=instrumentation.startActivitySync(Intent(context,PhotoEditorActivity::class.java).putExtra("project_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            try { instrumentation.runOnMainSync {
                val all=views(activity.window.decorView); val labels=all.filterIsInstance<TextView>().map { it.text.toString() }
                for(tool in listOf("Photos","Crop","Text","Filters","Collage","Export")) assertTrue("Missing photo tool $tool",tool in labels)
                assertFalse("Sound in photo workspace","Sound" in labels); assertFalse("Video animation in photo workspace","Animation" in labels)
                assertTrue(all.none { it is TimelineRegionView || it is EditorPreviewView })
            } } finally { instrumentation.runOnMainSync { activity.finish() } }
            val reopened=ProjectStore.load(context,id)!!; assertEquals(true,reopened.state.photoEditor); assertEquals("single",reopened.state.photoLayout)
        } finally { ProjectStore.delete(context,id); photo.delete() }
    }
    fun testCombinedTextFormattingSurvivesDraftAndChangesRenderedPixels() {
        val id="photo-format-${UUID.randomUUID()}"
        val layer=TextLayer(text="Studio",font="Sans",style="Regular",size=48f,color=Color.WHITE,shadow=false,bold=true,italic=true,underline=true,backgroundColor=Color.BLUE,alignment="Right")
        val draft=SavedProject(id,"Text formatting",System.currentTimeMillis(),EditorState(emptyList(),photoEditor=true,projectTexts=listOf(layer)))
        try {
            ProjectStore.save(context,draft)
            val restored=ProjectStore.load(context,id)!!.state.projectTexts!!.single()
            assertEquals(layer,restored)
            fun pixels(text: TextLayer): Bitmap=Bitmap.createBitmap(360,240,Bitmap.Config.ARGB_8888).also { TextPresets.draw(android.graphics.Canvas(it),text,360,240,750) }
            val formatted=pixels(restored)
            val plain=pixels(restored.copy(bold=false,italic=false,underline=false))
            val withoutUnderline=pixels(restored.copy(underline=false))
            try {
                assertFalse("Combined styling must affect exported pixels",formatted.sameAs(plain))
                assertFalse("Underline must work together with bold and italic",formatted.sameAs(withoutUnderline))
                assertTrue("Text highlight must render",(0 until formatted.width).any { x -> (0 until formatted.height).any { y -> formatted.getPixel(x,y)==Color.BLUE } })
                val bounds=PhotoTextGeometry.bounds(360,240,restored)
                assertTrue(bounds.width()>0 && bounds.height()>0)
            } finally { formatted.recycle();plain.recycle();withoutUnderline.recycle() }
        } finally { ProjectStore.delete(context,id) }
    }
    fun testFreeCropKeepsFiniteBounds() {
        instrumentation.runOnMainSync {
            val bitmap=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888)
            try {
                val crop=CropEditorView(context,android.graphics.RectF(0f,0f,1f,1f)); crop.image=bitmap
                crop.setRatio(1f); crop.setRatio(0f)
                assertTrue(crop.crop.left.isFinite() && crop.crop.top.isFinite() && crop.crop.width()>0 && crop.crop.height()>0)
                crop.reset(); assertEquals(1f,crop.crop.width())
            } finally { bitmap.recycle() }
        }
    }
    fun testBottomNavigationHasOnlyHomeProjectsSettings() {
        instrumentation.runOnMainSync {
            val nav=BottomNavigation(context,"home") {}
            val descriptions=views(nav).mapNotNull { it.contentDescription?.toString() }
            assertTrue("Home" in descriptions && "Projects" in descriptions && "Settings" in descriptions)
            assertFalse("Saved" in descriptions); assertEquals(3,nav.childCount)
        }
    }
    fun testPhotoThumbnailSelectionAppliesAndCanContinueEditing() {
        val file=photo(Color.rgb(120,165,200)); val id="thumbnail-${UUID.randomUUID()}"
        ProjectStore.save(context,SavedProject(id,"Thumbnail flow",System.currentTimeMillis(),EditorState(listOf(EditorClip(Uri.fromFile(file).toString(),"Photo",false,filterIntensity=0f)),photoEditor=true)))
        val activity=instrumentation.startActivitySync(Intent(context,PhotoEditorActivity::class.java).putExtra("project_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun click(label: String) { instrumentation.runOnMainSync { views(activity.window.decorView).firstOrNull { it.isClickable && it.contentDescription==label }?.performClick() ?: views(activity.window.decorView).filterIsInstance<TextView>().single { it.text.toString()==label }.performClick() } }
        fun waitForThumbnail(description: String) {
            val deadline=System.currentTimeMillis()+15000
            while(System.currentTimeMillis()<deadline) {
                var ready=false
                instrumentation.runOnMainSync { ready=views(activity.window.decorView).filterIsInstance<android.widget.ImageView>().any { it.contentDescription==description && it.drawable!=null } }
                if(ready) return
                Thread.sleep(100)
            }
            fail("No rendered photo thumbnail: $description")
        }
        try {
            click("Collage")
            waitForThumbnail("Preview of 2 columns")
            instrumentation.runOnMainSync {
                val all=views(activity.window.decorView)
                assertEquals(PhotoLayouts.all(context).size,all.filterIsInstance<PhotoCollageThumbnail>().size)
                all.single { it.contentDescription=="2 columns, 2 photos" }.performClick()
            }
            click("Edit photo")
            click("Filters")
            waitForThumbnail("Preview of Photo Natural")
            instrumentation.runOnMainSync {
                val card=views(activity.window.decorView).single { it.contentDescription=="Photo filter: Photo Natural" }
                card.performClick(); assertTrue(card.isSelected)
                val strength=views(activity.window.decorView).filterIsInstance<android.widget.SeekBar>().single { it.contentDescription=="Filter strength" }
                assertEquals("Selecting a filter at zero strength must show its result",100,strength.progress)
            }
            click("Edit photo")
            click("Canvas") // Choosing a thumbnail must leave the image editable.
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync() }
        try {
            val saved=ProjectStore.load(context,id)!!.state
            assertEquals("columns2",saved.photoLayout); assertEquals("Photo Natural",saved.clips.first().filter)
            assertEquals(1f,saved.clips.first().filterIntensity)
        } finally { ProjectStore.delete(context,id); file.delete() }
    }

}
