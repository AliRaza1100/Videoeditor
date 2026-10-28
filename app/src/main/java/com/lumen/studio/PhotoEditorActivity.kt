package com.lumen.studio

import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import java.util.UUID
import java.util.concurrent.Executors

/** Image-only workspace. No player, sound, video timeline or video export controls. */
@androidx.media3.common.util.UnstableApi
class PhotoEditorActivity: BaseActivity() {
    private val worker=Executors.newSingleThreadExecutor()
    private val thumbnailWorker=Executors.newSingleThreadExecutor()
    private lateinit var thumbnailRenderer: PhotoRenderer
    private val main=Handler(Looper.getMainLooper())
    private lateinit var renderer: PhotoRenderer
    private lateinit var history: EditHistory
    private val state get()=history.state
    private lateinit var image: ImageView
    private lateinit var preview: FrameLayout
    private lateinit var deleteText: ImageButton
    private lateinit var rotateText: ImageButton
    private lateinit var selectionChrome: View
    private lateinit var tools: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var panelConfirm: Button
    private lateinit var panelHost: LinearLayout
    private lateinit var status: TextView
    private lateinit var undo: ImageButton
    private lateinit var redo: ImageButton
    private lateinit var export: Button
    private var shown: Bitmap?=null
    private var projectId=""
    private var projectName="Photo project"
    private var epoch=0L
    private var selectedText=""
    private var toolbarSelection: String?=null
    @Volatile private var revision=0
    private var displayedRevision=-1
    private var drawQueued=false
    private var crop: CropEditorView?=null
    private var cropRevision=0
    private var pending: EditorState?=null
    private var pendingFormat="PNG"
    private var exporting=false
    @Volatile private var panelGeneration=0
    private val panelBitmaps=mutableListOf<Bitmap>()
    private val emptySlotButtons=mutableListOf<ImageButton>()
    private fun dp(n: Int)=Ui.dp(this,n)
    private fun button(label: String,action: ()->Unit)=StudioDesign.button(this,label,action=action)
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved); epoch=saved?.getLong("epoch") ?: EditingStorage.epoch(this)
        if(epoch!=EditingStorage.epoch(this)) { finish(); return }
        renderer=PhotoRenderer(applicationContext); thumbnailRenderer=PhotoRenderer(applicationContext)
        projectId=saved?.getString("id") ?: intent.getStringExtra("project_id") ?: UUID.randomUUID().toString()
        val stored=ProjectStore.load(this,projectId)
        val uris=intent.getStringArrayListExtra("uris").orEmpty()
        val names=intent.getStringArrayListExtra("names").orEmpty()
        var initial=(saved?.getSerializable("state") as? EditorState ?: stored?.state ?: EditorState(uris.mapIndexed { i,uri -> EditorClip(uri,names.getOrNull(i) ?: "Photo ${i+1}",false) },background=Color.WHITE)).copy(photoEditor=true)
        if((initial.clips.isEmpty() && stored==null && saved==null) || initial.clips.any { it.video }) { Toast.makeText(this,"Choose photos for the photo editor.",Toast.LENGTH_LONG).show(); finish(); return }
        if(stored==null && saved==null && initial.clips.size>1 && initial.photoLayout==null) {
            initial=initial.copy(photoLayout=PhotoLayouts.all(this).firstOrNull { it.cells.size==initial.clips.size }?.id ?: "grid9",ratio="1:1")
        }
        history=EditHistory(initial.normalized())
        (saved?.getSerializable("undo") as? ArrayList<*>)?.filterIsInstance<EditorState>()?.let { history.undo.addAll(it) }
        (saved?.getSerializable("redo") as? ArrayList<*>)?.filterIsInstance<EditorState>()?.let { history.redo.addAll(it) }
        projectName=saved?.getString("name") ?: stored?.name ?: names.firstOrNull()?.substringBeforeLast('.') ?: "Photo project"
        selectedText=saved?.getString("text") ?: ""; pending=saved?.getSerializable("pending") as? EditorState; pendingFormat=saved?.getString("format") ?: "PNG"
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(StudioDesign.background(this@PhotoEditorActivity)) }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(8),0,dp(8),0) }
        header.addView(button("←") { leave() }.apply { contentDescription="Back" },LinearLayout.LayoutParams(dp(48),dp(52)))
        header.addView(StudioDesign.label(this,"Photo editor",19f,bold=true),LinearLayout.LayoutParams(0,-2,1f))
        export=StudioDesign.button(this,"Export",primary=true) { exportOptions() }; header.addView(export,LinearLayout.LayoutParams(-2,dp(48)))
        root.addView(header)
        val historyRow=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(4),dp(8),dp(4)) }
        undo=studioIcon(this,R.drawable.ic_undo,"Undo") { closeCrop(); if(history.undo()) changed() }; redo=studioIcon(this,R.drawable.ic_redo,"Redo") { closeCrop(); if(history.redo()) changed() }
        historyRow.addView(undo,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd=dp(8) }); historyRow.addView(redo,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd=dp(12) })
        historyRow.addView(StudioDesign.label(this,"Drag to position · pinch to resize",11f,secondary=true),LinearLayout.LayoutParams(0,-2,1f)); root.addView(historyRow)
        preview=FrameLayout(this).apply { tag="media"; setBackgroundColor(0xFF17181D.toInt()) }
        image=ImageView(this).apply { tag="media"; scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="Edited photo preview" }
        preview.addView(image,FrameLayout.LayoutParams(-1,-1))
        selectionChrome=object: View(this) {
            private val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeWidth=dp(2).toFloat() }
            override fun onDraw(canvas: Canvas) {
                val bitmap=shown ?: return
                val text=state.projectTexts.orEmpty().firstOrNull { it.id==selectedText } ?: return
                if(crop!=null) return
                val scale=minOf(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height)
                val bounds=PhotoTextGeometry.bounds(bitmap.width,bitmap.height,text)
                canvas.save(); canvas.translate((width-bitmap.width*scale)/2,(height-bitmap.height*scale)/2); canvas.scale(scale,scale)
                ink.color=StudioDesign.accent(this@PhotoEditorActivity); ink.strokeWidth=dp(2)/scale
                canvas.drawRoundRect(bounds,dp(4)/scale,dp(4)/scale,ink); canvas.restore()
            }
        }.apply { importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        preview.addView(selectionChrome,FrameLayout.LayoutParams(-1,-1))
        deleteText=studioIcon(this,R.drawable.ph_trash,"Delete selected photo text") { change(state.copy(projectTexts=state.projectTexts.orEmpty().filter { it.id!=selectedText })); selectedText=""; toolbar() }
        rotateText=studioIcon(this,android.R.drawable.ic_menu_rotate,"Rotate selected photo text") { textChange { it.copy(rotation=(it.rotation+15)%360) } }
        preview.addView(deleteText,FrameLayout.LayoutParams(dp(48),dp(48))); preview.addView(rotateText,FrameLayout.LayoutParams(dp(48),dp(48)))
        preview.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> textHandles() }
        root.addView(preview,LinearLayout.LayoutParams(-1,0,1f))
        status=StudioDesign.label(this,"",12f,secondary=true).apply { setPadding(dp(16),dp(8),dp(16),dp(8)) }; root.addView(status)
        panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),0,dp(12),0); visibility=View.GONE }
        panelConfirm=StudioDesign.button(this,"Done",primary=true) { history.endGroup();saveDraft();if(crop!=null) closeCrop() else clearPanel() }.apply { visibility=View.GONE;contentDescription="Confirm editing changes" }
        panelHost=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=View.GONE }
        panelHost.addView(panelConfirm,LinearLayout.LayoutParams(-1,dp(48)).apply { marginStart=dp(12);marginEnd=dp(12) })
        panelHost.addView(ScrollView(this).apply { addView(panel) },LinearLayout.LayoutParams(-1,0,1f))
        val landscape=resources.configuration.screenWidthDp>resources.configuration.screenHeightDp
        root.addView(panelHost,LinearLayout.LayoutParams(-1,dp((resources.configuration.screenHeightDp*.34f).toInt().coerceIn(if(landscape) 112 else 200,300))))
        tools=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(8),dp(4),dp(8),dp(8)) }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(tools) },LinearLayout.LayoutParams(-1,dp(72)))
        setContentView(root); installGestures(); toolbar(); changed()
    }
    private fun saveDraft(): Boolean {
        if(!::history.isInitialized || epoch!=EditingStorage.epoch(this)) return false
        val result=runCatching { ProjectStore.save(this,SavedProject(projectId,projectName,System.currentTimeMillis(),state),epoch) }
        result.exceptionOrNull()?.let { if(::status.isInitialized && !isDestroyed) status.text="Unable to save photo draft: ${it.message}" }
        return result.isSuccess
    }
    private val save=Runnable { saveDraft() }
    private fun change(next: EditorState) { if(next!=state) { history.change(next); changed() } }
    private fun edit(action: (EditorClip)->EditorClip) { change(ClipEdits.update(state,action)) }
    private fun changed() {
        undo.isEnabled=history.undo.isNotEmpty(); redo.isEnabled=history.redo.isNotEmpty();export.isEnabled=state.clips.isNotEmpty() && !exporting
        val selection=state.projectTexts.orEmpty().firstOrNull { it.id==selectedText }?.id ?: ""
        toolbar()
        main.removeCallbacks(save); main.postDelayed(save,400); queuePreview()
    }
    private val draw=Runnable {
        drawQueued=false
        val version=revision; val snapshot=state
        worker.execute {
            if(version!=revision || isDestroyed) return@execute
            val result=runCatching { renderer.render(snapshot,1080) }
            main.post {
                val bitmap=result.getOrNull()
                if(isDestroyed || version!=revision) bitmap?.recycle()
                else if(bitmap!=null) { displayedRevision=version; val old=shown; shown=bitmap; image.setImageBitmap(bitmap); old?.recycle(); textHandles(); val layout=PhotoLayouts.selected(this,snapshot); status.text=if(layout.cells.size>1) "${minOf(snapshot.clips.size,layout.cells.size)} / ${layout.cells.size} photos · Selected ${snapshot.active+1} · ${layout.name}" else "Photo ${snapshot.active+1} of ${snapshot.clips.size}" }
                else status.text="Preview failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }
    private fun queuePreview() {
        revision++
        if(state.clips.isEmpty()) {
            main.removeCallbacks(draw);drawQueued=false;image.setImageDrawable(null);shown?.recycle();shown=null
            status.text="No photos. Add a photo to continue editing.";textHandles();return
        }
 if(!drawQueued) { drawQueued=true; main.postDelayed(draw,16) } }
    private fun toolbar() {
        textHandles(); tools.removeAllViews()
        val text=state.projectTexts.orEmpty().firstOrNull { it.id==selectedText }
        toolbarSelection=text?.id ?: ""
        val items=if(state.clips.isEmpty()) listOf("Add photos") else if(text==null) listOf("Photos","Remove photo","Crop","Text","Filters","Adjust","Collage","Canvas","Rotate","Flip") else listOf("Edit text","Style","Font","Color","Text background","Text controls","Delete text","Done")
        items.forEach { name -> tools.addView(EditorToolItem(this,name) {
            val text=state.projectTexts.orEmpty().firstOrNull { it.id==selectedText }
            if(name!="Crop") closeCrop()
            when(name) {
                "Photos" -> photos()
                "Add photos" -> pick(9)
                "Remove photo" -> removePhoto()
                "Crop" -> openCrop()
                "Text","Edit text" -> editText(text)
                "Filters" -> filters()
                "Adjust" -> adjustments()
                "Collage" -> collage()
                "Canvas" -> canvasOptions()
                "Rotate" -> edit { it.copy(rotation=(it.rotation+90)%360) }
                "Flip" -> edit { it.copy(flip=!it.flip) }
                "Font" -> textFonts()
                "Style" -> textStyles()
                "Text background" -> textBackground()
                "Outline","Shadow","Alignment","Spacing","Opacity","Size","Rotate text","Text controls" -> textControls()
                "Color" -> colors { color -> textChange { it.copy(color=color) } }
                "Delete text" -> { change(state.copy(projectTexts=state.projectTexts.orEmpty().filter { it.id!=selectedText })); selectedText=""; toolbar() }
                "Done" -> { selectedText=""; toolbar(); clearPanel() }
            }
        },LinearLayout.LayoutParams(dp(if(name.length>12) 104 else 76),dp(56)).apply { marginEnd=dp(2) }) }
    }
    private fun refreshEmptySlots() {
        if(!::preview.isInitialized || !::image.isInitialized) return
        emptySlotButtons.forEach { preview.removeView(it) };emptySlotButtons.clear()
        val bitmap=shown ?: return
        if(crop!=null || state.clips.isEmpty()) return
        val layout=PhotoLayouts.selected(this,state)
        val scale=minOf(image.width.toFloat()/bitmap.width,image.height.toFloat()/bitmap.height)
        val x=(image.width-bitmap.width*scale)/2;val y=(image.height-bitmap.height*scale)/2
        layout.cells.drop(state.clips.size).forEachIndexed { offset,cell ->
            val index=state.clips.size+offset
            val add=studioIcon(this,R.drawable.ic_project_add,"Add photo to collage slot ${index+1}") { pick((layout.cells.size-state.clips.size).coerceAtLeast(1)) }
            preview.addView(add,FrameLayout.LayoutParams(dp(48),dp(48)))
            add.translationX=x+cell.centerX()*bitmap.width*scale-dp(24);add.translationY=y+cell.centerY()*bitmap.height*scale-dp(24)
            emptySlotButtons.add(add)
        }
    }
    private fun textHandles() {
        if(!::deleteText.isInitialized) return
        refreshEmptySlots()
        selectionChrome.invalidate()
        val layer=state.projectTexts.orEmpty().firstOrNull { it.id==selectedText }
        val bitmap=shown
        val visible=layer!=null && bitmap!=null && crop==null
        deleteText.visibility=if(visible) View.VISIBLE else View.GONE; rotateText.visibility=deleteText.visibility
        if(layer==null || bitmap==null || !visible) return
        val scale=minOf(image.width.toFloat()/bitmap.width,image.height.toFloat()/bitmap.height)
        val box=PhotoTextGeometry.bounds(bitmap.width,bitmap.height,layer)
        val x=(image.width-bitmap.width*scale)/2; val y=(image.height-bitmap.height*scale)/2
        val target=dp(48).toFloat();val gap=dp(8).toFloat()
        val maxX=(preview.width-target).coerceAtLeast(0f)
        var left=(x+box.left*scale-target/2).coerceIn(0f,maxX)
        var right=(x+box.right*scale-target/2).coerceIn(0f,maxX)
        if(right-left<target+gap) {
            left=((left+right)/2-(target+gap)/2).coerceIn(0f,(maxX-target-gap).coerceAtLeast(0f))
            right=(left+target+gap).coerceAtMost(maxX)
        }
        deleteText.setPadding(dp(14),dp(14),dp(14),dp(14));rotateText.setPadding(dp(14),dp(14),dp(14),dp(14))
        deleteText.translationX=left;rotateText.translationX=right
        val top=(y+box.top*scale-dp(42)).coerceIn(0f,(preview.height-dp(48)).coerceAtLeast(0).toFloat())
        deleteText.translationY=top; rotateText.translationY=top
    }
    private fun textChange(action: (TextLayer)->TextLayer) { change(state.copy(projectTexts=state.projectTexts.orEmpty().map { if(it.id==selectedText) action(it) else it })) }
    private fun editText(existing: TextLayer?) {
        val layer=existing ?: TextLayer(text="",color=Color.WHITE,shadow=true)
        selectedText=layer.id
        var draft=layer
        val field=EditText(this).apply { hint="Write text"; setText(layer.text); maxLines=5; setSelectAllOnFocus(true); filters=arrayOf(android.text.InputFilter.LengthFilter(2000)) }
        val sample=TextView(this).apply { tag="media";text=layer.text.ifBlank { "Your text" };gravity=Gravity.CENTER;setTextColor(layer.color);textSize=layer.size.coerceIn(12f,36f);setPadding(dp(12),dp(12),dp(12),dp(12));minHeight=dp(72) }
        history.beginGroup()
        field.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {
                val current=state.projectTexts.orEmpty(); val value=s.toString();sample.text=value.ifBlank { "Your text" }
                val next=if(value.isBlank()) current.filter { it.id!=layer.id } else if(current.any { it.id==layer.id }) current.map { if(it.id==layer.id) it.copy(text=value) else it } else current+draft.copy(text=value)
                change(state.copy(projectTexts=next))
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(8),dp(18),dp(16)) }
        body.addView(FrameLayout(this).apply { background=StudioDesign.shape(this@PhotoEditorActivity,0xFF232632.toInt(),12);addView(sample,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER)) },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
        body.addView(field,LinearLayout.LayoutParams(-1,-2))
        fun update(action: (TextLayer)->TextLayer) {
            draft=action(state.projectTexts.orEmpty().firstOrNull { it.id==layer.id } ?: draft)
            textChange { action(it) }
            val style=com.lumen.studio.graphics.GraphicsCatalog.current?.type(draft.style)
            val family=com.lumen.studio.graphics.GraphicsCatalog.current?.fonts?.get(draft.font) ?: "sans-serif"
            val bold=draft.bold ?: ((style?.weight ?: 400)>=600)
            val italic=draft.italic ?: (style?.italic==true)
            field.typeface=Typeface.create(family,if(bold && italic) Typeface.BOLD_ITALIC else if(bold) Typeface.BOLD else if(italic) Typeface.ITALIC else Typeface.NORMAL)
            sample.typeface=field.typeface;sample.textSize=draft.size.coerceIn(12f,36f);sample.setTextColor(draft.color);sample.setBackgroundColor(draft.backgroundColor);val padding=dp((draft.backgroundPadding ?: 8f).toInt());sample.setPadding(padding,padding,padding,padding)
            sample.paintFlags=if(draft.underline ?: (style?.underline==true)) sample.paintFlags or Paint.UNDERLINE_TEXT_FLAG else sample.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            sample.gravity=Gravity.CENTER_VERTICAL or when(draft.alignment) { "Left" -> Gravity.LEFT;"Right" -> Gravity.RIGHT;else -> Gravity.CENTER_HORIZONTAL }
            field.gravity=Gravity.TOP or when(draft.alignment) { "Left" -> Gravity.START;"Right" -> Gravity.END;else -> Gravity.CENTER_HORIZONTAL }
        }
        fun heading(title: String) { body.addView(StudioDesign.label(this,title,13f,bold=true).apply { setPadding(0,dp(16),0,dp(8)) }) }
        fun choices(values: List<String>,current: String="",action: (String)->Unit) {
            val row=LinearLayout(this)
            values.forEach { value ->
                val choice=button(value) { action(value);for(i in 0 until row.childCount) selectStudioCard(row.getChildAt(i),row.getChildAt(i).tag==value) }.apply { tag=value;selectStudioCard(this,value==current);if(values.size>3) typeface=Typeface.create(com.lumen.studio.graphics.GraphicsCatalog.current?.fonts?.get(value) ?: "sans-serif",Typeface.NORMAL) }
                row.addView(choice,LinearLayout.LayoutParams(-2,dp(56)).apply { marginEnd=dp(8) })
            }
            body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false;addView(row) })
        }
        heading("Font")
        val fonts=com.lumen.studio.graphics.GraphicsCatalog.current?.fonts?.keys?.toList().orEmpty().ifEmpty { listOf("Sans","Serif","Monospace") }
        choices(fonts,draft.font ?: "Sans") { name -> update { it.copy(font=name) } }
        heading("Formatting")
        val formats=LinearLayout(this)
        listOf("Bold","Italic","Underline").forEach { name ->
            val style=com.lumen.studio.graphics.GraphicsCatalog.current?.type(draft.style)
            val control=ToggleButton(this).apply { textOn=name;textOff=name;isChecked=when(name) { "Bold" -> draft.bold ?: ((style?.weight ?: 400)>=600);"Italic" -> draft.italic ?: (style?.italic==true);else -> draft.underline ?: (style?.underline==true) };setTextColor(StudioDesign.text(context));buttonTintList=android.content.res.ColorStateList.valueOf(StudioDesign.accent(context));contentDescription=name
                setOnCheckedChangeListener { _,checked -> update { when(name) { "Bold" -> it.copy(bold=checked);"Italic" -> it.copy(italic=checked);else -> it.copy(underline=checked) } } }
            }
            formats.addView(control,LinearLayout.LayoutParams(0,dp(48),1f).apply { marginEnd=dp(8) })
        }
        body.addView(formats)
        heading("Alignment")
        choices(listOf("Left","Center","Right"),draft.alignment ?: "Center") { value -> update { it.copy(alignment=value) } }
        val sizeLabel=StudioDesign.label(this,"Text size · ${draft.size.toInt()}",13f,bold=true).apply { setPadding(0,dp(16),0,dp(8)) };body.addView(sizeLabel)
        body.addView(SeekBar(this).apply { max=194;progress=draft.size.toInt().coerceIn(6,200)-6;contentDescription="Text size";setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?,value: Int,user: Boolean) { if(user) { sizeLabel.text="${Ui.t(this@PhotoEditorActivity,"Text size")} · ${value+6}";update { it.copy(size=(value+6).toFloat()) } } }
            override fun onStartTrackingTouch(bar: SeekBar?) {} override fun onStopTrackingTouch(bar: SeekBar?) {}
        }) },LinearLayout.LayoutParams(-1,dp(48)))
        fun colorControls(title: String,highlight: Boolean) {
            heading(title)
            body.addView(StudioColorPicker(this,if(highlight) draft.backgroundColor else draft.color) { color -> update { if(highlight) it.copy(backgroundColor=color) else it.copy(color=color) } })
        }
        colorControls("Text color",false);colorControls("Background highlight",true)
        heading("Background padding")
        body.addView(SeekBar(this).apply { max=40;progress=(draft.backgroundPadding ?: 8f).toInt();contentDescription="Background padding";setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?,value: Int,user: Boolean) { if(user) update { it.copy(backgroundPadding=value.toFloat()) } }
            override fun onStartTrackingTouch(bar: SeekBar?) {} override fun onStopTrackingTouch(bar: SeekBar?) {}
        }) },LinearLayout.LayoutParams(-1,dp(48)))
        update { it }
        val dialog=AppDialog(this).setTitle("Text studio").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Done",null).show()
        dialog.setOnDismissListener { history.endGroup(); toolbar(); if(state.projectTexts.orEmpty().any { it.id==selectedText }) textOptions(); saveDraft() }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    private fun clearPanel() { if(::panelConfirm.isInitialized) panelConfirm.visibility=View.GONE;panelGeneration++; panelBitmaps.forEach { it.recycle() }; panelBitmaps.clear(); panel.removeAllViews(); panel.visibility=View.GONE; panelHost.visibility=View.GONE }
    private fun choose(title: String,items: List<String>,action: (Int)->Unit) { AppDialog(this).setTitle(title).setItems(items.toTypedArray()) { _,index -> action(index) }.show() }
    private fun slider(title: String,min: Int,max: Int,initial: Int,action: (Int)->Unit) {
        showPanel(title); inlineSlider(title,min,max,initial,action)
    }
    private fun textBackground() {
        val text=state.projectTexts.orEmpty().firstOrNull { it.id==selectedText } ?: return
        colors(text.backgroundColor) { color -> textChange { it.copy(backgroundColor=color) } }
        inlineSlider("Background padding",0,40,(text.backgroundPadding ?: 8f).toInt()) { value -> textChange { it.copy(backgroundPadding=value.toFloat()) } }
    }
    private fun colors(initial: Int?=null,action: (Int)->Unit) {
        showPanel("Color")
        val chosen=initial ?: state.projectTexts.orEmpty().firstOrNull { it.id==selectedText }?.color ?: state.background
        panel.addView(StudioColorPicker(this,chosen,action))
    }
    private fun showPanel(title: String) {
        clearPanel(); panel.visibility=View.VISIBLE; panelHost.visibility=View.VISIBLE;panelConfirm.visibility=View.VISIBLE;panelConfirm.contentDescription="Done: $title"
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(button("‹") { clearPanel() }.apply { contentDescription="Back from $title" },LinearLayout.LayoutParams(dp(48),dp(48)))
        header.addView(StudioDesign.label(this,title,16f,bold=true),LinearLayout.LayoutParams(0,-2,1f)); panel.addView(header)
    }
    private fun chipRow(items: List<String>,selected: String="",action: (String)->Unit) {
        val row=LinearLayout(this)
        items.forEach { name -> row.addView(button(name) { action(name);for(i in 0 until row.childCount) selectStudioCard(row.getChildAt(i),row.getChildAt(i).tag==name) }.apply { tag=name;selectStudioCard(this,name==selected) },LinearLayout.LayoutParams(-2,dp(48)).apply { marginEnd=dp(6) }) }
        panel.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(row) })
    }
    private fun filters(category: String="Photo · Natural") {
        showPanel("Filters · live photo previews")
        val all=com.lumen.studio.graphics.GraphicsCatalog.current!!.filters
        chipRow(listOf("All")+all.map { it.info.category }.distinct(),category) { filters(it) }
        (panel.getChildAt(0) as LinearLayout).addView(button("Edit photo") { clearPanel() },LinearLayout.LayoutParams(-2,dp(48)))
        val generation=panelGeneration
        val row=LinearLayout(this)
        lateinit var strength: SeekBar
        val options=listOf("Original")+all.filter { category=="All" || it.info.category==category }.map { it.info.name }
        options.forEach { name ->
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; contentDescription="Photo filter: $name" }
            val thumbnail=ImageView(this).apply { tag="media"; scaleType=ImageView.ScaleType.CENTER_CROP; setBackgroundColor(StudioDesign.raised(this@PhotoEditorActivity)); contentDescription="Preview of $name" }
            val clip=state.clips[state.active]
            val sample=state.copy(clips=listOf(clip.copy(filter=name,filterIntensity=1f,texts=emptyList())),active=0,photoLayout="single",ratio="Fit",projectTexts=emptyList())
            requestThumbnail(thumbnail,sample,generation)
            card.addView(thumbnail,LinearLayout.LayoutParams(dp(104),dp(92))); card.addView(StudioDesign.label(this,name,12f).apply { gravity=Gravity.CENTER; maxLines=3;tooltipText=name },LinearLayout.LayoutParams(dp(112),dp(56)))
            fun highlight() { for(i in 0 until row.childCount) { val view=row.getChildAt(i); view.isSelected=view.tag==state.clips[state.active].filter; view.background=StudioDesign.shape(this,if(view.isSelected) StudioDesign.raised(this) else StudioDesign.surface(this),12,view.isSelected);selectStudioCard(view,view.isSelected) } }
            card.tag=name; card.isFocusable=true; card.setOnClickListener { edit { it.copy(filter=name,filterIntensity=if(it.filterIntensity==0f && name!="Original") 1f else it.filterIntensity) }; strength.progress=(state.clips[state.active].filterIntensity*100).toInt(); highlight() }; row.addView(card,LinearLayout.LayoutParams(dp(116),dp(152)).apply { marginEnd=dp(8) }); highlight()
        }
        panel.addView(HorizontalScrollView(this).apply { addView(row) })
        strength=inlineSlider("Filter strength",0,100,(state.clips[state.active].filterIntensity*100).toInt()) { value -> edit { it.copy(filterIntensity=value/100f) } }
    }
    private fun inlineSlider(title: String,min: Int,max: Int,initial: Int,action: (Int)->Unit): SeekBar {
        val label=StudioDesign.label(this,"$title · $initial",13f,bold=true); panel.addView(label)
        val slider=SeekBar(this).apply { this.max=max-min; progress=initial.coerceIn(min,max)-min; contentDescription=title
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?,value: Int,user: Boolean) { label.text="$title · ${value+min}"; if(user) action(value+min) }
                override fun onStartTrackingTouch(bar: SeekBar?) { history.beginGroup() }
                override fun onStopTrackingTouch(bar: SeekBar?) { history.endGroup(); saveDraft() }
            })
        }
        panel.addView(slider,LinearLayout.LayoutParams(-1,dp(48)))
        return slider
    }
    private fun adjustments(selected: String="brightness") {
        showPanel("Adjust · selected photo")
        val row=LinearLayout(this)
        PhotoAdjustments.controls.forEach { control ->
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; isSelected=control.id==selected; background=StudioDesign.shape(this@PhotoEditorActivity,StudioDesign.raised(this@PhotoEditorActivity),14,isSelected); contentDescription=control.name; setOnClickListener { adjustments(control.id) } }
            card.addView(StudioDesign.label(this,control.symbol,27f).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(-1,dp(38)))
            card.addView(StudioDesign.label(this,control.name,11f,bold=true).apply { gravity=Gravity.CENTER; maxLines=2 },LinearLayout.LayoutParams(-1,dp(32)))
            row.addView(card,LinearLayout.LayoutParams(dp(88),dp(78)).apply { marginEnd=dp(8) })
        }
        panel.addView(HorizontalScrollView(this).apply { addView(row); post { smoothScrollTo((PhotoAdjustments.controls.indexOfFirst { it.id==selected }*dp(96)-dp(100)).coerceAtLeast(0),0) } })
        val control=PhotoAdjustments.controls.first { it.id==selected }
        inlineSlider(control.name,control.min,control.max,PhotoAdjustments.value(state.clips[state.active],selected)) { value -> edit { PhotoAdjustments.update(it,selected,value) } }
        chipRow(listOf("Reset ${control.name}","Reset all adjustments")) { action -> edit { if(action.startsWith("Reset all")) PhotoAdjustments.reset(it) else PhotoAdjustments.update(it,selected,control.default) }; adjustments(selected) }
    }
    private fun textOptions() = textControls()
    private fun textControls() {
        val text=state.projectTexts.orEmpty().firstOrNull { it.id==selectedText } ?: return
        showPanel("Text controls")
        inlineSlider("Size",6,200,text.size.toInt()) { value -> textChange { it.copy(size=value.toFloat()) } }
        inlineSlider("Opacity",0,100,((text.opacity ?: 1f)*100).toInt()) { value -> textChange { it.copy(opacity=value/100f) } }
        fun pair(first: ()->Unit,second: ()->Unit) {
            val host=panel;val row=LinearLayout(this);val sections=List(2) { LinearLayout(this).apply { orientation=LinearLayout.VERTICAL } }
            // Build using the shared slider binder, then move each control group into its column.
            listOf(first,second).forEachIndexed { index,build -> val start=host.childCount;build();while(host.childCount>start) { val child=host.getChildAt(start);host.removeViewAt(start);sections[index].addView(child) };row.addView(sections[index],LinearLayout.LayoutParams(0,-2,1f).apply { if(index==0) marginEnd=dp(12) }) }
            host.addView(row)
        }
        pair({ inlineSlider("Outline",0,12,text.outlineWidth.toInt()) { v -> textChange { it.copy(outlineWidth=v.toFloat()) } } },
             { inlineSlider("Shadow",0,100,((text.shadowStrength ?: if(text.shadow==false) 0f else 1f)*100).toInt()) { v -> textChange { it.copy(shadow=v>0,shadowStrength=v/100f) } } })
        pair({ inlineSlider("Spacing",-10,50,(text.letterSpacing*100).toInt()) { v -> textChange { it.copy(letterSpacing=v/100f) } } },
             { inlineSlider("Rotate text",-180,180,text.rotation.toInt().coerceIn(-180,180)) { v -> textChange { it.copy(rotation=v.toFloat()) } } })
        val alignment=LinearLayout(this)
        listOf("Left" to R.drawable.ic_align_left,"Center" to R.drawable.ic_align_center,"Right" to R.drawable.ic_align_right).forEach { (name,icon) ->
            alignment.addView(studioIcon(this,icon,"Align $name") { textChange { it.copy(alignment=name) };for(i in 0 until alignment.childCount) selectStudioCard(alignment.getChildAt(i),alignment.getChildAt(i).tag==name) }.apply { tag=name;selectStudioCard(this,name==(text.alignment ?: "Center")) },LinearLayout.LayoutParams(0,dp(48),1f).apply { marginEnd=dp(8) })
        };panel.addView(alignment,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12);bottomMargin=dp(12) })
        chipRow(listOf("Style","Font","Color","Background")) { when(it) { "Style" -> textStyles();"Font" -> textFonts();"Color" -> colors { color -> textChange { it.copy(color=color) } };else -> textBackground() } }
    }
    private fun textStyles() { showPanel("Text styles"); textStyleCards() }
    private fun textStyleCards() {
        val row=LinearLayout(this)
        fun highlight() { val value=state.projectTexts.orEmpty().firstOrNull { it.id==selectedText }?.style;val selected=com.lumen.studio.graphics.GraphicsCatalog.current?.type(value)?.info?.id ?: value;for(i in 0 until row.childCount) selectStudioCard(row.getChildAt(i),row.getChildAt(i).tag==selected) }
        com.lumen.studio.graphics.GraphicsCatalog.current!!.typography.forEach { preset ->
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;tag=preset.info.id;background=StudioDesign.shape(this@PhotoEditorActivity,StudioDesign.surface(this@PhotoEditorActivity),14,true);setPadding(dp(4),dp(4),dp(4),dp(8));contentDescription="Text style: ${preset.info.name}";isFocusable=true;setOnClickListener { textChange { it.copy(style=preset.info.id,color=preset.textColor,bold=null,italic=null,underline=null) };highlight() } }
            card.addView(TextPresetThumbnail(this,"Create",preset.info.id).apply { tag="media" },LinearLayout.LayoutParams(dp(112),dp(84)))
            card.addView(StudioDesign.label(this,preset.info.name,12f,bold=true).apply { gravity=Gravity.CENTER;maxLines=3;tooltipText=preset.info.name },LinearLayout.LayoutParams(dp(112),dp(52)))
            row.addView(card,LinearLayout.LayoutParams(dp(128),dp(152)).apply { marginEnd=dp(10) })
        };highlight();panel.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false;addView(row) })
    }
    private fun textFonts() {
        showPanel("Fonts")
        val row=LinearLayout(this)
        fun highlight() { val selected=state.projectTexts.orEmpty().firstOrNull { it.id==selectedText }?.font;for(i in 0 until row.childCount) selectStudioCard(row.getChildAt(i),row.getChildAt(i).tag==selected) }
        com.lumen.studio.graphics.GraphicsCatalog.current!!.fonts.forEach { (name,family) ->
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;tag=name;setPadding(dp(12),dp(12),dp(12),dp(12));background=StudioDesign.shape(this@PhotoEditorActivity,StudioDesign.surface(this@PhotoEditorActivity),14,true);contentDescription="Font: $name";isFocusable=true;setOnClickListener { textChange { it.copy(font=name) };highlight() } }
            listOf("Regular" to Typeface.NORMAL,"Bold" to Typeface.BOLD,"Italic" to Typeface.ITALIC).forEach { (sample,style) -> card.addView(StudioDesign.label(this,sample,18f).apply { typeface=Typeface.create(family,style);gravity=Gravity.CENTER },LinearLayout.LayoutParams(-1,dp(26))) }
            card.addView(StudioDesign.label(this,name,12f,bold=true).apply { gravity=Gravity.CENTER;maxLines=2 },LinearLayout.LayoutParams(-1,dp(38)))
            row.addView(card,LinearLayout.LayoutParams(dp(144),dp(148)).apply { marginEnd=dp(10) })
        };highlight();panel.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false;addView(row) })
    }
    private fun canvasOptions() {
        showPanel("Canvas")
        panel.addView(StudioDesign.label(this,"Aspect ratio",14f,bold=true).apply { setPadding(0,dp(8),0,dp(8)) })
        val row=LinearLayout(this)
        listOf("Fit","1:1","4:5","3:4","2:3","9:16","16:9","3:2","4:3").forEach { ratio ->
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; contentDescription="Canvas ratio $ratio"; background=StudioDesign.shape(this@PhotoEditorActivity,StudioDesign.raised(this@PhotoEditorActivity),12,state.ratio==ratio);selectStudioCard(this,state.ratio==ratio); setOnClickListener { change(state.copy(ratio=ratio)); canvasOptions() } }
            card.addView(PhotoRatioPreview(this,ratio),LinearLayout.LayoutParams(dp(72),dp(52)))
            card.addView(StudioDesign.label(this,ratio,12f).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(-1,dp(28)))
            row.addView(card,LinearLayout.LayoutParams(dp(80),dp(86)).apply { marginEnd=dp(8) })
        }; panel.addView(HorizontalScrollView(this).apply { addView(row) })
        panel.addView(StudioDesign.label(this,"Image position",14f,bold=true).apply { setPadding(0,dp(14),0,dp(8)) })
        chipRow(listOf("Fit image","Fill image","Center image"),if(state.photoCanvasFit=="Fill") "Fill image" else "Fit image") { action -> when(action) {
            "Center image" -> edit { it.copy(photoPanX=0f,photoPanY=0f,zoom=1f) }
            else -> change(state.copy(photoCanvasFit=if(action=="Fill image") "Fill" else "Fit"))
        } }
        panel.addView(StudioDesign.label(this,"Canvas background",14f,bold=true).apply { setPadding(0,dp(14),0,dp(8)) })
        chipRow(listOf("Solid color","Gradient","Blur background"),when(state.photoCanvasStyle) { "Gradient" -> "Gradient";"Blur" -> "Blur background";else -> "Solid color" }) { action -> when(action) {
            "Solid color" -> colors { change(state.copy(background=it,photoCanvasStyle="Solid")) }
            "Blur background" -> change(state.copy(photoCanvasStyle="Blur"))
            else -> gradientPanel()
        } }
        inlineSlider("Image zoom",50,400,(state.clips[state.active].zoom*100).toInt()) { value -> edit { it.copy(zoom=value/100f) } }
    }
    private fun gradientPanel() {
        showPanel("Gradient backgrounds")
        val row=LinearLayout(this)
        val values=listOf("Dawn" to (0xFFFFCB91.toInt() to 0xFFEA82AC.toInt()),"Lavender" to (0xFFB9D7FF.toInt() to 0xFFB8A1EA.toInt()),"Midnight" to (0xFF17223B.toInt() to 0xFF536A96.toInt()),"Mint" to (0xFFB8E6D4.toInt() to 0xFFF4E9BA.toInt()))
        fun highlight() { for(i in 0 until row.childCount) { val pair=values[i].second;selectStudioCard(row.getChildAt(i),state.photoCanvasStyle=="Gradient" && state.background==pair.first && state.photoCanvasColorEnd==pair.second) } }
        values.forEach { (name,pair) ->
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(6),dp(6),dp(6),dp(6));background=StudioDesign.shape(this@PhotoEditorActivity,StudioDesign.surface(this@PhotoEditorActivity),12,true);contentDescription="Gradient $name";isFocusable=true;setOnClickListener { change(state.copy(background=pair.first,photoCanvasColorEnd=pair.second,photoCanvasStyle="Gradient"));highlight() } }
            card.addView(View(this).apply { tag="media";background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,intArrayOf(pair.first,pair.second)).apply { cornerRadius=dp(10).toFloat() } },LinearLayout.LayoutParams(dp(100),dp(76)))
            card.addView(StudioDesign.label(this,name,12f,bold=true).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(-1,dp(34)))
            row.addView(card,LinearLayout.LayoutParams(dp(114),dp(124)).apply { marginEnd=dp(10) })
        };highlight();panel.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false;addView(row) })
    }
    @Suppress("DEPRECATION") private fun pick(max: Int,replace: Boolean=false,replaceAll: Boolean=false) {
        startActivityForResult(Intent(this,MediaPickerActivity::class.java).putExtra("mode","photo").putExtra("locked_type",true).putExtra("max_items",max).putExtra("picker_title",if(replaceAll) "Choose $max photos for this collage" else if(replace) "Replace photo" else "Add collage photos"),if(replaceAll) 612 else if(replace) 611 else 610)
    }
    private fun removePhoto() {
        if(state.clips.isEmpty()) return
        AppDialog(this).setTitle("Remove selected photo?").setMessage("Remove this photo from the project? The original image stays on your device. You can undo this change.")
            .setNegativeButton("Cancel",null).setPositiveButton("Remove") { _,_ -> closeCrop();selectedText="";clearPanel();change(ClipEdits.delete(state));toolbar() }.show()
    }
    private fun photos() {
        choose("Photos",listOf("Add photos","Select photo","Replace selected","Remove selected")) { index -> when(index) {
            0 -> if(state.clips.size<9) pick(9-state.clips.size) else status.setText("A collage supports up to nine photos.")
            1 -> choose("Select photo",state.clips.mapIndexed { i,c -> "${i+1} · ${c.name}" }) { history.select(it); changed() }
            2 -> pick(1,true)
            else -> removePhoto()
        } }
    }
    private fun collage() {
        val before=state
        showPanel("Collage")
        val choosePhotos=button("Choose photos") { pick(PhotoLayouts.selected(this,state).cells.size,replaceAll=true) }
        (panel.getChildAt(0) as LinearLayout).addView(choosePhotos,LinearLayout.LayoutParams(-2,dp(48)))
        val required=StudioDesign.label(this,"",12f,secondary=true); panel.addView(required)
        fun requirements() {
            val count=PhotoLayouts.selected(this,state).cells.size
            choosePhotos.text="Choose $count ${if(count==1) "photo" else "photos"}"
            required.text="$count ${if(count==1) "photo" else "photos"} required · ${minOf(count,state.clips.size)} selected"+(if(state.clips.size<count) " · add ${count-state.clips.size} more" else "")
        }
        requirements()
        val layouts=PhotoLayouts.all(this); val row=LinearLayout(this)
        val generation=panelGeneration
        val cards=mutableListOf<Pair<PhotoLayout,LinearLayout>>()
        fun highlight() { cards.forEach { (layout,card) -> card.isSelected=state.photoLayout==layout.id; card.background=StudioDesign.shape(this,if(card.isSelected) StudioDesign.raised(this) else StudioDesign.surface(this),12,card.isSelected);selectStudioCard(card,card.isSelected) } }
        layouts.forEach { layout ->
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; contentDescription="${layout.name}, ${layout.cells.size} photos"; isFocusable=true }
            val thumb=PhotoCollageThumbnail(this,layout).apply { tag="media"; scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="Preview of ${layout.name}" }
            card.addView(thumb,LinearLayout.LayoutParams(dp(112),dp(100)))
            card.addView(StudioDesign.label(this,"${layout.name}\n${layout.cells.size} ${if(layout.cells.size==1) "photo" else "photos"} required",12f).apply { gravity=Gravity.CENTER; maxLines=2 },LinearLayout.LayoutParams(dp(120),dp(38)))
            cards.add(layout to card)
            card.setOnClickListener { change(state.copy(photoLayout=layout.id,ratio=if(layout.cells.size>1 && state.ratio=="Fit") "1:1" else state.ratio)); highlight(); requirements(); status.text="${layout.name} · tap a cell to select its photo, then drag or pinch. Add photos to fill empty cells." }
            row.addView(card,LinearLayout.LayoutParams(dp(124),dp(144)).apply { marginEnd=dp(8) })
            // Duplicate only in the sample when slots are missing; actual export uses real project photos.
            val sample=before.copy(photoLayout=layout.id,ratio=if(before.ratio=="Fit" && layout.cells.size>1) "1:1" else before.ratio,projectTexts=emptyList(),clips=List(layout.cells.size) { i -> before.clips[i%before.clips.size] })
            requestThumbnail(thumb,sample,generation)
        }
        highlight(); panel.addView(HorizontalScrollView(this).apply { addView(row) })
        panel.addView(button("Edit photo") { clearPanel() },LinearLayout.LayoutParams(-1,dp(48)))
        chipRow(listOf("Add photos","Spacing","Corners","Undo collage changes")) { action -> when(action) {
            "Add photos" -> if(state.clips.size<9) pick(9-state.clips.size)
            "Spacing" -> slider("Collage spacing",0,80,state.photoGap ?: 12) { change(state.copy(photoGap=it)) }
            "Corners" -> slider("Rounded corners",0,100,state.photoCorner ?: 12) { change(state.copy(photoCorner=it)) }
            else -> { change(before); clearPanel() }
        } }
        panel.addView(StudioDesign.label(this,"Tap + in an empty slot to add a photo. Tap Done to continue; use Undo to restore your earlier layout.",12f,secondary=true))
    }
    private fun requestThumbnail(target: ImageView,sample: EditorState,generation: Int) {
        thumbnailWorker.execute {
            if(generation!=panelGeneration || isDestroyed) return@execute
            val result=runCatching { thumbnailRenderer.render(sample,240) }
            main.post {
                val bitmap=result.getOrNull()
                if(generation!=panelGeneration || isDestroyed) bitmap?.recycle()
                else if(bitmap!=null) { panelBitmaps.add(bitmap); target.setImageBitmap(bitmap) }
                else target.contentDescription="${target.contentDescription}. Preview unavailable; tap to apply."
            }
        }
    }
    private fun openCrop() {
        closeCrop(); clearPanel(); panel.visibility=View.VISIBLE; panelHost.visibility=View.VISIBLE;panelConfirm.visibility=View.VISIBLE;panelConfirm.contentDescription="Confirm photo crop"
        val clip=state.clips[state.active]; val token=++cropRevision
        val view=CropEditorView(this,RectF((clip.cropLeft+1)/2,(1-clip.cropTop)/2,(clip.cropRight+1)/2,(1-clip.cropBottom)/2))
        crop=view; textHandles(); preview.addView(view,FrameLayout.LayoutParams(-1,-1))
        view.onGestureStart={ history.beginGroup() }; view.onGestureEnd={ history.endGroup(); saveDraft() }
        view.onCropChanged={ r -> edit { it.copy(cropLeft=r.left*2-1,cropRight=r.right*2-1,cropTop=1-r.top*2,cropBottom=1-r.bottom*2) } }
        val heading=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),dp(8),dp(4),dp(8)) }
        heading.addView(StudioDesign.label(this,"Crop photo",17f,bold=true),LinearLayout.LayoutParams(0,-2,1f))

        panel.addView(heading)
        val row=LinearLayout(this)
        listOf("Free","1:1","4:5","16:9","Reset").forEach { name -> row.addView(button(if(name=="Reset") "↺ Reset" else name) { for(i in 0 until row.childCount) selectStudioCard(row.getChildAt(i),row.getChildAt(i).tag==(if(name=="Reset") "Free" else name));edit { it.copy(cropRatio=if(name=="Reset") "Free" else name) };when(name) { "Reset" -> view.reset(); "Free" -> view.setRatio(0f); else -> { val parts=name.split(':'); view.setRatio(parts[0].toFloat()/parts[1].toFloat()) } } }.apply { tag=name;selectStudioCard(this,name==(clip.cropRatio ?: "Free")) },LinearLayout.LayoutParams(-2,dp(48)).apply { marginEnd=dp(8) }) }
        panel.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false;setPadding(dp(4),dp(4),dp(4),dp(8));addView(row) })
        worker.execute {
            val result=runCatching { renderer.source(clip.uri,1536).copy(Bitmap.Config.ARGB_8888,false) }
            main.post { val bitmap=result.getOrNull(); if(isDestroyed || token!=cropRevision) bitmap?.recycle() else if(bitmap!=null) { view.image=bitmap;view.restoreRatio(state.clips[state.active].cropRatio) } else status.text="Unable to crop: ${result.exceptionOrNull()?.message}" }
        }
    }
    private fun closeCrop() { if(crop!=null && ::panel.isInitialized) clearPanel(); cropRevision++; crop?.let { preview.removeView(it); it.image?.recycle(); it.image=null }; crop=null; history.endGroup(); textHandles() }
    private fun installGestures() {
        var x=0f; var y=0f
        val pinch=ScaleGestureDetector(this,object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if(selectedText.isNotEmpty()) textChange { it.copy(size=(it.size*detector.scaleFactor).coerceIn(6f,200f)) }
                else edit { it.copy(zoom=(it.zoom*detector.scaleFactor).coerceIn(.5f,4f)) }
                return true
            }
        })
        image.setOnTouchListener { _,event ->
            val bitmap=shown ?: return@setOnTouchListener false
            val scale=minOf(image.width.toFloat()/bitmap.width,image.height.toFloat()/bitmap.height); val w=bitmap.width*scale; val h=bitmap.height*scale
            val nx=((event.x-(image.width-w)/2)/w).coerceIn(0f,1f); val ny=((event.y-(image.height-h)/2)/h).coerceIn(0f,1f)
            if(event.actionMasked==MotionEvent.ACTION_DOWN) {
                history.beginGroup(); x=event.x; y=event.y
                val hit=state.projectTexts.orEmpty().asReversed().firstOrNull { PhotoTextGeometry.bounds(bitmap.width,bitmap.height,it).contains(nx*bitmap.width,ny*bitmap.height) }
                selectedText=hit?.id ?: ""
                if(selectedText.isEmpty()) PhotoLayouts.selected(this,state).cells.indexOfFirst { it.contains(nx,ny) }.takeIf { it in state.clips.indices }?.let { history.select(it) }
                toolbar(); image.parent.requestDisallowInterceptTouchEvent(true)
            }
            pinch.onTouchEvent(event)
            when(event.actionMasked) {
                MotionEvent.ACTION_MOVE -> { if(!pinch.isInProgress && event.pointerCount==1) {
                    val dx=(event.x-x)/w; val dy=(event.y-y)/h; x=event.x; y=event.y
                    if(selectedText.isNotEmpty()) textChange { it.copy(x=(it.x+dx*2).coerceIn(-1f,1f),y=(it.y-dy*2).coerceIn(-1f,1f)) }
                    else edit { it.copy(photoPanX=((it.photoPanX ?: 0f)+dx*4).coerceIn(-1f,1f),photoPanY=((it.photoPanY ?: 0f)+dy*4).coerceIn(-1f,1f)) }
                } }
                MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> { history.endGroup(); image.parent.requestDisallowInterceptTouchEvent(false); saveDraft(); image.performClick() }
                MotionEvent.ACTION_POINTER_UP -> { val index=if(event.actionIndex==0) 1 else 0; x=event.getX(index); y=event.getY(index) }
            }
            return@setOnTouchListener true
        }
    }
    @Suppress("DEPRECATION") private fun exportOptions() {
        if(exporting || state.clips.isEmpty()) return
        closeCrop(); choose("Export image",listOf("PNG · 2048 px","JPEG · 2048 px")) { which ->
            if(!saveDraft()) return@choose; pending=state; pendingFormat=if(which==0) "PNG" else "JPEG"
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type=if(which==0) "image/png" else "image/jpeg"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE,"Lumen_Photo_${System.currentTimeMillis()}.${if(which==0) "png" else "jpg"}") },620)
        }
    }
    @Deprecated("Platform activity result") override fun onActivityResult(request: Int,result: Int,data: Intent?) {
        super.onActivityResult(request,result,data)
        if(result!=RESULT_OK) { if(request==620) pending=null; return }
        if(request==610 || request==611 || request==612) {
            val uris=data?.getStringArrayListExtra("uris").orEmpty(); val names=data?.getStringArrayListExtra("names").orEmpty()
            if(uris.isEmpty()) return
            val photos=uris.mapIndexed { i,uri -> EditorClip(uri,names.getOrNull(i) ?: "Photo",false) }
            if(request==612) { change(state.copy(clips=photos,active=0)); collage() }
            else if(request==611) edit { photos.first().copy(id=it.id) }
            else { val clips=(state.clips+photos).take(9); val layout=if(PhotoLayouts.selected(this,state).cells.size==1) PhotoLayouts.all(this).firstOrNull { it.cells.size==clips.size }?.id ?: "grid9" else state.photoLayout; change(state.copy(clips=clips,photoLayout=layout)) }
        }
        if(request==610) collage()
        if(request==620) {
            val uri=data?.data ?: return; val snapshot=pending ?: state; pending=null; val format=pendingFormat
            exporting=true; export.isEnabled=false; status.text="Exporting image…"
            worker.execute {
                val result=runCatching { val bitmap=renderer.render(snapshot,2048); try { (contentResolver.openOutputStream(uri,"w") ?: error("Unable to write the selected destination.")).use { require(bitmap.compress(if(format=="PNG") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,if(format=="PNG") 100 else 95,it)) }; true } finally { bitmap.recycle() } }
                main.post { exporting=false; if(!isDestroyed) { export.isEnabled=true; status.text=if(result.isSuccess) "Image exported successfully." else "Image export failed: ${result.exceptionOrNull()?.message}" } }
            }
        }
    }
    private fun leave() { if(exporting) { status.text="Wait for image export to finish."; return }; closeCrop(); saveDraft(); finish() }
    override fun handleBack() { if(crop!=null) { closeCrop(); clearPanel() } else if(selectedText.isNotEmpty()) { selectedText=""; toolbar() } else if(panel.visibility==View.VISIBLE) clearPanel() else leave() }
    override fun onResume() { super.onResume(); if(::history.isInitialized && epoch!=EditingStorage.epoch(this)) finish() }
    override fun onPause() { if(::history.isInitialized) saveDraft(); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) { if(::history.isInitialized) { out.putLong("epoch",epoch); out.putString("id",projectId); out.putString("name",projectName); out.putString("text",selectedText); out.putSerializable("state",state); out.putSerializable("undo",ArrayList(history.undo)); out.putSerializable("redo",ArrayList(history.redo)); out.putSerializable("pending",pending); out.putString("format",pendingFormat) }; super.onSaveInstanceState(out) }
    override fun onDestroy() { panelGeneration++; panelBitmaps.forEach { it.recycle() }; panelBitmaps.clear(); revision++; main.removeCallbacksAndMessages(null); cropRevision++; crop?.image?.recycle(); if(::image.isInitialized) image.setImageDrawable(null); shown?.recycle(); if(::renderer.isInitialized) worker.execute { renderer.close() }; worker.shutdown(); if(::thumbnailRenderer.isInitialized) thumbnailWorker.execute { thumbnailRenderer.close() }; thumbnailWorker.shutdown(); super.onDestroy() }
}
