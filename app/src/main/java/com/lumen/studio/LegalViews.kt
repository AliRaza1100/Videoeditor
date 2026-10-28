package com.lumen.studio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONObject

object TermsConsent {
    val VERSION get()=if(AdminConnection.configured) "2026-10-20-connected-v1" else "2026-10-20"
    fun accepted(context: Context)=context.getSharedPreferences("onboarding",Context.MODE_PRIVATE).getString("accepted_terms",null)==VERSION
    fun setAccepted(context: Context,value: Boolean) {
        context.getSharedPreferences("onboarding",Context.MODE_PRIVATE).edit().apply {
            if(value) putString("accepted_terms",VERSION) else remove("accepted_terms")
        }.apply()
    }
}

/** Bundled terms remain readable without a connection or granting WebView file access. */
class TermsActivity: BaseActivity() {
    private var web: WebView?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(StudioDesign.background(this@TermsActivity)) }
        root.addView(StudioDesign.toolbar(this,"Terms & Conditions") { finish() })
        val policy=JSONObject(assets.open(if(AdminConnection.configured) "legal/terms-connected.json" else "legal/terms.json").bufferedReader().use { it.readText() })
        fun escape(s: String)=android.text.TextUtils.htmlEncode(s)
        fun css(color: Int)=String.format(java.util.Locale.US,"#%06X",color and 0xffffff)
        val content=buildString {
            append("<!doctype html><html lang='en'><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:24px;font:16px/1.65 sans-serif;background:${css(StudioDesign.background(this@TermsActivity))};color:${css(StudioDesign.text(this@TermsActivity))};overflow-wrap:anywhere}h1{font-size:26px}h2{font-size:20px;margin-top:28px}</style></head><body><h1>Lumen Studio</h1>")
            append("<p>Effective date: ${escape(policy.getString("effectiveDate"))}</p>")
            if(BuildConfig.POLICY_PLACEHOLDER) append("<p><strong>Test edition: developer and support details are examples. Live support is not yet configured.</strong></p>")
            append("<p>Developer: ${escape(BuildConfig.DEVELOPER_NAME)}<br>Contact: ${escape(BuildConfig.SUPPORT_EMAIL)}</p>")
            val sections=policy.getJSONArray("sections")
            for(i in 0 until sections.length()) { val section=sections.getJSONObject(i); append("<h2>${escape(section.getString("title"))}</h2><p>${escape(section.getString("body"))}</p>") }
            append("</body></html>")
        }
        web=WebView(this).apply {
            setBackgroundColor(StudioDesign.background(this@TermsActivity))
            settings.javaScriptEnabled=false; settings.allowFileAccess=false; settings.allowContentAccess=false
            settings.blockNetworkLoads=true; settings.domStorageEnabled=false
            webViewClient=object: WebViewClient() {
                @Deprecated("Legacy navigation callback")
                override fun shouldOverrideUrlLoading(view: WebView?,url: String?)=true
                override fun shouldOverrideUrlLoading(view: WebView?,request: android.webkit.WebResourceRequest?)=true
            }
            loadDataWithBaseURL(null,content,"text/html","UTF-8",null)
        }
        root.addView(web,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
    }
    override fun onDestroy() { web?.destroy(); web=null; super.onDestroy() }
}

class AboutUsView(activity: Activity,navigate: (String)->Unit): LinearLayout(activity) {
    init {
        orientation=VERTICAL; setBackgroundColor(StudioDesign.background(activity))
        addView(StudioDesign.toolbar(activity,"About Us") { navigate("settings") })
        val body=LinearLayout(activity).apply { orientation=VERTICAL; setPadding(Ui.dp(activity,24),Ui.dp(activity,16),Ui.dp(activity,24),Ui.dp(activity,24)) }
        listOf("Lumen Studio" to true,
            "A space for your next story. Lumen Studio brings video and photo editing to your phone, from your first clip to your finished export." to false,
            "Trim and arrange clips, shape their speed, add music, and express your ideas with text, stickers, filters and effects. Reopen your saved drafts to keep creating at your own pace." to false,
            "Your projects stay on this device. This edition has no account or cloud sync and does not upload your media. Choose what to import, where to save an export, and who to share it with." to false,
            "Export options depend on your source media and device capabilities. A higher export resolution cannot restore detail missing from the original." to false,
            "Version ${BuildConfig.VERSION_NAME}" to false).forEach { (value,title) -> body.addView(StudioDesign.label(activity,value,if(title) 26f else 16f,bold=title).apply { setTextIsSelectable(true) },LayoutParams(-1,-2).apply { bottomMargin=Ui.dp(activity,20) }) }
        body.addView(StudioDesign.button(activity,"Contact Us") { ContactUs.show(activity) },LayoutParams(-1,-2))
        addView(ScrollView(activity).apply { addView(body) },LayoutParams(-1,0,1f))
    }
}

