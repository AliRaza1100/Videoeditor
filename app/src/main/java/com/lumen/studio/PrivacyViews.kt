package com.lumen.studio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class PrivacyPolicyView(private val activity: Activity,navigate: (String)->Unit): LinearLayout(activity) {
    init {
        orientation=VERTICAL; setBackgroundColor(StudioDesign.background(activity)); val dp={ n: Int -> Ui.dp(activity,n) }
        addView(StudioDesign.toolbar(activity,"Privacy policy") { navigate("settings") })
        val body=LinearLayout(activity).apply { orientation=VERTICAL; setPadding(dp(24),dp(12),dp(24),dp(28)) }
        fun paragraph(value: String,heading: Boolean=false) { body.addView(StudioDesign.label(activity,value,if(heading) 18f else 14f,secondary=!heading,bold=heading).apply { setTextIsSelectable(true); if(heading && android.os.Build.VERSION.SDK_INT>=28) isAccessibilityHeading=true },LayoutParams(-1,-2).apply { topMargin=dp(if(heading) 24 else 10) }) }
        if(BuildConfig.POLICY_PLACEHOLDER) paragraph("Test policy: the developer name and contact below are examples, not a published privacy notice.")
        val policy=JSONObject(activity.assets.open(if(BuildConfig.ADS_ENABLED) "legal/privacy-ads.json" else if(AdminConnection.configured) "legal/privacy-connected.json" else "legal/privacy-policy.json").bufferedReader().use { it.readText() })
        paragraph("Lumen Studio · ${policy.getString("effectiveDate")}")
        paragraph("Developer",true); paragraph(BuildConfig.DEVELOPER_NAME)
        paragraph("Privacy contact",true); paragraph(if(BuildConfig.POLICY_PLACEHOLDER) "${BuildConfig.SUPPORT_EMAIL} (example; not monitored)" else BuildConfig.SUPPORT_EMAIL)
        val sections=policy.getJSONArray("sections")
        for(i in 0 until sections.length()) { val section=sections.getJSONObject(i); paragraph(section.getString("title"),true); paragraph(section.getString("body")) }
        if(!BuildConfig.POLICY_PLACEHOLDER) body.addView(StudioDesign.button(activity,"Open published policy") {
            try { activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(BuildConfig.PRIVACY_URL))) } catch(_: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(activity,"No browser is available.",android.widget.Toast.LENGTH_SHORT).show() }
        },LayoutParams(-1,dp(52)).apply { topMargin=dp(24) })
        body.addView(StudioDesign.button(activity,"Contact Us") { ContactUs.show(activity) },LayoutParams(-1,dp(52)).apply { topMargin=dp(12) })
        addView(ScrollView(activity).apply { addView(body) },LayoutParams(-1,0,1f))
    }
}

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
object EditingStorage {
    fun epoch(context: Context)=context.getSharedPreferences("editing-data",Context.MODE_PRIVATE).getLong("epoch",0)
    fun bytes(context: Context): Long {
        val directories=listOf("projects","sounds","imports","diagnostics").map { File(context.filesDir,it) }+context.cacheDir
        return directories.sumOf { directory -> directory.walkTopDown().filter { it.isFile }.sumOf { it.length() } }+
            context.filesDir.listFiles().orEmpty().filter { generated(it) }.sumOf { it.length() }
    }
    private fun generated(file: File)=file.isFile && (file.name.startsWith("sticker-") || file.name.startsWith("freeze-")) && file.extension=="png"
    fun clear(context: Context)=synchronized(ProjectStore) {
        check(!VideoExport.busy && !SoundLibrary.busy) { "Wait for export or audio import to finish before clearing editing data." }
        check(context.getSharedPreferences("editing-data",Context.MODE_PRIVATE).edit().putLong("epoch",epoch(context)+1).commit()) { "Unable to invalidate open drafts. Try again." }
        val root=context.filesDir.canonicalFile
        listOf("projects","sounds","imports","diagnostics").forEach { name ->
            val directory=File(root,name).canonicalFile
            check(directory.path.startsWith(root.path+File.separator))
            check(!directory.exists() || directory.deleteRecursively()) { "Some editing files could not be removed. Try again." }
        }
        root.listFiles().orEmpty().filter { generated(it) }.forEach { check(it.delete()) { "A generated file could not be removed." } }
        context.cacheDir.listFiles().orEmpty().forEach { check(it.deleteRecursively()) { "Some temporary files could not be removed." } }
        context.getSharedPreferences("exports",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("project-order",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("project-presentation",Context.MODE_PRIVATE).edit().clear().commit()
        context.contentResolver.persistedUriPermissions.forEach { grant ->
            try {
                if(grant.isReadPermission && grant.isWritePermission) context.contentResolver.releasePersistableUriPermission(grant.uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                else if(grant.isReadPermission) context.contentResolver.releasePersistableUriPermission(grant.uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                else if(grant.isWritePermission) context.contentResolver.releasePersistableUriPermission(grant.uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch(_: SecurityException) { }
        }
        PreviewImages.clearMemory(); StillPreviewRenderer.clearMemory(); FrameEffectRenderer.clearMemory()
    }
}

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class StorageDataView(private val activity: Activity,navigate: (String)->Unit): LinearLayout(activity) {
    private val worker=Executors.newSingleThreadExecutor()
    private var revision=0
    private val status: TextView
    init {
        orientation=VERTICAL; setBackgroundColor(StudioDesign.background(activity)); val dp={ n: Int -> Ui.dp(activity,n) }
        val header=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(12),dp(12),dp(20),dp(8)) }
        header.addView(StudioDesign.button(activity,"←") { navigate("settings") }.apply { background=null; contentDescription="Back" },LayoutParams(dp(48),dp(48)))
        header.addView(StudioDesign.label(activity,"Storage & data",24f,bold=true),LayoutParams(0,-2,1f)); addView(header)
        val body=LinearLayout(activity).apply { orientation=VERTICAL; setPadding(dp(24),dp(12),dp(24),dp(24)) }
        body.addView(StudioDesign.label(activity,"Your work stays on your device",21f,bold=true))
        body.addView(StudioDesign.label(activity,"Drafts are kept until you delete them. Selected media is accessed through Android's picker; no whole-library permission is needed.",14f,secondary=true),LayoutParams(-1,-2).apply { topMargin=dp(12) })
        status=StudioDesign.label(activity,"Checking local storage…",14f); body.addView(status,LayoutParams(-1,-2).apply { topMargin=dp(24); bottomMargin=dp(20) })
        body.addView(StudioDesign.button(activity,"Manage projects") { navigate("projects") },LayoutParams(-1,dp(52)))
        body.addView(StudioDesign.label(activity,"Clear editing data removes drafts, imported sounds, private copies, generated stickers and freeze frames, temporary playback files and saved file access. It preserves your appearance settings, original device media and exported files outside app storage.",14f,secondary=true),LayoutParams(-1,-2).apply { topMargin=dp(24); bottomMargin=dp(16) })
        val clear=StudioDesign.button(activity,"Clear editing data") { }
        clear.setTextColor(if(AppPreferences.light(activity)) android.graphics.Color.rgb(179,38,30) else android.graphics.Color.rgb(240,137,152))
        clear.setOnClickListener {
            if(VideoExport.busy || SoundLibrary.busy) { status.text="Wait for export or audio import to finish."; return@setOnClickListener }
            AppDialog(activity).setTitle("Clear editing data?").setMessage("This permanently removes all drafts, imported sounds and private editing files. Original media and exported files outside app storage remain. This cannot be undone.")
                .setNegativeButton("Cancel",null).setPositiveButton("Clear editing data") { _,_ ->
                    clear.isEnabled=false; status.text="Clearing editing data…"; val current=++revision
                    worker.execute {
                        val result=runCatching { EditingStorage.clear(activity.applicationContext) }
                        activity.runOnUiThread { if(!activity.isDestroyed && isAttachedToWindow && current==revision) { clear.isEnabled=true; status.text=if(result.isSuccess) "Editing data cleared. Original media and exported files were preserved." else result.exceptionOrNull()?.message ?: "Unable to clear editing data." } }
                    }
                }.show()
        }
        body.addView(clear,LayoutParams(-1,dp(52)))
        body.addView(StudioDesign.button(activity,"Privacy policy") { navigate("privacy") },LayoutParams(-1,dp(52)).apply { topMargin=dp(12) })
        addView(ScrollView(activity).apply { addView(body) },LayoutParams(-1,0,1f))
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); val current=++revision; worker.execute { val bytes=runCatching { EditingStorage.bytes(activity.applicationContext) }.getOrDefault(0); activity.runOnUiThread { if(!activity.isDestroyed && current==revision && isAttachedToWindow) status.text="${ProjectStore.list(activity).size} drafts · ${android.text.format.Formatter.formatShortFileSize(activity,bytes)} ${Ui.t(activity,"local editing data")}" } } }
    override fun onDetachedFromWindow() { revision++; worker.shutdown(); super.onDetachedFromWindow() }
}
