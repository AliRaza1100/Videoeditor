package com.lumen.studio

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout

/** A quiet brand introduction; no unsupported performance or export claims. */
class LumenSplashView(context: Context): LinearLayout(context) {
    init {
        orientation=VERTICAL; gravity=Gravity.CENTER; setBackgroundColor(StudioDesign.background(context))
        setPadding(Ui.dp(context,24),Ui.dp(context,24),Ui.dp(context,24),Ui.dp(context,24))
        val center=LinearLayout(context).apply { orientation=VERTICAL; gravity=Gravity.CENTER }
        center.addView(StudioMark(context),LayoutParams(Ui.dp(context,80),Ui.dp(context,80)))
        center.addView(StudioDesign.label(context,"Lumen Studio",30f,bold=true),LayoutParams(-2,-2).apply { topMargin=Ui.dp(context,24) })
        center.addView(StudioDesign.label(context,"Your story starts here.",15f,secondary=true),LayoutParams(-2,-2).apply { topMargin=Ui.dp(context,10) })
        addView(center,LayoutParams(-1,0,1f))
        addView(StudioDesign.label(context,"VIDEO & PHOTO EDITOR",10f,secondary=true).apply { letterSpacing=.15f; gravity=Gravity.CENTER },LayoutParams(-1,Ui.dp(context,40)))
    }
}
