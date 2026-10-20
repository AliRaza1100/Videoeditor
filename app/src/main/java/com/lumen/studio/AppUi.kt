package com.lumen.studio

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.json.JSONObject
import java.util.Locale

object AppPreferences {
    private fun prefs(context: Context) = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun theme(context: Context): String {
        val settings=prefs(context); val stored=settings.getString("theme",null)
        if(stored in listOf("light","dark","violet","gold")) return stored!!
        val resolved=if(stored==null && settings.contains("light")) { if(settings.getBoolean("light",false)) "light" else "dark" } else "gold"
        settings.edit().putString("theme",resolved).putBoolean("light",resolved!="dark").apply()
        return resolved
    }
    fun light(context: Context)=theme(context)!="dark"
    fun setTheme(context: Context,value: String) { require(value in listOf("light","dark","violet","gold")); prefs(context).edit().putString("theme",value).putBoolean("light",value!="dark").apply() }
    fun language(context: Context) = prefs(context).getString("language", "en") ?: "en"
    fun setLight(context: Context, value: Boolean) { setTheme(context,if(value) "light" else "dark") }
    fun setLanguage(context: Context, value: String) { require(value in listOf("en","ur","hi","zh")); prefs(context).edit().putString("language",value).apply() }
    fun localized(context: Context): Context {
        val config = android.content.res.Configuration(context.resources.configuration)
        val locale = Locale.forLanguageTag(language(context))
        config.setLocale(locale); config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}

object Ui {
    private val catalogs = mutableMapOf<String, Map<String,String>>()
    fun foreground(context: Context, color: Int): Int {
        val accent = Color.green(color) > Color.red(color) * 1.3 && Color.green(color) > Color.blue(color) * 1.15
        if(accent) return StudioDesign.accent(context)
        if(!AppPreferences.light(context)) return color
        val neutral=maxOf(Color.red(color),Color.green(color),Color.blue(color))-minOf(Color.red(color),Color.green(color),Color.blue(color))<45
        val brightness=(Color.red(color)+Color.green(color)+Color.blue(color))/3
        return if(neutral && brightness>210) StudioDesign.text(context) else if(neutral && brightness>100) StudioDesign.muted(context) else color
    }
    fun t(context: Context, text: CharSequence?): String {
        val value = text?.toString() ?: ""
        val language = AppPreferences.language(context)
        if (language == "en" || value.isEmpty()) return value
        val catalog = synchronized(catalogs) { catalogs.getOrPut(language) {
            val json = JSONObject(context.assets.open("i18n/$language.json").bufferedReader().use { it.readText() })
            json.keys().asSequence().associateWith { json.getString(it) }
        } }
        catalog[value]?.let { return it }
        // Dynamic UI strings preserve filenames, numeric values and user-authored content.
        for ((key, translation) in catalog) {
            if (!key.contains("{0}")) continue
            val parts = Regex("\\{[0-9]+\\}").split(key)
            val match = Regex(parts.joinToString("(.*?)") { Regex.escape(it) }).matchEntire(value) ?: continue
            var result = translation
            match.groupValues.drop(1).forEachIndexed { index, argument -> result = result.replace("{$index}", argument) }
            return result
        }
        if (value.contains('\n')) return value.split('\n').joinToString("\n") { t(context,it) }
        val prefix = Regex("^([^\\p{L}\\p{N}]*)(.+)$").matchEntire(value)
        if (prefix != null && prefix.groupValues[1].isNotEmpty()) return prefix.groupValues[1] + (catalog[prefix.groupValues[2]] ?: prefix.groupValues[2])
        return value
    }
    fun style(view: View) {
        if (view.tag == "media") return
        val context = view.context
        if(view.tag=="primary-action" && view is Button) { view.setTextColor(StudioDesign.primaryText(context)); return }
        view.contentDescription?.let { view.contentDescription = t(context,it) }
        if (view is TextView) {
            if (view !is EditText && view !is AppText && view !is AppButton) view.text = t(context,view.text)
            view.hint?.let { view.hint = t(context,it) }
            view.setTextColor(foreground(context,view.currentTextColor))
            view.setHintTextColor(StudioDesign.muted(context))
            if (view is Button) { view.minHeight = dp(context,44); view.minimumHeight = dp(context,44) }
        }
        run {
            fun surface(color: Int): Int {
                if(!isDark(color)) return color
                val level=(Color.red(color)+Color.green(color)+Color.blue(color))/3
                return if(level<=22) StudioDesign.background(context) else if(level<=34) StudioDesign.surface(context) else StudioDesign.raised(context)
            }
            when (val background = view.background) {
                is ColorDrawable -> if (isDark(background.color)) view.setBackgroundColor(surface(background.color))
                is GradientDrawable -> {
                    val color = background.color?.defaultColor
                    if (color != null && isDark(color)) background.setColor(surface(color))
                    else if (background.colors?.all { isDark(it) } == true) background.colors = intArrayOf(StudioDesign.surface(context),StudioDesign.raised(context))
                }
            }
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) style(view.getChildAt(index))
    }
    private fun isDark(color: Int) = Color.alpha(color) > 0 && Color.red(color) < 65 && Color.green(color) < 65 && Color.blue(color) < 65
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}

open class BaseActivity : Activity() {
    private var appearance = ""
    private var backCallback: android.window.OnBackInvokedCallback?=null
    override fun attachBaseContext(base: Context) { super.attachBaseContext(AppPreferences.localized(base)) }
    override fun onCreate(state: Bundle?) {
        setTheme(when(AppPreferences.theme(this)) { "gold" -> R.style.Theme_Lumen_Gold; "violet" -> R.style.Theme_Lumen_Violet; "light" -> R.style.Theme_Lumen_Light; else -> R.style.Theme_Lumen })
        super.onCreate(state)
        if(android.os.Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        appearance = "${AppPreferences.theme(this)}:${AppPreferences.language(this)}"
        if(android.os.Build.VERSION.SDK_INT>=33) {
            backCallback=android.window.OnBackInvokedCallback { handleBack() }.also {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,it)
            }
        }
    }
    protected open fun handleBack() { finish() }
    // Android 13+ uses the registered platform dispatcher; this bridge serves API 26–32.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Deprecated("Legacy bridge for Android 8–12")
    override fun onBackPressed() { handleBack() }
    override fun onDestroy() {
        if(android.os.Build.VERSION.SDK_INT>=33) backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        super.onDestroy()
    }
    override fun setContentView(view: View) {
        view.layoutDirection = if(AppPreferences.language(this) == "ur") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        if(android.os.Build.VERSION.SDK_INT >= 30) {
            val left = view.paddingLeft; val top = view.paddingTop; val right = view.paddingRight; val bottom = view.paddingBottom
            view.setOnApplyWindowInsetsListener { target,insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime())
                target.setPadding(left+bars.left,top+bars.top,right+bars.right,bottom+bars.bottom)
                insets
            }
        }
        Ui.style(view); super.setContentView(view); view.requestApplyInsets()
    }
    override fun onPause() { AdsController.paused(this); super.onPause() }
    override fun onResume() {
        super.onResume()
        AdsController.resumed(this)
        if (appearance != "${AppPreferences.theme(this)}:${AppPreferences.language(this)}") recreate()
    }
}

