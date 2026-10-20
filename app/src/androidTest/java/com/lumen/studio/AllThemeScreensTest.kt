package com.lumen.studio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.test.InstrumentationTestCase
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.pow

/** Attached view checks complement palette math; they require a device, not android.jar stubs. */
@Suppress("DEPRECATION")
class AllThemeScreensTest: InstrumentationTestCase() {
    private val context get()=instrumentation.targetContext
    private fun luminance(color: Int): Double {
        val channels=listOf(Color.red(color),Color.green(color),Color.blue(color)).map { it/255.0 }.map { if(it<=.04045) it/12.92 else ((it+.055)/1.055).pow(2.4) }
        return channels[0]*.2126+channels[1]*.7152+channels[2]*.0722
    }
    private fun contrast(a: Int,b: Int): Double { val x=luminance(a); val y=luminance(b); return (maxOf(x,y)+.05)/(minOf(x,y)+.05) }
    private fun color(drawable: Drawable?): Int? = when(drawable) {
        is android.graphics.drawable.InsetDrawable -> color(drawable.drawable)
        is ColorDrawable -> drawable.color
        is GradientDrawable -> drawable.color?.defaultColor
        is RippleDrawable -> if(drawable.numberOfLayers>0) color(drawable.getDrawable(0)) else null
        else -> null
    }
    private fun check(view: View,background: Int,theme: String,screen: String) {
        if(view.tag=="media") return // User media, thumbnail content and the black video matte keep their own colors.
        val own=color(view.background)?.takeIf { Color.alpha(it)==255 } ?: background
        if(view is TextView && view.text.isNotEmpty() && view.isEnabled && view.alpha==1f) {
            assertTrue("Unreadable $theme / $screen / ${view.text}: fg=${view.currentTextColor} bg=$own",contrast(view.currentTextColor,own)>=4.5)
        }
        if(view.isClickable && view.isEnabled && view.visibility==View.VISIBLE) assertTrue("Disabled touch focus: $theme / $screen",view.isFocusable || view.hasOnClickListeners())
        if(view is ViewGroup) for(i in 0 until view.childCount) check(view.getChildAt(i),own,theme,screen)
    }
    fun testEveryNativeScreenFamilyInEveryTheme() {
        assertTrue(context.packageName.endsWith(".qa"))
        context.getSharedPreferences("onboarding",0).edit().putString("accepted_terms",TermsConsent.VERSION).putLong("completed_version",1).commit()
        try { for(theme in listOf("light","dark","violet","gold")) {
            AppPreferences.setTheme(context,theme)
            val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            try {
                instrumentation.waitForIdleSync(); Thread.sleep(500)
                instrumentation.runOnMainSync {
                    val screens=linkedMapOf<String,()->View>(
                        "Splash" to { LumenSplashView(activity) },
                        "Home" to { HomeView(activity) },
                        "Projects" to { ProjectsView(activity) {} },
                        "Saved" to { ProjectsView(activity,{},true) },
                        "Settings" to { SettingsView(activity) {} },
                        "Notifications" to { NotificationsView(activity) {} },
                        "Themes" to { AppearanceChoicesView(activity,true) {} },
                        "Languages" to { AppearanceChoicesView(activity,false) {} },
                        "Privacy" to { PrivacyPolicyView(activity) {} },
                        "Storage" to { StorageDataView(activity) {} }
                    )
                    for((name,make) in screens) { val view=make(); activity.setContentView(view); check(view,StudioDesign.background(activity),theme,name) }
                    for(page in 0 until OnboardingView.PAGE_COUNT) { val view=OnboardingView(activity,page,{},{}); activity.setContentView(view); check(view,StudioDesign.background(activity),theme,"Onboarding $page") }
                    var action=""
                    val home=HomeView(activity) { action=it }
                    activity.setContentView(home)
                    fun visit(v: View) { if(v.contentDescription=="New Video Project") v.performClick(); if(v is ViewGroup) for(i in 0 until v.childCount) visit(v.getChildAt(i)) }
                    visit(home); assertEquals("Theme change broke Home action","new_video",action)
                    val dialog=AppDialog(activity).setTitle("Confirm").setMessage("Theme readability check").setPositiveButton("OK",null).setNegativeButton("Cancel",null).show()
                    try { check(dialog.window!!.decorView,StudioDesign.surface(activity),theme,"Confirmation dialog") } finally { dialog.dismiss() }
                }
                for(name in listOf("SoundLibraryActivity","PlaybackCheckActivity","VideoPlayerActivity")) {
                    val intent=Intent().setClassName(context.packageName,"com.lumen.studio.$name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if(name=="VideoPlayerActivity") intent.putExtra("video_uri","file://${context.cacheDir}/theme-check-missing.mp4")
                    val screen=instrumentation.startActivitySync(intent)
                    try { instrumentation.waitForIdleSync(); instrumentation.runOnMainSync { check(screen.window.decorView,StudioDesign.background(screen),theme,name) } } finally { instrumentation.runOnMainSync { screen.finish() } }
                }
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        } } finally { AppPreferences.setTheme(context,"dark") }
    }
}


