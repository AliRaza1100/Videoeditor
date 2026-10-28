package com.lumen.studio

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.*
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Only user-selected documents are read. No device-wide media/storage permissions. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class MediaPickerActivity: BaseActivity() {
    private data class Item(val uri: String,val name: String,val video: Boolean)
    private val selected=mutableListOf<Item>()
    private val worker=Executors.newSingleThreadExecutor()
    private val maximum get()=intent.getIntExtra("max_items",if(mode=="photo") 9 else 5).coerceIn(1,if(mode=="photo") 9 else 5)
    private var mode="video"
    private var pickerOpen=false
    private var loading=false
    private val direct get()=intent.getBooleanExtra("direct_selection",false)
    private lateinit var rows: LinearLayout
    private lateinit var status: TextView
    private lateinit var use: Button
    private lateinit var choose: Button
    private lateinit var files: Button
    private fun dp(n: Int)=Ui.dp(this,n)
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        mode=saved?.getString("mode") ?: intent.getStringExtra("mode") ?: "video"
        pickerOpen=saved?.getBoolean("picker_open") ?: false
        val uris=saved?.getStringArrayList("uris").orEmpty(); val names=saved?.getStringArrayList("names").orEmpty(); val videos=saved?.getBooleanArray("videos")
        uris.forEachIndexed { index,uri -> selected.add(Item(uri,names.getOrNull(index) ?: "Media",videos?.getOrNull(index) ?: false)) }
        buildScreen()
        if(saved==null) window.decorView.post { launchPicker(false) }
    }
    private fun buildScreen() {
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(StudioDesign.background(this@MediaPickerActivity)); setPadding(dp(20),dp(12),dp(20),dp(20)) }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(StudioDesign.button(this,"←") { finish() }.apply { background=null; contentDescription="Back" },LinearLayout.LayoutParams(dp(48),dp(48)))
        header.addView(StudioDesign.label(this,intent.getStringExtra("picker_title") ?: "Choose media",22f,bold=true),LinearLayout.LayoutParams(0,-2,1f))
        root.addView(header)
        root.addView(StudioDesign.label(this,"Only the files you choose",20f,bold=true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(20) })
        root.addView(StudioDesign.label(this,"Select photos or videos with Android's picker. Lumen Studio does not need access to your whole library.",14f,secondary=true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10); bottomMargin=dp(20) })
        choose=StudioDesign.button(this,"Choose media",primary=true) { launchPicker(false) }; root.addView(choose,LinearLayout.LayoutParams(-1,dp(52)))
        files=StudioDesign.button(this,"Browse files") { launchPicker(true) }; root.addView(files,LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=dp(8) })
        status=StudioDesign.label(this,"",12f,secondary=true); root.addView(status,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(18); bottomMargin=dp(8) })
        rows=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(rows) },LinearLayout.LayoutParams(-1,0,1f))
        use=StudioDesign.button(this,if(maximum==1) "Use selected media" else "Continue",primary=true) { finishSelection() }
        root.addView(use,LinearLayout.LayoutParams(-1,dp(54)))
        if(direct) {
            setContentView(LinearLayout(this).apply {
                gravity=Gravity.CENTER; setBackgroundColor(StudioDesign.background(this@MediaPickerActivity))
                addView(StudioDesign.label(this@MediaPickerActivity,"Opening gallery…",16f))
            })
        } else setContentView(root)
        updateRows()
    }
    @Suppress("DEPRECATION") private fun launchPicker(documents: Boolean) {
        if(pickerOpen || loading) return
        val remaining=maximum-selected.size
        if(remaining<=0) { status.text=Ui.t(this,"Remove a file to choose another."); return }
        val mime=when(mode) { "video" -> "video/*"; "photo" -> "image/*"; else -> "*/*" }
        val picker=if(!documents && Build.VERSION.SDK_INT>=33) Intent(MediaStore.ACTION_PICK_IMAGES).apply {
            // Start on the media grid rather than a remembered Collections tab.
            putExtra("android.provider.extra.PICK_IMAGES_LAUNCH_TAB",1)
            if(mode!="all") type=mime
            if(remaining>1) putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,minOf(remaining,MediaStore.getPickImagesMaxLimit()))
        } else Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type=mime
            if(mode=="all") putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("image/*","video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE,remaining>1)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try { pickerOpen=true; startActivityForResult(picker,210) }
        catch(_: ActivityNotFoundException) { pickerOpen=false; if(!documents) launchPicker(true) else { status.text=Ui.t(this,"No file picker is available on this device."); updateEnabled() } }
    }
    @Deprecated("Platform result bridge") override fun onActivityResult(request: Int,result: Int,data: Intent?) {
        super.onActivityResult(request,result,data); if(request!=210) return
        pickerOpen=false
        if(result!=Activity.RESULT_OK || data==null) { if(direct) finish() else status.text=Ui.t(this,"No files selected. You can choose media when you are ready."); return }
        val uris=linkedSetOf<Uri>(); data.data?.let { uris.add(it) }; data.clipData?.let { clips -> for(i in 0 until clips.itemCount) uris.add(clips.getItemAt(i).uri) }
        if(uris.size>maximum-selected.size) { status.text=Ui.t(this,"Too many files selected. Please choose fewer files."); return }
        loading=true; updateEnabled(); status.text=Ui.t(this,"Checking selected files…")
        val selectionMode=mode
        val existingUris=selected.map { it.uri }.toSet()
        worker.execute {
            val accepted=mutableListOf<Item>(); var failures=0
            uris.forEach { uri ->
                if(uri.toString() in existingUris) return@forEach
                try {
                    val name=contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: "Selected media"
                    val reportedMime=contentResolver.getType(uri).orEmpty()
                    val mime=if(reportedMime.startsWith("video/") || reportedMime.startsWith("image/")) reportedMime else android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.').lowercase()).orEmpty()
                    require(mime.startsWith("video/") || mime.startsWith("image/")) { "Choose a photo or video" }
                    val video=mime.startsWith("video/")
                    require(selectionMode=="all" || (selectionMode=="video")==video)
                    contentResolver.openFileDescriptor(uri,"r")?.use { } ?: error("File unavailable")
                    if(video) require(TimelineEngine.duration(applicationContext,uri.toString())>0)
                    else {
                        val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                        contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it,null,bounds) }
                        require(bounds.outWidth>0 && bounds.outHeight>0)
                    }
                    val stable=try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); uri.toString() }
                    catch(_: SecurityException) { copyToPrivateStorage(uri,mime) }
                    accepted.add(Item(stable,name,video))
                } catch(_: Exception) { failures++ }
            }
            runOnUiThread {
                if(isDestroyed) return@runOnUiThread
                loading=false; selected.addAll(accepted.filter { item -> selected.none { it.uri==item.uri } }); updateRows()
                if(failures>0) status.text=Ui.t(this,"Some files could not be opened. Choose readable photos or videos and try again.")
                if(direct) {
                    if(selected.isNotEmpty()) finishSelection()
                    else { Toast.makeText(this,"These files could not be opened. Please choose other media.",Toast.LENGTH_LONG).show(); finish() }
                }
            }
        }
    }
    private fun copyToPrivateStorage(uri: Uri,mime: String): String {
        val extension=when(mime) { "image/png" -> ".png"; "image/webp" -> ".webp"; "image/jpeg" -> ".jpg"; else -> if(mime.startsWith("video/")) ".mp4" else ".image" }
        val destination=File(File(filesDir,"imports").apply { mkdirs() },UUID.randomUUID().toString()+extension)
        try {
            contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output ->
                val buffer=ByteArray(65536); var size=0L
                while(true) { val count=input.read(buffer); if(count<0) break; size+=count; require(size<=1024L*1024*1024) { "File too large" }; output.write(buffer,0,count) }
            } } ?: error("File unavailable")
            return Uri.fromFile(destination).toString()
        } catch(error: Exception) { destination.delete(); throw error }
    }
    private fun updateEnabled() { choose.isEnabled=!loading && selected.size<maximum; files.isEnabled=choose.isEnabled; use.isEnabled=!loading && selected.isNotEmpty(); use.alpha=if(use.isEnabled) 1f else .4f }
    private fun updateRows() {
        rows.removeAllViews(); status.text="${selected.size}/$maximum · ${Ui.t(this,"Selected files")}"; updateEnabled()
        selected.forEachIndexed { index,item ->
            val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(8),dp(8),0,dp(8)); background=StudioDesign.shape(this@MediaPickerActivity,StudioDesign.surface(this@MediaPickerActivity),14) }
            val thumbnail=ImageView(this).apply { tag="media"; scaleType=ImageView.ScaleType.CENTER_CROP }; row.addView(thumbnail,LinearLayout.LayoutParams(dp(48),dp(48))); PreviewImages.load(this,item.uri,item.video,thumbnail)
            row.addView(StudioDesign.label(this,item.name,12f).apply { tag="media"; maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END; setPadding(dp(8),0,dp(4),0) },LinearLayout.LayoutParams(0,-2,1f))
            listOf("↑","↓","×").forEachIndexed { command,label -> row.addView(StudioDesign.button(this,label) {
                when(command) { 0 -> if(index>0) java.util.Collections.swap(selected,index,index-1); 1 -> if(index<selected.lastIndex) java.util.Collections.swap(selected,index,index+1); else -> selected.removeAt(index) }; updateRows()
            }.apply { background=null; setPadding(0,0,0,0); contentDescription="${listOf("Move earlier","Move later","Remove")[command]}: ${item.name}"; isEnabled=when(command) { 0 -> index>0; 1 -> index<selected.lastIndex; else -> true } },LinearLayout.LayoutParams(dp(44),dp(48))) }
            rows.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        }
    }
    private fun finishSelection() {
        if(loading || selected.isEmpty()) return
        val valid=selected.all { item -> runCatching { contentResolver.openFileDescriptor(Uri.parse(item.uri),"r")?.use { true } ?: false }.getOrDefault(false) }
        if(!valid) { status.text=Ui.t(this,"A selected file is no longer available. Remove it and choose it again."); return }
        setResult(Activity.RESULT_OK,Intent().putStringArrayListExtra("uris",ArrayList(selected.map { it.uri })).putStringArrayListExtra("names",ArrayList(selected.map { it.name })).putExtra("videos",selected.map { it.video }.toBooleanArray()).putExtra("mode",mode)); finish()
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("mode",mode); out.putBoolean("picker_open",pickerOpen); out.putStringArrayList("uris",ArrayList(selected.map { it.uri })); out.putStringArrayList("names",ArrayList(selected.map { it.name })); out.putBooleanArray("videos",selected.map { it.video }.toBooleanArray()); super.onSaveInstanceState(out)
    }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}
