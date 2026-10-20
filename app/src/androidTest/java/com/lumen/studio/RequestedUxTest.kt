package com.lumen.studio

import android.test.InstrumentationTestCase
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView

@Suppress("DEPRECATION")
class RequestedUxTest: InstrumentationTestCase() {
    private fun views(v: View): List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    fun testHomeHeaderAndCleanProjectControls() {
        val context=instrumentation.targetContext
        assertTrue(context.packageName.endsWith(".qa"))
        instrumentation.runOnMainSync {
            var action=""
            val home=HomeView(context) { action=it }
            val children=views(home)
            assertTrue(children.filterIsInstance<TextView>().any { it.text.toString()=="Lumen Studio" })
            val bell=children.single { it.isClickable && it.contentDescription=="Notifications" }
            bell.performClick(); assertEquals("notifications",action)
            val projects=views(ProjectsView(context) {})
            assertTrue("Search must be removed",projects.none { it is EditText })
            assertTrue("Favorites must be removed",projects.none { it.contentDescription?.toString()?.contains("favorite",true)==true || (it is TextView && it.text.toString().contains("favorite",true)) })
        }
    }
    fun testLightUsesWhiteBackgroundAndBlackMainText() {
        val colors=ThemeColors.palette("light")
        assertEquals(android.graphics.Color.WHITE,colors[0]); assertEquals(android.graphics.Color.BLACK,colors[3])
    }
}
