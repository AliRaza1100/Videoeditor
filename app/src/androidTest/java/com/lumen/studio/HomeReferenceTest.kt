package com.lumen.studio

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.test.InstrumentationTestCase
import android.view.View
import android.view.ViewGroup
import java.io.File
import java.util.UUID

@Suppress("DEPRECATION")
class HomeReferenceTest: InstrumentationTestCase() {
    private fun views(v: View): List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    fun testHomeActionsAndThreeRecentLimitInBothThemes() {
        val context=instrumentation.targetContext
        assertTrue(context.packageName.endsWith(".qa"))
        val theme=AppPreferences.theme(context)
        val file=File(context.cacheDir,"home-${UUID.randomUUID()}.png")
        val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        val ids=List(4) { "home-${UUID.randomUUID()}" }
        try {
            ids.forEachIndexed { i,id -> ProjectStore.save(context,SavedProject(id,"Recent $i",System.currentTimeMillis()+i,EditorState(listOf(EditorClip(Uri.fromFile(file).toString(),"Photo",false)),photoEditor=true))) }
            for(themeName in listOf("light","dark","violet","gold")) {
                AppPreferences.setTheme(context,themeName)
                instrumentation.runOnMainSync {
                    var action=""
                    val home=HomeView(context) { action=it }
                    val all=views(home)
                    for((description,expected) in listOf("New Video Project" to "new_video","Video Editor" to "new_video","Edit Photo. Crop, text & filters" to "edit_photo","All Projects" to "projects","View all projects" to "projects")) {
                        all.single { it.contentDescription==description }.performClick(); assertEquals(expected,action)
                    }
                    val recent=all.filter { it.contentDescription?.startsWith("Open project:")==true }
                    assertEquals(3,recent.size)
                    recent.first().performClick(); assertTrue(action.startsWith("open:"))
                    val project=ProjectStore.load(context,ids.first())!!
                    val card=ProjectRow(context,project,compact=true,gridCard=true) {}
                    card.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(context,156),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                    assertTrue("Grid cards must remain compact",card.measuredHeight<=Ui.dp(context,170))
                }
            }
        } finally { ids.forEach { ProjectStore.delete(context,it) }; file.delete(); AppPreferences.setTheme(context,theme) }
    }
}
