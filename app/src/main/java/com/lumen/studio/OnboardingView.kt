package com.lumen.studio

import android.content.Context
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView

/** Four photographic introductions with accessible native controls. */
class OnboardingView(context: Context, page: Int, onNext: () -> Unit, onSkip: () -> Unit): LinearLayout(context) {
    companion object { const val PAGE_COUNT=4 }
    init {
        orientation=VERTICAL; setBackgroundColor(StudioDesign.background(context))
        val index=page.coerceIn(0,PAGE_COUNT-1)
        val titles=listOf("Advanced Filter\nEffects","Get Creative\nEffects","Explore Artistic\nBackgrounds","Add Multiple\nSoundtracks")
        val descriptions=listOf("Find your look with expressive color and filters.","Give your photos and videos a fresh creative touch.","Create photo collages with unique layouts and canvas colors.","Layer music and extracted audio to bring your story to life.")
        val body=LinearLayout(context).apply { orientation=VERTICAL; gravity=Gravity.CENTER_HORIZONTAL }
        val landscape=resources.configuration.screenWidthDp>resources.configuration.screenHeightDp
        val artHeight=if(landscape) 240 else (resources.configuration.screenHeightDp*.49f).toInt().coerceIn(240,440)
        body.addView(OnboardingArtwork(context,index),LayoutParams(-1,Ui.dp(context,artHeight)))
        val copy=LinearLayout(context).apply { orientation=VERTICAL; gravity=Gravity.CENTER_HORIZONTAL; setPadding(Ui.dp(context,24),0,Ui.dp(context,24),Ui.dp(context,16)) }
        copy.addView(StudioDesign.label(context,titles[index],29f,bold=true).apply { gravity=Gravity.CENTER; if(android.os.Build.VERSION.SDK_INT>=28) setAccessibilityHeading(true) },LayoutParams(-1,-2))
        copy.addView(StudioDesign.label(context,descriptions[index],14f,secondary=true).apply { gravity=Gravity.CENTER },LayoutParams(-1,-2).apply { topMargin=Ui.dp(context,12) })
        val dots=LinearLayout(context).apply { gravity=Gravity.CENTER; contentDescription="Introduction ${index+1} of $PAGE_COUNT"; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES }
        repeat(PAGE_COUNT) { dot -> dots.addView(android.view.View(context).apply {
            background=StudioDesign.shape(context,if(dot==index) StudioDesign.text(context) else StudioDesign.border(context),8)
            importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        },LayoutParams(Ui.dp(context,if(dot==index) 16 else 5),Ui.dp(context,5)).apply { marginStart=Ui.dp(context,5); marginEnd=Ui.dp(context,5) }) }
        copy.addView(dots,LayoutParams(-1,Ui.dp(context,36)))
        val next=StudioDesign.button(context,if(index==PAGE_COUNT-1) "Get started" else "Next",primary=true,action=onNext).apply {
            val dark=AppPreferences.theme(context)=="dark"
            tag=if(dark) "media" else "primary-action"
            setTextColor(if(dark) android.graphics.Color.rgb(15,17,24) else android.graphics.Color.WHITE)
            tag="media"
            background=StudioDesign.shape(context,if(dark) android.graphics.Color.WHITE else if(AppPreferences.theme(context) in listOf("violet","gold","light")) StudioDesign.primary(context) else android.graphics.Color.rgb(29,32,46),28)
        }
        val skip=StudioDesign.button(context,"Skip",action=onSkip).apply { background=null }
        fun enableActions() { val accepted=TermsConsent.accepted(context); next.isEnabled=accepted; skip.isEnabled=accepted; next.alpha=if(accepted) 1f else .45f; skip.alpha=next.alpha }
        if(index==0) {
            copy.addView(CheckBox(context).apply {
                text="I agree to the Terms & Conditions"; textSize=14f; setTextColor(StudioDesign.text(context))
                buttonTintList=android.content.res.ColorStateList.valueOf(StudioDesign.accent(context))
                minHeight=Ui.dp(context,48); isChecked=TermsConsent.accepted(context)
                setOnCheckedChangeListener { _,checked -> TermsConsent.setAccepted(context,checked); enableActions() }
            },LayoutParams(-1,-2))
            copy.addView(StudioDesign.button(context,"Read Terms & Conditions") { context.startActivity(android.content.Intent(context,TermsActivity::class.java)) },LayoutParams(-1,Ui.dp(context,48)).apply { bottomMargin=Ui.dp(context,12) })
        }
        copy.addView(next,LayoutParams(-1,Ui.dp(context,56)))
        if(index<PAGE_COUNT-1) copy.addView(skip,LayoutParams(-1,Ui.dp(context,48)).apply { topMargin=Ui.dp(context,6) })
        enableActions(); body.addView(copy,LayoutParams(-1,-2))
        addView(ScrollView(context).apply { isFillViewport=true; isVerticalScrollBarEnabled=false; addView(body) },LayoutParams(-1,-1))
    }
}

