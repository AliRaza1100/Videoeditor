package com.lumen.studio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private fun Context.dp(n: Int) = Ui.dp(this,n)
private fun shape(context: Context,color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = context.dp(12).toFloat() }
private fun text(context: Context,value: String,size: Float = 14f) = AppText(context).apply {
    text = value; textSize = size; setTextColor(Color.rgb(232,237,247)); setPadding(context.dp(12),context.dp(8),context.dp(12),context.dp(8))
}
private fun action(context: Context,value: String,clicked: () -> Unit) = AppButton(context).apply {
    text = value; textSize = 14f; isAllCaps = false; setTextColor(Color.rgb(0,225,119))
    background = shape(context,Color.rgb(28,32,37)); minHeight = context.dp(48)
    setOnClickListener { clicked() }
}

class SettingsView(private val activity: Activity,navigate: (String) -> Unit) : LinearLayout(activity) {
    init {
        val context=activity; orientation=VERTICAL; setBackgroundColor(Color.rgb(17,19,21))
        addView(StudioDesign.label(context,"Settings",28f,bold=true).apply { setPadding(context.dp(20),context.dp(24),context.dp(20),context.dp(20)) })
        val body=LinearLayout(context).apply { orientation=VERTICAL; setPadding(context.dp(20),0,context.dp(20),context.dp(20)) }
        val profile=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL; background=StudioDesign.shape(context,StudioDesign.surface(context),16,true); setPadding(context.dp(16),context.dp(16),context.dp(16),context.dp(16)) }
        profile.addView(studioIcon(context,R.drawable.ph_user_circle,"Local creator"),LayoutParams(context.dp(48),context.dp(48)))
        profile.addView(LinearLayout(context).apply { orientation=VERTICAL; setPadding(context.dp(12),0,0,0); addView(StudioDesign.label(context,"Local creator",18f,bold=true)); addView(StudioDesign.label(context,"This device · projects stay on your phone",12f,secondary=true)) },LayoutParams(0,-2,1f))
        body.addView(profile,LayoutParams(-1,-2).apply { bottomMargin=context.dp(20) })
        body.addView(StudioDesign.label(context,"Preferences",16f,bold=true))
        body.addView(action(context,"Theme · "+AppPreferences.theme(context).replaceFirstChar { it.titlecase() }) { navigate("theme") },LayoutParams(-1,context.dp(56)).apply { topMargin=context.dp(8) })
        body.addView(action(context,"Language · "+(mapOf("en" to "English","ur" to "اردو","hi" to "हिन्दी","zh" to "中文")[AppPreferences.language(context)] ?: "English")) { navigate("languages") },LayoutParams(-1,context.dp(56)).apply { topMargin=context.dp(8) })
        body.addView(Switch(context).apply {
            text=Ui.t(context,"Notifications"); contentDescription=Ui.t(context,"Notifications"); setTextColor(Ui.foreground(context,Color.WHITE)); minHeight=context.dp(56)
            isChecked=Reminders.enabled(context)
            setOnCheckedChangeListener { _,enabled ->
                if(enabled && android.os.Build.VERSION.SDK_INT>=33 && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Reminders.setEnabled(context,false); isChecked=false
                    activity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),400)
                } else { Reminders.setEnabled(context,enabled); Reminders.configure(context) }
            }
        },LayoutParams(-1,context.dp(56)).apply { topMargin=context.dp(8) })
        body.addView(StudioDesign.label(context,"Optional editing reminders every three hours. Turn them off any time.",12f,secondary=true),LayoutParams(-1,-2).apply { topMargin=context.dp(8); bottomMargin=context.dp(24) })
        body.addView(StudioDesign.label(context,"Support",16f,bold=true),LayoutParams(-1,-2).apply { bottomMargin=context.dp(12) })
        body.addView(action(context,"Privacy policy") { navigate("privacy") },LayoutParams(-1,context.dp(56)).apply { bottomMargin=context.dp(8) })
        if(AdsController.sdkEnabled) {
            body.addView(action(context,"Advertising privacy choices") { AdsController.privacy(activity) },LayoutParams(-1,context.dp(56)))
            body.addView(StudioDesign.label(context,AdsController.status(),12f,secondary=true))
        }
        body.addView(action(context,"Terms & Conditions") { navigate("terms") },LayoutParams(-1,context.dp(56)).apply { bottomMargin=context.dp(8) })
        body.addView(action(context,"About Us") { navigate("about") },LayoutParams(-1,context.dp(56)).apply { bottomMargin=context.dp(8) })
        body.addView(action(context,"App introduction") { navigate("introduction") },LayoutParams(-1,context.dp(56)).apply { bottomMargin=context.dp(8) })
        body.addView(action(context,"Contact Us") { navigate("contact") },LayoutParams(-1,context.dp(56)).apply { bottomMargin=context.dp(20) })
        if(AdminConnection.configured) {
            body.addView(action(context,"Support inbox") { activity.startActivity(Intent(activity,SupportInboxActivity::class.java)) },LayoutParams(-1,context.dp(56)))
            body.addView(StudioDesign.label(context,"Optional usage analytics sends an installation ID, device model/brand, Android API, density, configured locale/region and editing counters to the administrator. It never sends media, filenames or authored text. Turning it off requests deletion of usage history; support conversations remain until you delete online data.",13f,secondary=true),LayoutParams(-1,-2).apply { topMargin=context.dp(18) })
            body.addView(Switch(context).apply {
                text="Share usage analytics";setTextColor(StudioDesign.text(context));minHeight=context.dp(56);isChecked=AdminConnection.analyticsEnabled(context)
                setOnCheckedChangeListener { _,enabled -> AdminConnection.setAnalytics(context,enabled) { result -> if(!activity.isDestroyed) result.exceptionOrNull()?.let { Toast.makeText(context,"Choice saved. Server update failed; retry when connected: ${it.message}",Toast.LENGTH_LONG).show() } } }
            })
            body.addView(action(context,"Delete online data") {
                AppDialog(activity).setTitle("Delete online data?").setMessage("Remove this installation's analytics and support conversations from the admin server. Local projects, photos and videos remain on this phone.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_ -> AdminConnection.deleteOnlineData(context) { result -> Toast.makeText(context,if(result.isSuccess) "Online data deleted." else result.exceptionOrNull()?.message ?: "Unable to delete online data.",Toast.LENGTH_LONG).show();if(result.isSuccess && !activity.isDestroyed) navigate("settings") } }.show()
            },LayoutParams(-1,context.dp(56)).apply { bottomMargin=context.dp(20) })
        }
        for(i in 0 until body.childCount) {
            val row=body.getChildAt(i)
            if(row is TextView && (row is android.widget.Button || row is Switch)) {
                if(row.text.toString().startsWith("Theme")) row.contentDescription="Theme"
                if(row.text.toString().startsWith("Language")) row.contentDescription="Language"
                val label=row.text.toString()
                val resource=when { label.startsWith("Theme") -> R.drawable.ph_palette; label.startsWith("Language") -> R.drawable.ph_globe; label.contains("Contact") || label.contains("inbox") -> R.drawable.ph_envelope; label.contains("Privacy") || label.contains("Terms") || label.contains("data") -> R.drawable.ph_shield_check; label.contains("About") -> R.drawable.ph_question; else -> R.drawable.ph_gear_six }
                val icon=context.getDrawable(resource)?.mutate()?.apply { setTint(StudioDesign.accent(context)); setBounds(0,0,context.dp(24),context.dp(24)) }
                row.setCompoundDrawablesRelative(icon,null,null,null); row.compoundDrawablePadding=context.dp(12)
            }
        }
        body.addView(StudioDesign.label(context,"Lumen Studio · ${BuildConfig.VERSION_NAME}",12f,secondary=true),LayoutParams(-1,-2))
        addView(ScrollView(context).apply { addView(body) },LayoutParams(-1,0,1f))
        addView(BottomNavigation(context,"settings",navigate))
    }
}

