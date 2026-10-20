package com.lumen.studio

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.*

/** Native settings cards: adaptive text height, semantic theme colors and existing actions. */
class SettingsView(private val activity: Activity, private val navigate: (String) -> Unit) : LinearLayout(activity) {
    private val theme=AppPreferences.theme(activity)
    private val gold=theme=="gold"
    private val ink=if(gold) Color.rgb(25,24,21) else StudioDesign.text(activity)
    private val secondary=if(gold) Color.rgb(101,99,94) else StudioDesign.muted(activity)
    private val accent=if(gold) Color.rgb(131,86,7) else StudioDesign.accent(activity)
    private val soft=if(gold) Color.rgb(255,247,229) else StudioDesign.raised(activity)
    private val line=if(gold) Color.rgb(242,236,224) else StudioDesign.border(activity)
    private fun dp(n: Int)=Ui.dp(activity,n)
    private fun copy(value: String,size: Float,bold: Boolean=false,muted: Boolean=false)=StudioDesign.label(activity,value,size,bold=bold).apply {
        setTextColor(if(muted) secondary else ink)
    }
    private fun rounded(color: Int,radius: Int=18)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private fun icon(resource: Int,color: Int=accent)=ImageView(activity).apply {
        setImageResource(resource);imageTintList=ColorStateList.valueOf(color)
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun tile(resource: Int)=FrameLayout(activity).apply {
        background=rounded(soft,13)
        addView(icon(resource),FrameLayout.LayoutParams(dp(26),dp(26),Gravity.CENTER))
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun ripple(color: Int=StudioDesign.surface(activity))=RippleDrawable(
        ColorStateList.valueOf((accent and 0x00ffffff) or 0x15000000),rounded(color,14),rounded(Color.WHITE,14))
    private fun section(title: String,subtitle: String): LinearLayout {
        return LinearLayout(activity).apply {
            orientation=VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(12))
            background=rounded(StudioDesign.surface(activity),20).apply { setStroke(dp(1),line) }
            val heading=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
            heading.addView(View(activity).apply { background=rounded(if(gold) Color.rgb(218,165,18) else accent,3) },LayoutParams(dp(4),dp(19)))
            heading.addView(copy(title,18f,true).apply { setPaddingRelative(dp(11),0,0,0) },LayoutParams(0,-2,1f))
            addView(heading)
            addView(copy(subtitle,13f,muted=true).apply { setPaddingRelative(dp(15),dp(4),0,dp(13)) })
        }
    }
    private fun divider(parent: LinearLayout) {
        parent.addView(View(activity).apply { setBackgroundColor(line) },LayoutParams(-1,dp(1)).apply { marginStart=dp(64);marginEnd=dp(10) })
    }
    private fun row(parent: LinearLayout,title: String,description: String,resource: Int,value: String?=null,control: View?=null,action: (() -> Unit)?=null) {
        val item=LinearLayout(activity).apply {
            gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(66);setPadding(dp(7),dp(7),dp(9),dp(7))
            background=ripple();if(action!=null) { isFocusable=true;contentDescription=Ui.t(activity,title);setOnClickListener { action() } }
        }
        item.addView(tile(resource),LayoutParams(dp(48),dp(48)))
        item.addView(LinearLayout(activity).apply {
            orientation=VERTICAL;setPaddingRelative(dp(13),0,dp(8),0)
            addView(copy(title,15f,true))
            addView(copy(description,12f,muted=true).apply { setPadding(0,dp(3),0,0) })
            if(action!=null) importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        },LayoutParams(0,-2,1f))
        if(control!=null) item.addView(control,LayoutParams(-2,dp(48)))
        else {
            if(value!=null) item.addView(copy(value,14f,true).apply {
                setTextColor(accent);maxWidth=dp(84);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                if(android.os.Build.VERSION.SDK_INT>=30) item.stateDescription=Ui.t(activity,value)
            },LayoutParams(-2,-2).apply { marginEnd=dp(7) })
            item.addView(icon(R.drawable.ph_arrow_right,secondary),LayoutParams(dp(16),dp(16)))
        }
        parent.addView(item,LayoutParams(-1,-2))
    }
    private fun toggle(title: String,checked: Boolean,onChange: (Boolean)->Unit)=Switch(activity).apply {
        contentDescription=Ui.t(activity,title);showText=false;isChecked=checked;minimumWidth=dp(48);minimumHeight=dp(48)
        thumbTintList=ColorStateList.valueOf(Color.WHITE)
        trackTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(accent,if(gold) Color.rgb(164,157,140) else StudioDesign.muted(activity)))
        setOnCheckedChangeListener { _,enabled -> onChange(enabled) }
    }
    init {
        orientation=VERTICAL;setBackgroundColor(StudioDesign.background(activity))
        val body=LinearLayout(activity).apply { orientation=VERTICAL;setPadding(dp(12),0,dp(12),dp(20)) }
        val header=FrameLayout(activity)
        header.addView(SettingsHeaderArt(activity,accent,soft),FrameLayout.LayoutParams(dp(170),dp(145),Gravity.TOP or Gravity.END))
        header.addView(LinearLayout(activity).apply {
            orientation=VERTICAL;setPadding(dp(8),dp(21),dp(8),dp(20))
            addView(copy("Settings",34f,true))
            addView(copy("Customize your experience",16f,muted=true).apply { setPadding(0,dp(5),0,0) })
        },FrameLayout.LayoutParams(-1,-2))
        body.addView(header,LayoutParams(-1,-2))
        val preferences=section("App Preferences","Personalize your app experience")
        row(preferences,"Theme","Choose your preferred theme",R.drawable.ph_palette,theme.replaceFirstChar { it.titlecase() }) { navigate("theme") }
        divider(preferences)
        row(preferences,"Language","Select app language",R.drawable.ph_globe,mapOf("en" to "English","ur" to "اردو","hi" to "हिन्दी","zh" to "中文")[AppPreferences.language(activity)] ?: "English") { navigate("languages") }
        divider(preferences)
        val notifications=toggle("Notifications",Reminders.enabled(activity)) { enabled ->
            if(enabled && android.os.Build.VERSION.SDK_INT>=33 && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Reminders.setEnabled(activity,false)
                activity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),400)
            } else { Reminders.setEnabled(activity,enabled);Reminders.configure(activity) }
        }
        row(preferences,"Notifications","Editing reminders and updates",R.drawable.ph_bell,control=notifications)
        preferences.addView(LinearLayout(activity).apply {
            gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(11),dp(12),dp(11));background=rounded(if(gold) Color.rgb(250,245,235) else soft,14)
            addView(SettingsClock(activity,accent),LayoutParams(dp(26),dp(26)))
            addView(copy("Optional editing reminders every three hours. Turn them off any time.",12f,muted=true).apply { setPaddingRelative(dp(12),0,0,0) },LayoutParams(0,-2,1f))
        },LayoutParams(-1,-2).apply { topMargin=dp(3) })
        body.addView(preferences,LayoutParams(-1,-2))
        val support=section("Support & Legal","Information and resources")
        row(support,"Privacy policy","How we protect your data",R.drawable.ph_shield_check) { navigate("privacy") }
        if(AdsController.sdkEnabled) {
            divider(support);row(support,"Advertising privacy choices","Manage your ad preferences",R.drawable.ph_megaphone) { AdsController.privacy(activity) }
        }
        divider(support);row(support,"Terms & Conditions","Read our terms of service",R.drawable.ph_file_text) { navigate("terms") }
        divider(support);row(support,"About Us","Learn more about Lumen Studio",R.drawable.ph_question) { navigate("about") }
        divider(support);row(support,"App introduction","Features and how to use",R.drawable.ph_info) { navigate("introduction") }
        divider(support);row(support,"Contact Us","Get in touch for support",R.drawable.ph_envelope) { navigate("contact") }
        val rating=LinearLayout(activity).apply {
            gravity=Gravity.CENTER_VERTICAL;setPadding(dp(13),dp(11),dp(13),dp(11));background=ripple(soft)
            isFocusable=true;contentDescription=Ui.t(activity,"Rate us on the Play Store");setOnClickListener { openStore() }
            addView(tile(R.drawable.ph_crown),LayoutParams(dp(48),dp(48)))
            addView(LinearLayout(activity).apply {
                orientation=VERTICAL;setPaddingRelative(dp(12),0,dp(8),0)
                addView(copy("Love the app?",16f,true).apply { setTextColor(accent) })
                addView(copy("Rate us on the Play Store",13f,muted=true).apply { setPadding(0,dp(4),0,0) })
            },LayoutParams(0,-2,1f))
            addView(icon(R.drawable.ph_arrow_right),LayoutParams(dp(20),dp(20)))
        }
        support.addView(rating,LayoutParams(-1,-2).apply { topMargin=dp(12) })
        body.addView(support,LayoutParams(-1,-2).apply { topMargin=dp(12) })
        if(AdminConnection.configured) {
            val online=section("Connected services","Your choices and support conversations")
            row(online,"Support inbox","Read replies to your support messages",R.drawable.ph_envelope) { activity.startActivity(Intent(activity,SupportInboxActivity::class.java)) }
            divider(online)
            row(online,"Share usage analytics","Help improve Lumen Studio",R.drawable.ph_shield_check,control=toggle("Share usage analytics",AdminConnection.analyticsEnabled(activity)) { enabled ->
                AdminConnection.setAnalytics(activity,enabled) { result ->
                    if(!activity.isDestroyed) result.exceptionOrNull()?.let { Toast.makeText(activity,"Choice saved. Server update failed; retry when connected: ${it.message}",Toast.LENGTH_LONG).show() }
                }
            })
            online.addView(copy("Optional usage analytics sends an installation ID, device model/brand, Android API, density, configured locale/region and editing counters to the administrator. It never sends media, filenames or authored text. Turning it off requests deletion of usage history; support conversations remain until you delete online data.",12f,muted=true).apply { setPadding(dp(8),dp(8),dp(8),dp(12)) })
            divider(online)
            row(online,"Delete online data","Remove analytics and support conversations",R.drawable.ph_trash) {
                AppDialog(activity).setTitle("Delete online data?").setMessage("Remove this installation's analytics and support conversations from the admin server. Local projects, photos and videos remain on this phone.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_ -> AdminConnection.deleteOnlineData(activity) { result ->
                        Toast.makeText(activity,if(result.isSuccess) "Online data deleted." else result.exceptionOrNull()?.message ?: "Unable to delete online data.",Toast.LENGTH_LONG).show()
                        if(result.isSuccess && !activity.isDestroyed) navigate("settings")
                    } }.show()
            }
            body.addView(online,LayoutParams(-1,-2).apply { topMargin=dp(12) })
        }
        body.addView(copy("Lumen Studio · ${BuildConfig.VERSION_NAME}",12f,muted=true).apply { gravity=Gravity.CENTER;setPadding(0,dp(20),0,0) })
        val centered=FrameLayout(activity).apply {
            addView(body,FrameLayout.LayoutParams(if(resources.configuration.screenWidthDp>760) dp(760) else -1,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        }
        addView(ScrollView(activity).apply { isFillViewport=true;isVerticalScrollBarEnabled=false;addView(centered) },LayoutParams(-1,0,1f))
        addView(BottomNavigation(activity,"settings",navigate).apply { background=rounded(StudioDesign.surface(activity),22) })
    }
    private fun openStore() {
        try { activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id=${activity.packageName}"))) }
        catch(_: android.content.ActivityNotFoundException) {
            try { activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}"))) }
            catch(_: android.content.ActivityNotFoundException) { Toast.makeText(activity,Ui.t(activity,"No app available to open this link."),Toast.LENGTH_SHORT).show() }
        }
    }
}

/** Original lightweight vector ornament, kept away from text and excluded from accessibility. */
private class SettingsHeaderArt(context: android.content.Context,private val accent: Int,private val soft: Int): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        canvas.save();canvas.scale(width/170f,height/145f)
        paint.color=soft
        canvas.drawPath(Path().apply { moveTo(0f,0f);cubicTo(48f,4f,28f,55f,96f,39f);cubicTo(139f,24f,147f,54f,170f,64f);lineTo(170f,0f);close() },paint)
        canvas.save();canvas.translate(111f,99f);canvas.rotate(11f)
        paint.shader=LinearGradient(-38f,-39f,38f,36f,intArrayOf(soft,(accent and 0x00ffffff) or 0x55000000),null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(-37f,-37f,37f,42f,12f,12f,paint);paint.shader=null
        paint.color=StudioDesign.surface(context)
        for(i in 0..3) canvas.drawRoundRect(-31f,-28f+i*17,-24f,-18f+i*17,2f,2f,paint)
        canvas.drawPath(Path().apply { moveTo(-10f,-15f);lineTo(13f,0f);lineTo(-10f,15f);close() },paint)
        canvas.restore()
        paint.color=soft;canvas.drawCircle(145f,105f,22f,paint)
        paint.color=(accent and 0x00ffffff) or 0x77000000;paint.style=Paint.Style.STROKE;paint.strokeWidth=5f
        canvas.drawCircle(145f,105f,9f,paint)
        for(i in 0..7) { canvas.save();canvas.rotate(i*45f,145f,105f);canvas.drawLine(145f,91f,145f,96f,paint);canvas.restore() }
        paint.style=Paint.Style.FILL;canvas.restore()
    }
}
private class SettingsClock(context: android.content.Context,private val color: Int): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeWidth=2f;strokeCap=Paint.Cap.ROUND;color=this@SettingsClock.color }
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        canvas.save();canvas.scale(width/26f,height/26f);canvas.drawCircle(13f,13f,10f,paint);canvas.drawLine(13f,6f,13f,13f,paint);canvas.drawLine(13f,13f,18f,16f,paint);canvas.restore()
    }
}
