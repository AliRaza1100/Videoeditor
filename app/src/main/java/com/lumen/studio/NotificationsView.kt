package com.lumen.studio

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Switch

class NotificationsView(activity: Activity,navigate: (String)->Unit): LinearLayout(activity) {
    init {
        orientation=VERTICAL; setBackgroundColor(StudioDesign.background(activity)); val pad=Ui.dp(activity,16); setPadding(pad,pad,pad,pad)
        val header=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(StudioDesign.button(activity,"‹") { navigate("home") }.apply { contentDescription="Back" },LayoutParams(Ui.dp(activity,48),Ui.dp(activity,48)))
        header.addView(StudioDesign.label(activity,"Notifications",24f,bold=true)); addView(header)
        addView(StudioDesign.label(activity,"Editing reminders",19f,bold=true),LayoutParams(-1,-2).apply { topMargin=Ui.dp(activity,24) })
        addView(StudioDesign.label(activity,"Optional reminders to return to your projects. You can turn them off at any time.",14f,secondary=true),LayoutParams(-1,-2).apply { topMargin=Ui.dp(activity,12) })
        addView(Switch(activity).apply {
            text="Editing reminders"; setTextColor(StudioDesign.text(activity)); minHeight=Ui.dp(activity,56); isChecked=Reminders.enabled(activity)
            setOnCheckedChangeListener { _,enabled ->
                if(enabled && Build.VERSION.SDK_INT>=33 && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                    Reminders.setEnabled(activity,false); isChecked=false; activity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),400)
                } else { Reminders.setEnabled(activity,enabled); Reminders.configure(activity) }
            }
        },LayoutParams(-1,Ui.dp(activity,64)))
    }
}
