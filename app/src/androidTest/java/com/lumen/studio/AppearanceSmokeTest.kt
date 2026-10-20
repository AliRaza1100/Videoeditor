package com.lumen.studio

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.test.InstrumentationTestCase
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import java.io.File

/** Runs on an isolated emulator, including Urdu RTL and scroll-to-reach controls. */
@Suppress("DEPRECATION")
class AppearanceSmokeTest : InstrumentationTestCase() {
    private fun views(view: View): List<View> = listOf(view) + if(view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun start(name: String, extras: Intent = Intent()): Activity {
        return instrumentation.startActivitySync(extras.setClassName(instrumentation.targetContext.packageName,"com.lumen.studio.$name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun await(check: () -> Boolean) {
        repeat(250) { instrumentation.waitForIdleSync(); if(check()) return; Thread.sleep(100) }
        fail("Timed out waiting for UI")
    }
    private fun root(activity: Activity) = activity.findViewById<View>(android.R.id.content)
    private fun find(activity: Activity, value: String): View? = views(root(activity)).firstOrNull { it.isShown && it.isClickable && ((it is TextView && it.text.toString()==value) || it.contentDescription?.toString()==value) }
    private fun click(activity: Activity,value: String) {
        val target = find(activity,value) ?: error("Missing button: $value")
        instrumentation.runOnMainSync { target.requestRectangleOnScreen(Rect(0,0,target.width,target.height),true) }
        instrumentation.waitForIdleSync()
        val bounds = Rect()
        assertTrue("Button is hidden: $value",target.getGlobalVisibleRect(bounds) && !bounds.isEmpty)
        assertTrue("Button disabled: $value",target.isEnabled && target.isClickable)
        instrumentation.runOnMainSync { assertTrue(target.performClick()) }
        instrumentation.waitForIdleSync()
    }
    private fun verifyReachableButtons(activity: Activity) {
        verifyReachableButtons(activity,root(activity))
    }
    private fun verifyReachableButtons(activity: Activity,container: View) {
        views(container).filterIsInstance<Button>().filter { it.isEnabled && it.isShown }.forEach { button ->
            instrumentation.runOnMainSync { button.requestRectangleOnScreen(Rect(0,0,button.width,button.height),true) }
            instrumentation.waitForIdleSync()
            val bounds = Rect()
            assertTrue("Hidden enabled control: ${button.text}",button.getGlobalVisibleRect(bounds) && !bounds.isEmpty)
            assertTrue("Not clickable: ${button.text}",button.isClickable)
            assertTrue("Small touch target: ${button.text}",button.height >= Ui.dp(activity,44))
        }
    }
    private fun capture(activity: Activity,name: String) {
        val image = instrumentation.uiAutomation.takeScreenshot() ?: return
        val folder = File(activity.filesDir,"theme-checks").apply { mkdirs() }
        val file = File(folder,"${AppPreferences.language(activity)}-${if(AppPreferences.light(activity)) "light" else "dark"}-$name.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }
    fun testThemesLanguagesAndNavigation() {
        val context = instrumentation.targetContext
        val packageInfo = context.packageManager.getPackageInfo(context.packageName,0)
        context.getSharedPreferences("onboarding",0).edit().putString("accepted_terms",TermsConsent.VERSION).putLong("completed_version",packageInfo.longVersionCode).commit()
        Reminders.setEnabled(context,false)
        for(language in listOf("en","ur","hi","zh")) for(light in listOf(false,true)) {
            AppPreferences.setLanguage(context,language); AppPreferences.setLight(context,light)
            val main = start("MainActivity")
            await { find(main,"${Ui.t(main,"Settings")}") != null }
            click(main,"${Ui.t(main,"Settings")}")
            assertTrue(views(root(main)).filterIsInstance<TextView>().any { it.text.toString() == Ui.t(main,"Theme") })
            val background = (root(main) as ViewGroup).getChildAt(0).background as ColorDrawable
            assertTrue("Theme background mismatch",if(light) Color.red(background.color) > 200 else Color.red(background.color) < 65)
            assertEquals(if(language == "ur") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,(root(main) as ViewGroup).getChildAt(0).layoutDirection)
            verifyReachableButtons(main)
            assertEquals(1,views(root(main)).filterIsInstance<android.widget.Switch>().size)
            capture(main,"settings")
            click(main,Ui.t(main,"Theme"))
            assertEquals(2,views(root(main)).filterIsInstance<Button>().count { it.text.toString().startsWith("● ") || it.text.toString().startsWith("○ ") })
            assertEquals(1,views(root(main)).filterIsInstance<Button>().count { it.isSelected })
            capture(main,"theme-choices"); click(main,Ui.t(main,"Back"))
            click(main,Ui.t(main,"Language"))
            assertEquals(4,views(root(main)).filterIsInstance<Button>().count { it.text.toString().startsWith("● ") || it.text.toString().startsWith("○ ") })
            assertEquals(1,views(root(main)).filterIsInstance<Button>().count { it.isSelected })
            capture(main,"language-choices"); click(main,Ui.t(main,"Back"))
            click(main,"${Ui.t(main,"Projects")}")
            verifyReachableButtons(main)
            capture(main,"projects")
            click(main,"${Ui.t(main,"Home")}")
            verifyReachableButtons(main)
            capture(main,"home")
            instrumentation.runOnMainSync { main.finish() }

            val sounds = start("SoundLibraryActivity")
            verifyReachableButtons(sounds)
            capture(sounds,"sounds")
            instrumentation.runOnMainSync { sounds.finish() }

            val fixture = File(context.filesDir,"appearance-test.png")
            val image = Bitmap.createBitmap(120,200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            fixture.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
            val editor = start("EditorActivity",Intent().putStringArrayListExtra("uris",arrayListOf(Uri.fromFile(fixture).toString())).putStringArrayListExtra("names",arrayListOf("Appearance Test")))
            await { (editor as EditorActivity).previewRendered  }
            verifyReachableButtons(editor)
            capture(editor,"editor")
            click(editor,"Clip 1: Appearance Test")
            click(editor,Ui.t(editor,"Edit"))
            click(editor,Ui.t(editor,"Ratio"))
            val panel = (editor as EditorActivity).toolPanel
            assertEquals(View.VISIBLE,panel.visibility)
            verifyReachableButtons(editor,panel)
            capture(editor,"edit-tools")
            click(editor,Ui.t(editor,"Back to tools"))
            click(editor,"⛶")
            verifyReachableButtons(editor)
            capture(editor,"fullscreen")
            instrumentation.runOnMainSync { editor.finish() }
            println("PASS language=$language theme=${if(light) "light" else "dark"}")
        }
    }
    fun testProjectThumbnailAndDirectEditorNavigation() {
        val context=instrumentation.targetContext
        AppPreferences.setLanguage(context,"en"); AppPreferences.setLight(context,false)
        val source=File(context.filesDir,"render-tests/source.mp4"); assertTrue(source.exists())
        val id="navigation-${System.currentTimeMillis()}"; val name="Thumbnail navigation"
        ProjectStore.save(context,SavedProject(id,name,System.currentTimeMillis(),EditorState(listOf(EditorClip(Uri.fromFile(source).toString(),"Source video",true)))))
        val main=start("MainActivity"); await { find(main,"Settings")!=null }
        for(tab in listOf("Home","Projects")) {
            click(main,if(tab=="Home") "Home" else "Projects")
            val row=views(root(main)).filterIsInstance<ProjectRow>().first { it.contentDescription.toString()=="Open project: $name" }
            await { views(row).filterIsInstance<android.widget.ImageView>().first().drawable!=null }
            assertEquals(1,views(row).filterIsInstance<android.widget.ImageButton>().size)
            assertEquals("Delete project",views(row).filterIsInstance<android.widget.ImageButton>().single().contentDescription.toString())
            val monitor=instrumentation.addMonitor(EditorActivity::class.java.name,null,false)
            click(main,"Open project: $name")
            val editor=monitor.waitForActivityWithTimeout(15000) as? EditorActivity ?: error("Project did not reopen the editor")
            assertEquals(id,editor.intent.getStringExtra("project_id")); await { editor.previewRendered }
            instrumentation.removeMonitor(monitor); instrumentation.runOnMainSync { editor.finish() }; instrumentation.waitForIdleSync()
        }
        instrumentation.runOnMainSync { main.finish() }
    }
    fun testSettingsSelectionPersists() {
        val context = instrumentation.targetContext
        AppPreferences.setLanguage(context,"en"); AppPreferences.setLight(context,false)
        val version = context.packageManager.getPackageInfo(context.packageName,0).longVersionCode
        context.getSharedPreferences("onboarding",0).edit().putString("accepted_terms",TermsConsent.VERSION).putLong("completed_version",version).commit()
        val first = start("MainActivity")
        await { find(first,"Settings") != null }
        click(first,"Settings")
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name,null,false)
        click(first,"Theme")
        click(first,"○ Light")
        val light = monitor.waitForActivityWithTimeout(10000) ?: error("Theme switch did not recreate the screen")
        assertTrue(AppPreferences.light(context))
        click(light,"Back")
        click(light,"Language")
        click(light,"○ हिन्दी")
        val hindi = monitor.waitForActivityWithTimeout(10000) ?: error("Language switch did not recreate the screen")
        assertEquals("hi",AppPreferences.language(context))
        assertTrue(views(root(hindi)).filterIsInstance<TextView>().any { it.text.toString() == "भाषा" })
        instrumentation.removeMonitor(monitor)
        instrumentation.runOnMainSync { hindi.finish() }
        val reopened = start("MainActivity")
        await { find(reopened,"सेटिंग्स") != null }
        assertTrue(AppPreferences.light(reopened)); assertEquals("hi",AppPreferences.language(reopened))
        instrumentation.runOnMainSync { reopened.finish() }
    }
    fun testNotificationPreviewAndOptOut() {
        val context = instrumentation.targetContext
        if(android.os.Build.VERSION.SDK_INT >= 33) android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")).use { it.readBytes() }
        Reminders.setEnabled(context,true); Reminders.configure(context,true)
        Reminders.show(context,test = false)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        await { manager.activeNotifications.any { it.id == 12 } }
        assertTrue(context.getSharedPreferences("reminders",0).getBoolean("scheduled",false))
        Reminders.setEnabled(context,false); Reminders.configure(context)
        await { manager.activeNotifications.none { it.id == 12 } }
        assertFalse(context.getSharedPreferences("reminders",0).getBoolean("scheduled",true))
    }
    fun testSavedProjectReopens() {
        val context = instrumentation.targetContext
        val state = EditorState(listOf(EditorClip("file:///test.png","Original name",false,rotation = 90f)),title = "اردو عنوان",audioName = "音声")
        val id = "persistence-test-${System.currentTimeMillis()}"
        ProjectStore.save(context,SavedProject(id,"My Project",200,state))
        ProjectStore.save(context,SavedProject(id,"Old Draft",100,state.copy(title = "old")))
        val loaded = ProjectStore.load(context,id) ?: error("Saved project missing")
        assertEquals("My Project",loaded.name); assertEquals(state,loaded.state)
        assertTrue(ProjectStore.list(context).any { it.id == id })
    }
}