class AppText(context: Context) : TextView(context) {
    override fun setText(text: CharSequence?, type: BufferType?) { super.setText(Ui.t(context,text),type) }
    override fun setTextColor(color: Int) { super.setTextColor(Ui.foreground(context,color)) }
}
class AppButton(context: Context) : Button(context) {
    init { backgroundTintList=null; gravity=android.view.Gravity.CENTER; isAllCaps=false; includeFontPadding=false; minWidth=0; minimumWidth=0; minHeight=Ui.dp(context,44); minimumHeight=Ui.dp(context,44); maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END; stateListAnimator=null }
    override fun setText(text: CharSequence?, type: TextView.BufferType?) { super.setText(Ui.t(context,text),type) }
    override fun setTextColor(color: Int) { super.setTextColor(if(tag=="primary-action") StudioDesign.primaryText(context) else Ui.foreground(context,color)) }
}
class AppDialog(context: Context) : AlertDialog.Builder(context) {
    private val owner = context
    override fun create(): AlertDialog = super.create().apply {
        setOnShowListener {
            window?.setBackgroundDrawable(ColorDrawable(StudioDesign.surface(owner)))
            window?.decorView?.let { Ui.style(it) }
            listOf(DialogInterface.BUTTON_POSITIVE,DialogInterface.BUTTON_NEGATIVE,DialogInterface.BUTTON_NEUTRAL).forEach { which ->
                getButton(which)?.setTextColor(StudioDesign.accent(owner))
            }
        }
    }
    override fun setTitle(title: CharSequence?) = apply { super.setTitle(Ui.t(owner,title)) }
    override fun setMessage(message: CharSequence?) = apply { super.setMessage(Ui.t(owner,message)) }
    override fun setItems(items: Array<out CharSequence>?, listener: DialogInterface.OnClickListener?) = apply {
        super.setItems(items?.map { Ui.t(owner,it) }?.toTypedArray(),listener)
    }
    override fun setPositiveButton(text: CharSequence?, listener: DialogInterface.OnClickListener?) = apply { super.setPositiveButton(Ui.t(owner,text),listener) }
    override fun setNegativeButton(text: CharSequence?, listener: DialogInterface.OnClickListener?) = apply { super.setNegativeButton(Ui.t(owner,text),listener) }
    override fun setView(view: View?) = apply { if(view != null) Ui.style(view); super.setView(view) }
}
