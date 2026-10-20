package com.lumen.studio

import android.app.Activity
import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.View
import android.widget.*

/** Selection is a draft until Apply; navigating back leaves the saved preference untouched. */
class AppearanceChoicesView(private val activity: Activity,private val theme: Boolean,private val navigate: (String)->Unit): LinearLayout(activity) {
    private val ui=ReferenceSurfaces(activity)
    private var pending=if(theme) AppPreferences.theme(activity) else AppPreferences.language(activity)
    val draftSelection get()=pending
    fun restoreDraft(value: String?) {
        val allowed=if(theme) setOf("gold","light","dark","violet") else setOf("en","ur","hi","zh")
        if(value in allowed) { pending=value!!;render() }
    }
    private val options=LinearLayout(activity).apply { orientation=VERTICAL }
    private fun dp(n: Int)=ui.dp(n)
    init {
        id=if(theme) R.id.theme_choices else R.id.language_choices
        orientation=VERTICAL;setBackgroundColor(StudioDesign.background(activity))
        val header=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(12),dp(20),dp(12)) }
        header.addView(StudioDesign.button(activity,"←") { navigate("settings") }.apply { contentDescription=Ui.t(activity,"Back");background=null;textSize=24f },LayoutParams(dp(48),dp(48)))
        header.addView(StudioDesign.label(activity,if(theme) "Theme" else "Language",26f,bold=true).apply { setPaddingRelative(dp(10),0,0,0) },LayoutParams(0,-2,1f))
        addView(header)
        val content=LinearLayout(activity).apply { orientation=VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(18)) }
        val intro=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),dp(16),dp(14),dp(16));background=ui.shape(ui.soft) }
        intro.addView(FrameLayout(activity).apply {
            background=ui.shape(ui.selected,14)
            addView(ui.icon(if(theme) R.drawable.ph_palette else R.drawable.ph_globe),FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER))
        },LayoutParams(dp(50),dp(50)))
        intro.addView(LinearLayout(activity).apply {
            orientation=VERTICAL;setPaddingRelative(dp(14),0,0,0)
            addView(StudioDesign.label(activity,if(theme) "Choose App Theme" else "Choose App Language",15f,bold=true))
            addView(StudioDesign.label(activity,if(theme) "Customize the look and feel of the app." else "Select the language you want to use in the app.",13f,secondary=true).apply { setPadding(0,dp(5),0,0) })
        },LayoutParams(0,-2,1f))
        content.addView(intro,LayoutParams(-1,-2).apply { bottomMargin=dp(16) })
        content.addView(options,LayoutParams(-1,-2))
        val center=FrameLayout(activity).apply { addView(content,FrameLayout.LayoutParams(if(resources.configuration.screenWidthDp>640) dp(640) else -1,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL)) }
        addView(ScrollView(activity).apply { isFillViewport=true;isVerticalScrollBarEnabled=false;addView(center) },LayoutParams(-1,0,1f))
        val footer=FrameLayout(activity).apply { setPadding(dp(20),dp(10),dp(20),dp(20)) }
        footer.addView(StudioDesign.button(activity,if(theme) "Apply Theme" else "Apply Language",primary=true) {
            if(theme) AppPreferences.setTheme(activity,pending) else { AppPreferences.setLanguage(activity,pending);Reminders.configure(activity) }
            navigate("settings")
            activity.recreate()
        },FrameLayout.LayoutParams(if(resources.configuration.screenWidthDp>640) dp(600) else -1,dp(56),Gravity.CENTER))
        addView(footer);render()
    }
    private fun render() {
        options.removeAllViews()
        val values=if(theme) listOf(Triple("Gold","gold","Elegant and modern"),Triple("Light","light","Clean and minimal"),Triple("Dark","dark","Comfortable for night use"),Triple("Violet","violet","Stylish and unique"))
            else listOf(Triple("English","en",""),Triple("اردو","ur","Urdu"),Triple("हिन्दी","hi","Hindi"),Triple("中文","zh","Chinese"))
        values.forEach { (name,code,subtitle) ->
            val active=code==pending
            val row=LinearLayout(activity).apply {
                orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(if(theme) 86 else 66);setPadding(dp(12),dp(10),dp(16),dp(10));background=ui.card(active)
                isFocusable=true;isSelected=active;contentDescription=Ui.t(activity,name);tag="appearance-option"
                if(android.os.Build.VERSION.SDK_INT>=30) stateDescription=Ui.t(activity,if(active) "Selected" else "Not selected")
                setOnClickListener { pending=code;render() }
            }
            if(theme) row.addView(ThemeSwatch(activity,code),LayoutParams(dp(82),dp(64)).apply { marginEnd=dp(14) })
            else row.addView(ChoiceIndicator(activity,active),LayoutParams(dp(24),dp(24)).apply { marginEnd=dp(20) })
            row.addView(LinearLayout(activity).apply {
                orientation=VERTICAL;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(StudioDesign.label(activity,name,16f,bold=theme || code=="en"))
                if(subtitle.isNotEmpty()) addView(StudioDesign.label(activity,subtitle,12f,secondary=true).apply { setPadding(0,dp(5),0,0) })
            },LayoutParams(0,-2,1f))
            if(theme) row.addView(ChoiceIndicator(activity,active),LayoutParams(dp(24),dp(24)).apply { marginStart=dp(10) })
            else if(code=="en") row.addView(StudioDesign.label(activity,"Default",12f,bold=true).apply { setTextColor(ui.accent);setPadding(dp(8),dp(6),dp(8),dp(6));background=ui.shape(ui.soft,10) })
            options.addView(row,LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        }
    }
    override fun onSaveInstanceState(): Parcelable=Bundle().apply { putParcelable("super",super.onSaveInstanceState());putString("pending",pending) }
    override fun onRestoreInstanceState(state: Parcelable?) {
        if(state is Bundle) { @Suppress("DEPRECATION") val previous=state.getParcelable<Parcelable>("super");super.onRestoreInstanceState(previous);pending=state.getString("pending",pending);render() }
        else super.onRestoreInstanceState(state)
    }
}