/** Settings choices have their own screen and a persistent, visibly selected option. */
class AppearanceChoicesView(activity: Activity,theme: Boolean,navigate: (String)->Unit) : LinearLayout(activity) {
    init {
        val context=activity; orientation=VERTICAL; setBackgroundColor(Color.rgb(17,19,21))
        addView(LinearLayout(context).apply {
            gravity=Gravity.CENTER_VERTICAL
            addView(action(context,"←") { navigate("settings") }.apply { contentDescription=Ui.t(context,"Back") },LayoutParams(context.dp(48),context.dp(48)))
            addView(text(context,if(theme) "Theme" else "Language",22f))
        })
        val body=LinearLayout(context).apply { orientation=VERTICAL; setPadding(context.dp(12),context.dp(8),context.dp(12),context.dp(12)) }
        val options=if(theme) listOf("Gold" to "gold","Light" to "light","Dark" to "dark","Violet" to "violet") else listOf("English" to "en","اردو" to "ur","हिन्दी" to "hi","中文" to "zh")
        options.forEach { (name,code) ->
            val selected=if(theme) AppPreferences.theme(context)==code else AppPreferences.language(context)==code
            body.addView(action(context,(if(selected) "● " else "○ ")+Ui.t(context,name)) {
                if(theme) AppPreferences.setTheme(context,code) else { AppPreferences.setLanguage(context,code); Reminders.configure(context) }
                activity.recreate()
            }.apply {
                isSelected=selected
                if(selected) background=GradientDrawable().apply { setColor(StudioDesign.raised(context)); setStroke(context.dp(2),StudioDesign.accent(context)); cornerRadius=context.dp(14).toFloat() }
            },LayoutParams(-1,context.dp(56)).apply { topMargin=context.dp(8) })
        }
        addView(ScrollView(context).apply { addView(body) },LayoutParams(-1,0,1f))
    }
}