object ContactUs {
    fun show(activity: Activity,onSent: ()->Unit = {}) {
        val ticketId=java.util.UUID.randomUUID().toString()
        val body=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(Ui.dp(activity,24),Ui.dp(activity,8),Ui.dp(activity,24),Ui.dp(activity,12)) }
        fun field(hintText: String,multiline: Boolean)=EditText(activity).apply {
            hint=hintText; setTextColor(StudioDesign.text(activity)); setHintTextColor(StudioDesign.muted(activity))
            inputType=android.text.InputType.TYPE_CLASS_TEXT or if(multiline) android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES else android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(!multiline); minLines=if(multiline) 4 else 1; maxLines=if(multiline) 8 else 1
            filters=arrayOf(android.text.InputFilter.LengthFilter(if(multiline) 4000 else 120)); gravity=Gravity.TOP
        }
        body.addView(StudioDesign.label(activity,if(AdminConnection.configured) "Send shares this reason, description and a random installation ID with the app administrator. Read replies in Settings > Support inbox. Do not include passwords or private media." else if(BuildConfig.POLICY_PLACEHOLDER) "Support is not configured in this test edition. You can fill the form, but sending is unavailable until a real address is added." else "Send opens your email app with these details. Review and send the email there. Do not include private media or passwords.",14f,secondary=true))
        body.addView(StudioDesign.label(activity,"Reason / title",14f,bold=true))
        val reason=field("Reason",false); body.addView(reason,LinearLayout.LayoutParams(-1,-2))
        body.addView(StudioDesign.label(activity,"Description",14f,bold=true))
        val description=field("Describe how we can help",true); body.addView(description,LinearLayout.LayoutParams(-1,-2))
        val dialog=AppDialog(activity).setTitle("Contact Us").setView(ScrollView(activity).apply { addView(body) }).setNegativeButton("Cancel",null).setPositiveButton("Send",null).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(StudioDesign.surface(activity)))
            dialog.window?.decorView?.let { Ui.style(it) }
            listOf(android.app.AlertDialog.BUTTON_POSITIVE,android.app.AlertDialog.BUTTON_NEGATIVE).forEach { dialog.getButton(it).setTextColor(StudioDesign.accent(activity)) }
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title=reason.text.toString().trim(); val details=description.text.toString().trim()
                when {
                    title.isEmpty() -> { reason.error="Enter a reason"; reason.requestFocus() }
                    details.isEmpty() -> { description.error="Enter a description"; description.requestFocus() }
                    AdminConnection.configured -> {
                        val send=dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);send.isEnabled=false
                        AdminConnection.sendTicket(activity,ticketId,title,details) { result ->
                            if(activity.isDestroyed || !dialog.isShowing)return@sendTicket
                            send.isEnabled=true
                            result.fold(onSuccess={dialog.dismiss();onSent();Toast.makeText(activity,"Message sent. Replies appear in Support inbox.",Toast.LENGTH_LONG).show()},onFailure={ description.error=it.message ?: "Message was not sent. Try again." })
                        }
                    }
                    BuildConfig.POLICY_PLACEHOLDER -> Toast.makeText(activity,"Support address is not configured. Nothing was sent.",Toast.LENGTH_LONG).show()
                    else -> try {
                        val uri=Uri.parse("mailto:${Uri.encode(BuildConfig.SUPPORT_EMAIL,"@")}?subject=${Uri.encode("Lumen Studio: $title")}&body=${Uri.encode(details)}")
                        activity.startActivity(Intent(Intent.ACTION_SENDTO,uri)); dialog.dismiss()
                    } catch(_: android.content.ActivityNotFoundException) { Toast.makeText(activity,"No email app is available. Contact ${BuildConfig.SUPPORT_EMAIL}.",Toast.LENGTH_LONG).show() }
                }
            }
        }
        dialog.show()
    }
}
