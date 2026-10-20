package com.lumen.studio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import java.io.File

class SoundLibraryActivity : BaseActivity() {
    private val green get()=StudioDesign.accent(this)
    private val handler = Handler(Looper.getMainLooper())
    private var selected: String? = null
    private var previewPlayer: MediaPlayer? = null
    private var seenRevision = -1L
    private var previewId: String? = null
    private var playingButton: Button? = null
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var apply: Button
    private lateinit var addAudio: Button
    private lateinit var extractVideo: Button
    private var sounds = emptyList<LibrarySound>()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(14).toFloat() }
    private fun label(value: String, size: Float = 13f) = AppText(this).apply { text = value; textSize = size; setTextColor(Color.rgb(235,239,247)); setPadding(dp(10),dp(8),dp(10),dp(8)) }
    private fun button(value: String, action: () -> Unit) = AppButton(this).apply {
        text = value; isAllCaps = false; textSize = 13f; setTextColor(green)
        background = shape(Color.rgb(28,32,37)); setOnClickListener { action() }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        selected = saved?.getString("selected")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(15,17,20)); setPadding(dp(12),dp(8),dp(12),dp(12)) }
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(button("←") { finish() }, LinearLayout.LayoutParams(dp(48),dp(48)))
            addView(label("Sound",22f))
        })
        root.addView(label("Add a sound",17f))
        addAudio = button("♫  Import sound from device") { pick("audio/*", 401) }
        extractVideo = button("▣  Extract sound from video") { openVideoGallery() }
        root.addView(addAudio, LinearLayout.LayoutParams(-1,dp(54)))
        root.addView(extractVideo, LinearLayout.LayoutParams(-1,dp(54)).apply { topMargin = dp(8) })
        status = label("")
        root.addView(status)
        root.addView(LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            addView(label("Your saved sounds",17f),LinearLayout.LayoutParams(0,-2,1f))
            addView(button("Clear selection") { selected=null; drawSounds(); updateSelection() },LinearLayout.LayoutParams(-2,dp(44)))
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1,0,1f))
        apply = button("Apply selected sound") {
            val sound = sounds.firstOrNull { it.id == selected } ?: return@button
            if (!File(sound.path).isFile) { status.text = "This sound is no longer available."; refresh(); return@button }
            stopPreview()
            setResult(RESULT_OK, Intent().putExtra("sound_uri", Uri.fromFile(File(sound.path)).toString()).putExtra("sound_name", sound.name))
            finish()
        }.apply { tag="primary-action"; background = shape(StudioDesign.primary(this@SoundLibraryActivity)); setTextColor(Color.WHITE) }
        root.addView(apply, LinearLayout.LayoutParams(-1,dp(52)))
        setContentView(root)
        refresh()
    }

    @Suppress("DEPRECATION")
    private fun openVideoGallery() {
        stopPreview()
        startActivityForResult(Intent(this,MediaPickerActivity::class.java).putExtra("mode","video").putExtra("locked_type",true).putExtra("max_items",1).putExtra("picker_title","Extract sound from video"),402)
    }
    @Suppress("DEPRECATION")
    private fun pick(type: String, code: Int) {
        stopPreview()
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            this.type = type; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, code)
    }
    @Deprecated("Platform activity result API")
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code,result,data)
        if (result != RESULT_OK || (code != 401 && code != 402)) return
        val uri = data?.data ?: data?.getStringArrayListExtra("uris")?.firstOrNull()?.let { Uri.parse(it) } ?: return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
        SoundLibrary.import(this,uri,code == 402)
        refresh()
    }

    private fun refresh() {
        val previous = sounds.map { it.id }; val previousRevision=seenRevision; val revision=SoundLibrary.revision
        var readFailed = false
        sounds = try { SoundLibrary.list(this).asReversed() } catch (_: Exception) {
            readFailed = true; emptyList()
        }
        seenRevision = revision
        status.text = if (readFailed) "Unable to read the saved sound library." else SoundLibrary.status.ifEmpty { "Import audio or extract a video's soundtrack. Sounds are saved on this device." }
        addAudio.isEnabled = !SoundLibrary.busy; extractVideo.isEnabled = !SoundLibrary.busy
        if (selected != null && sounds.none { it.id == selected }) selected = null
        if (previous != sounds.map { it.id } || previousRevision!=seenRevision || list.childCount == 0) drawSounds()
        updateSelection()
    }
    private fun drawSounds() {
        list.removeAllViews()
        if(SoundLibrary.busy) {
            val pending=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(12),dp(14),dp(12)); background=shape(StudioDesign.raised(this@SoundLibraryActivity)); contentDescription="Sound import in progress" }
            pending.addView(label(SoundLibrary.status,15f)); pending.addView(label(SoundLibrary.importingName,12f))
            pending.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=100; isIndeterminate=SoundLibrary.progress<0; progress=SoundLibrary.progress.coerceAtLeast(0); progressTintList=android.content.res.ColorStateList.valueOf(green); contentDescription=if(isIndeterminate) "Importing sound" else "Import ${progress}%" },LinearLayout.LayoutParams(-1,dp(12)))
            pending.addView(label(if(SoundLibrary.progress>=0) "${SoundLibrary.progress}% · saving on this device" else "Saving on this device…",12f))
            list.addView(pending,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
        }
        if (sounds.isEmpty() && !SoundLibrary.busy) { list.addView(label("No saved sounds yet. Choose an option above to add one.")); return }
        sounds.forEach { sound ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6),dp(6),dp(6),dp(6)); background = StudioDesign.shape(this@SoundLibraryActivity,StudioDesign.surface(this@SoundLibraryActivity),14,true); if(selected==sound.id) foreground=GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius=dp(14).toFloat(); setStroke(dp(2),green) }
            }
            row.setOnClickListener { selected=if(selected==sound.id) null else sound.id; drawSounds(); updateSelection() }
            row.addView(button(if (previewId == sound.id) "Ⅱ" else "▶") { viewSound(sound) }.apply {
                contentDescription = "Preview ${sound.name}"
                tag = sound.id
            }, LinearLayout.LayoutParams(dp(48),dp(48)))
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(sound.name,14f))
                val seconds = sound.durationMs / 1000
                addView(label("%02d:%02d · %s".format(seconds/60,seconds%60,Ui.t(this@SoundLibraryActivity,if(sound.extracted) "Extracted from video" else "Device audio")),11f))
            },LinearLayout.LayoutParams(0,-2,1f))
            row.addView(button(if (selected == sound.id) "●" else "○") { selected = if(selected == sound.id) null else sound.id; drawSounds(); updateSelection() }.apply {
                contentDescription = if(selected == sound.id) "Deselect ${sound.name}" else "Select ${sound.name}"; isSelected = selected == sound.id
            },LinearLayout.LayoutParams(dp(48),dp(48)))
            list.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(8) })
        }
        Ui.style(list)
    }
    private fun updateSelection() {
        val sound = sounds.firstOrNull { it.id == selected }
        apply.isEnabled = sound != null; apply.alpha = if(sound == null) .4f else 1f
        apply.text = if(sound == null) "Select a sound to apply" else "Apply sound  →"
    }
    private fun viewSound(sound: LibrarySound) {
        if (previewId == sound.id) { stopPreview(); return }
        stopPreview()
        previewId = sound.id
        drawSounds()
        playingButton = (0 until list.childCount).mapNotNull { list.getChildAt(it) as? LinearLayout }
            .mapNotNull { it.getChildAt(0) as? Button }.firstOrNull { it.tag == sound.id }
        try {
            previewPlayer = MediaPlayer().apply {
                setDataSource(sound.path)
                setOnPreparedListener { if(previewId == sound.id) it.start() }
                setOnCompletionListener { stopPreview() }
                setOnErrorListener { _,_,_ -> status.text = "Unable to preview this sound."; stopPreview(); true }
                prepareAsync()
            }
        } catch (_: Exception) { status.text = "Unable to preview this sound."; stopPreview() }
    }
    private fun stopPreview() {
        previewPlayer?.release(); previewPlayer = null; previewId = null
        playingButton?.text = "▶"; playingButton = null
        if (::list.isInitialized) (0 until list.childCount).forEach { index ->
            ((list.getChildAt(index) as? LinearLayout)?.getChildAt(0) as? Button)?.text = "▶"
        }
    }
    private val poll = object : Runnable {
        override fun run() { if (!isDestroyed) { if(seenRevision != SoundLibrary.revision) refresh(); handler.postDelayed(this,700) } }
    }
    override fun onResume() { super.onResume(); refresh(); handler.post(poll) }
    override fun onPause() { handler.removeCallbacks(poll); stopPreview(); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) { out.putString("selected",selected); super.onSaveInstanceState(out) }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); stopPreview(); super.onDestroy() }
}

