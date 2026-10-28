package com.lumen.studio

import android.app.Dialog
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import android.graphics.drawable.GradientDrawable
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

@UnstableApi
class EditorActivity : BaseActivity() {
    var toolsDialog: Dialog? = null; private set
    private val green get()=StudioDesign.accent(this)
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var history: EditHistory
    private lateinit var root: LinearLayout
    private lateinit var historyBar: LinearLayout
    private lateinit var preview: FrameLayout
    private lateinit var previewColumn: LinearLayout
    private lateinit var editingColumn: LinearLayout
    private val landscape get()=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
    private lateinit var stillPreview: ImageView
    private var stillRevision=0
    private var stillRefresh: Runnable?=null
    private lateinit var sourceMute: SourceMuteView
    private lateinit var surface: EditorPreviewView
    lateinit var toolPanel: LinearLayout; private set
    private lateinit var timelineLabels: LinearLayout
    private lateinit var musicMute: ImageButton
    private lateinit var fullscreenControl: ImageButton
    private lateinit var timelineScroll: ScrollView
    private lateinit var timelineFrame: FrameLayout
    private lateinit var timelineHorizontal: HorizontalScrollView
    private lateinit var toolbar: LinearLayout
    private enum class Selection { NONE, VIDEO, AUDIO, TEXT, STICKER, VISUAL }
    private var selection=Selection.NONE
    private var selectedId=""
    private var selectionGlobal=false
    private var editSubmenu=false
    var timelineZoom=48f; private set
    private var timelineSync=false
    private var timelineTouching=false
    private var lastTimelineScrub=0L
    private var lastTimelineWidth=0
    private var cropView: CropEditorView?=null
    private val pxPerMs get()=timelineZoom*resources.displayMetrics.density/1000f
    private val panelStack=java.util.ArrayDeque<ToolPage>()
    private var currentPanel: ToolPage?=null
    var previewRendered=false; private set
    private var retry: Button?=null
    private data class ToolPage(val title: String,val body: View,var onExit: (() -> Unit)?=null)
    private lateinit var timeline: LinearLayout
    private lateinit var status: TextView
    private lateinit var projectTitle: TextView
    private lateinit var time: TextView
    private lateinit var seek: SeekBar
    private lateinit var play: Button
    private lateinit var undo: Button
    private lateinit var redo: Button
    private lateinit var export: Button
    private var player: Player?=null
    private lateinit var playbackCache: RenderedPlayback
    private var boundPlayer: Player?=null
    private var boundSurface: Surface?=null
    private var boundWidth=0
    private var boundHeight=0
    private var plan: TimelinePlan?=null
    private var projectId=""
    private var projectName=""
    private var editingEpoch=0L
    private var fullscreen=false
    private var generation=0
    private var savedPosition=0L
    private var playAfterRefresh=false
    private var preparing=false
    private var resumePreview=false
    private var currentTab=""
    private var exportAfterReward=false
    private var exportResolution=720
    private var pendingExport: EditorState?=null
    private var pendingImage: Bitmap?=null
    private var selectedLayer=""
    private var lastExportMessage=""
    private val state get()=history.state
    private val clip get()=state.clips[state.active]
    private fun dp(n: Int)=Ui.dp(this,n)
    private fun shape(color: Int)=GradientDrawable().apply { setColor(color); cornerRadius=dp(12).toFloat() }
    private fun label(value: String,size: Float=12f)=AppText(this).apply { text=value; textSize=size; setTextColor(Color.WHITE); setPadding(dp(8),dp(4),dp(8),dp(4)) }
    private fun button(value: String,action: () -> Unit)=StudioDesign.button(this,value,action=action).apply { textSize=12f; setPadding(dp(8),0,dp(8),0) }
    private fun message(value: String)=AppDialog(this).setMessage(value).setPositiveButton("OK",null).show()
    @Suppress("DEPRECATION") override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        editingEpoch=saved?.getLong("editing_epoch") ?: EditingStorage.epoch(this)
        if(editingEpoch!=EditingStorage.epoch(this)) { finish(); return }
        projectId=saved?.getString("project_id") ?: intent.getStringExtra("project_id") ?: UUID.randomUUID().toString()
        val stored=ProjectStore.load(this,projectId)
        val uris=intent.getStringArrayListExtra("uris") ?: arrayListOf()
        val names=intent.getStringArrayListExtra("names") ?: arrayListOf()
        val initial=(saved?.getSerializable("state") as? EditorState ?: stored?.state ?: EditorState(uris.mapIndexed { index,uri ->
            EditorClip(uri,names.getOrNull(index) ?: "Media ${index+1}",TimelineEngine.isVideo(this,uri))
        })).normalized()
        history=EditHistory(initial)
        (saved?.getSerializable("undo") as? ArrayList<*>)?.filterIsInstance<EditorState>()?.let { history.undo.addAll(it) }
        (saved?.getSerializable("redo") as? ArrayList<*>)?.filterIsInstance<EditorState>()?.let { history.redo.addAll(it) }
        projectName=saved?.getString("project_name") ?: stored?.name ?: initial.clips.firstOrNull()?.name?.substringBeforeLast('.') ?: "New Project"
        savedPosition=saved?.getLong("position",0) ?: 0; fullscreen=saved?.getBoolean("fullscreen",false) ?: false
        exportResolution=saved?.getInt("export_resolution",720) ?: 720
        pendingExport=saved?.getSerializable("pending_export") as? EditorState; selectedLayer=saved?.getString("selected_layer") ?: ""
        selection=runCatching { Selection.valueOf(saved?.getString("selection") ?: "NONE") }.getOrDefault(Selection.NONE)
        selectedId=saved?.getString("selected_id") ?: ""; selectionGlobal=saved?.getBoolean("selection_global") ?: false; editSubmenu=saved?.getBoolean("edit_submenu") ?: false; timelineZoom=saved?.getFloat("timeline_zoom",48f) ?: 48f
        playbackCache=RenderedPlayback(applicationContext)
        buildScreen(); refreshPreview(); saveDraft(); handler.post(tick)
    }
    private fun buildScreen() {
        pause(); player?.release(); player=null; currentPanel=null; panelStack.clear(); currentTab=""
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(12,14,18)) }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(8),0,dp(8),0) }
        header.addView(button("←") { navigateBack() }.apply { contentDescription="Back" },LinearLayout.LayoutParams(dp(44),dp(44)))
        projectTitle=TextView(this).apply { tag="media"; text=projectName; textSize=12f; setTextColor(Ui.foreground(this@EditorActivity,Color.WHITE)); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END; gravity=Gravity.CENTER_VERTICAL; contentDescription=Ui.t(this@EditorActivity,"Project name"); if(!fullscreen) setOnClickListener { projectPanel() } }
        header.addView(projectTitle,LinearLayout.LayoutParams(0,dp(44),1f))
        undo=button("↶") { pause(); if(history.undo()) changed() }.apply { contentDescription="Undo"; background=null; textSize=20f }
        redo=button("↷") { pause(); if(history.redo()) changed() }.apply { contentDescription="Redo"; background=null; textSize=20f }
        export=button("EXPORT") { exportMenu() }.apply { tag="primary-action"; setTextColor(StudioDesign.primaryText(this@EditorActivity)); background=StudioDesign.shape(this@EditorActivity,StudioDesign.primary(this@EditorActivity),12); textSize=11f }
        if(!fullscreen) header.addView(export,LinearLayout.LayoutParams(dp(64),dp(44)))
        root.addView(header,LinearLayout.LayoutParams(-1,dp(48)))
        val statusRow=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        status=label("",10f).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END }
        statusRow.addView(status,LinearLayout.LayoutParams(0,-2,1f))
        retry=button("Retry preview") { refreshPreview() }.apply { visibility=View.GONE }
        statusRow.addView(retry,LinearLayout.LayoutParams(-2,dp(24))); root.addView(statusRow,LinearLayout.LayoutParams(-1,dp(24)))
        val workspace=LinearLayout(this).apply { orientation=if(landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        previewColumn=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        editingColumn=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        workspace.addView(previewColumn,if(landscape) LinearLayout.LayoutParams(0,-1,1f) else LinearLayout.LayoutParams(-1,0,1f))
        if(!fullscreen) workspace.addView(editingColumn,if(landscape) LinearLayout.LayoutParams(0,-1,1f) else LinearLayout.LayoutParams(-1,-2))
        root.addView(workspace,LinearLayout.LayoutParams(-1,0,1f))
        preview=FrameLayout(this).apply { tag="media"; setBackgroundColor(Color.BLACK); layoutDirection=View.LAYOUT_DIRECTION_LTR; clipChildren=true }
        surface=EditorPreviewView(this) { view -> if(::surface.isInitialized && surface===view) bindPreviewSurface() }
        preview.addView(surface,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
        stillPreview=ImageView(this).apply { tag="media"; contentDescription="Processed preview"; scaleType=ImageView.ScaleType.FIT_CENTER; visibility=View.GONE; isClickable=false; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        preview.addView(stillPreview,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
        previewColumn.addView(preview,LinearLayout.LayoutParams(-1,0,1f))
        preview.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> fitPreview() }
        installLayerDragging()
        installLayerControls()
        sourceMute=SourceMuteView(this).apply { setOnClickListener { toggleSourceMute() } }

        val controls=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(8),0,dp(8),0) }

        play=button("▶") { if(!preparing && state.clips.isNotEmpty()) { if(player==null) { playAfterRefresh=true; refreshPreview() } else player?.let { if(it.isPlaying) it.pause() else { lastTimelineScrub=0; if(it.playbackState==Player.STATE_ENDED) it.seekTo(0); it.play() } } } }.apply { background=null; setTextColor(green); textSize=22f; contentDescription="Play or pause" }
        controls.addView(play,LinearLayout.LayoutParams(dp(44),dp(44))); time=label("00:00 / 00:00",10f).apply { maxLines=1 }; controls.addView(time,LinearLayout.LayoutParams(dp(102),-2))
        seek=SeekBar(this).apply {
            progressTintList=android.content.res.ColorStateList.valueOf(green); thumbTintList=android.content.res.ColorStateList.valueOf(green)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?,value: Int,user: Boolean) { if(user) { savedPosition=value.toLong(); player?.seekTo(savedPosition); selectAt(savedPosition); if(player==null) renderPausedFrame() } }
                override fun onStartTrackingTouch(bar: SeekBar?) { pause() }; override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        musicMute=studioIcon(this,R.drawable.ph_music_notes,"Mute soundtrack") { commit(state.copy(musicMuted=state.musicMuted!=true)) }

        controls.addView(seek,LinearLayout.LayoutParams(0,dp(44),1f)); previewColumn.addView(controls,LinearLayout.LayoutParams(-1,dp(44)))
        historyBar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; contentDescription="Timeline controls" }
        historyBar.addView(undo,LinearLayout.LayoutParams(dp(48),dp(44)))
        historyBar.addView(redo,LinearLayout.LayoutParams(dp(48),dp(44)))
        historyBar.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
        fullscreenControl=studioIcon(this,if(fullscreen) R.drawable.ph_arrows_in else R.drawable.ph_arrows_out,if(fullscreen) "Exit fullscreen preview" else "Enter fullscreen preview") { toggleFullscreen() }.apply { tag="media"; imageTintList=android.content.res.ColorStateList.valueOf(Color.WHITE); background=shape(Color.argb(200,15,18,23)) }
        preview.addView(fullscreenControl,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM or Gravity.RIGHT).apply { rightMargin=dp(8); bottomMargin=dp(8) })

        if(fullscreen) previewColumn.addView(historyBar,LinearLayout.LayoutParams(-1,dp(44)))
        timeline=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; layoutDirection=View.LAYOUT_DIRECTION_LTR }
        timelineHorizontal=HorizontalScrollView(this).apply { tag="timeline-horizontal"; layoutDirection=View.LAYOUT_DIRECTION_LTR; isHorizontalScrollBarEnabled=false; addView(timeline) }
        timelineScroll=ScrollView(this).apply { addView(timelineHorizontal); isFillViewport=false }
        timelineLabels=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(StudioDesign.surface(this@EditorActivity)); contentDescription="Timeline track types" }
        timelineFrame=FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(16,19,24)); addView(timelineScroll,FrameLayout.LayoutParams(-1,-1))
            addView(View(this@EditorActivity).apply { setBackgroundColor(green); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },FrameLayout.LayoutParams(dp(1),-1,Gravity.CENTER_HORIZONTAL))
            addView(label("▼",10f).apply { setTextColor(green); gravity=Gravity.CENTER; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },FrameLayout.LayoutParams(dp(20),dp(16),Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        }
        timelineFrame.addView(timelineLabels,FrameLayout.LayoutParams(dp(48),-2,Gravity.LEFT or Gravity.TOP))
        timelineScroll.setOnScrollChangeListener { _,_,y,_,_ -> timelineLabels.translationY=-y.toFloat() }
        timelineHorizontal.setOnScrollChangeListener { _,x,_,_,_ ->
            if(!timelineSync && !preparing && state.clips.isNotEmpty() && (timelineTouching || System.currentTimeMillis()-lastTimelineScrub<250)) {
                pause(); lastTimelineScrub=System.currentTimeMillis(); savedPosition=TimelineBounds.position(state,(x/pxPerMs).toLong()); player?.seekTo(savedPosition); selectAt(savedPosition); if(player==null) renderPausedFrame(); updateSplitEnabled()
            }
        }
        val scale=ScaleGestureDetector(this,object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean { pause(); timelineZoom=(timelineZoom*detector.scaleFactor).coerceIn(12f,240f); updateTimeline(); return true }
        })
        var downX=0f
        timelineHorizontal.setOnTouchListener { _,event ->
            scale.onTouchEvent(event)
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pause(); timelineTouching=true; lastTimelineScrub=System.currentTimeMillis(); downX=event.x }
                MotionEvent.ACTION_MOVE -> timelineTouching=true
                MotionEvent.ACTION_UP -> { timelineTouching=false; if(kotlin.math.abs(event.x-downX)<dp(6)) deselect() }
                MotionEvent.ACTION_CANCEL -> timelineTouching=false
            }
            scale.isInProgress
        }
        timelineHorizontal.addOnLayoutChangeListener { _,left,_,right,_,_,_,_,_ -> if(right-left>0 && lastTimelineWidth!=right-left) { lastTimelineWidth=right-left; timelineHorizontal.post { if(!isDestroyed) updateTimeline() } } }
        toolPanel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=View.GONE; background=shape(Color.rgb(24,27,32)) }
        toolbar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        if(!fullscreen) {
            editingColumn.addView(historyBar,LinearLayout.LayoutParams(-1,dp(44)))
            editingColumn.addView(timelineFrame,LinearLayout.LayoutParams(-1,dp(164)))
            editingColumn.addView(toolPanel,LinearLayout.LayoutParams(-1,dp(168)))
            editingColumn.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(toolbar) },LinearLayout.LayoutParams(-1,dp(60)))
        }
        root.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> root.post { if(!isDestroyed) resizeWorkbench() } }
        setContentView(root); updateTimeline()
    }
    private fun resizeWorkbench() {
        if(fullscreen || !::toolPanel.isInitialized) return
        val available=(root.height-root.paddingTop-root.paddingBottom).coerceAtLeast(dp(120))
        val editing=toolPanel.visibility==View.VISIBLE
        val presets=editing && currentPanel?.title in listOf("Filters","Effects","Text","Caption","Font","Style","Color","Text animation","Stickers","Text background","Text size","Size (%)","Curve speed","Timeline curve speed","In animations","Out animations","Combo animations")
        timelineFrame.visibility=if(presets) View.GONE else View.VISIBLE
        historyBar.visibility=if(presets) View.GONE else View.VISIBLE
        val region=(available-dp(72)).coerceAtLeast(dp(72))
        // Reserve one workbench region: opening tools replaces timeline space, never preview space.
        val work=if(landscape) region else minOf(dp(320),maxOf(dp(260),(available*.48f).toInt()))
        val th=if(presets) 0 else if(editing) minOf(dp(72),work/4) else work-dp(104)
        val ph=(if(presets) work-dp(60) else work-dp(104)-th).coerceAtLeast(dp(44))
        if(timelineFrame.layoutParams.height!=th) timelineFrame.layoutParams=LinearLayout.LayoutParams(-1,th)
        if(toolPanel.layoutParams.height!=ph) toolPanel.layoutParams=LinearLayout.LayoutParams(-1,ph)
    }

    private var gestureActive=false
    private lateinit var selectionChrome: FrameLayout
    private lateinit var deleteLayerHandle: ImageButton
    private lateinit var rotateLayerHandle: ImageButton
    private var layerGestureView: LayerTransformView?=null
    private fun installLayerDragging() {
        val overlay=LayerTransformView(this)
        layerGestureView=overlay
        preview.addView(overlay,FrameLayout.LayoutParams(-1,-1))
        var id=""; var kind=""; var original=state; var text: TextLayer?=null; var image: ImageLayer?=null; var frame: VisualLayer?=null
        var lastX=0f; var lastY=0f; var moved=false; var factor=1f; var previousSpan=0f
        fun resize(ratio: Float) {
            image=image?.copy(scale=(image!!.scale*ratio).coerceIn(.02f,3f))
            text=text?.copy(size=(text!!.size*ratio).coerceIn(6f,200f))
            frame=frame?.copy(frameScale=((frame!!.frameScale ?: 1f)*ratio).coerceIn(.25f,1.5f))
            moved=true; overlay.text=text; overlay.image=image; overlay.frame=frame; overlay.invalidate()
        }
        fun update(x: Float,y: Float) {
            val dx=(x-lastX)*2/surface.width.coerceAtLeast(1); val dy=-(y-lastY)*2/surface.height.coerceAtLeast(1)
            lastX=x; lastY=y
            text=text?.copy(x=(text!!.x+dx).coerceIn(-1f,1f),y=(text!!.y+dy).coerceIn(-1f,1f))
            image=image?.copy(x=(image!!.x+dx).coerceIn(-1f,1f),y=(image!!.y+dy).coerceIn(-1f,1f))
            frame=frame?.copy(frameX=((frame!!.frameX ?: 0f)+dx).coerceIn(-1f,1f),frameY=((frame!!.frameY ?: 0f)+dy).coerceIn(-1f,1f))
            overlay.text=text; overlay.image=image; overlay.frame=frame; overlay.invalidate()
        }
        val scale=ScaleGestureDetector(this,object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next=(factor*detector.scaleFactor).coerceIn(.02f,8f); val ratio=next/factor; factor=next
                resize(ratio); return true
            }
        })
        surface.setOnTouchListener { view,event ->
            if(state.clips.isEmpty() || fullscreen || preparing) return@setOnTouchListener false
            fun focusX()=(0 until event.pointerCount).sumOf { event.getX(it).toDouble() }.toFloat()/event.pointerCount
            fun focusY()=(0 until event.pointerCount).sumOf { event.getY(it).toDouble() }.toFloat()/event.pointerCount
            if(event.actionMasked==MotionEvent.ACTION_DOWN) {
                val x=event.x/view.width*2-1; val y=1-event.y/view.height*2
                val position=player?.currentPosition ?: savedPosition
                text=(clip.texts.filter { selectedPosition()>=it.startMs && (it.endMs==0L || selectedPosition()<it.endMs) }+state.projectTexts.orEmpty().filter { position>=it.startMs && (it.endMs==0L || position<it.endMs) }).minByOrNull { (it.x-x)*(it.x-x)+(it.y-y)*(it.y-y) }
                image=(clip.overlays.filter { selectedPosition()>=it.startMs && (it.endMs==0L || selectedPosition()<it.endMs) }+state.projectOverlays.orEmpty().filter { position>=it.startMs && (it.endMs==0L || position<it.endMs) }).minByOrNull { (it.x-x)*(it.x-x)+(it.y-y)*(it.y-y) }
                val td=text?.let { (it.x-x)*(it.x-x)+(it.y-y)*(it.y-y) } ?: Float.MAX_VALUE
                val md=image?.let { (it.x-x)*(it.x-x)+(it.y-y)*(it.y-y) } ?: Float.MAX_VALUE
                frame=state.projectVisuals.orEmpty().lastOrNull { it.kind=="effect" && it.name=="Frame" && position>=it.startMs && position<it.endMs }
                val frameClip=state.clips.getOrNull(plan?.starts?.indexOfLast { it<=position } ?: state.active) ?: clip
                val box=frame?.let { FrameEffectRenderer.bounds(this,frameClip,it,surface.width,surface.height) }
                val near=box!=null && event.x>=box.left-dp(24) && event.x<=box.right+dp(24) && event.y>=box.top-dp(24) && event.y<=box.bottom+dp(24)
                val edge=box!=null && minOf(kotlin.math.abs(event.x-box.left),kotlin.math.abs(event.x-box.right),kotlin.math.abs(event.y-box.top),kotlin.math.abs(event.y-box.bottom))<=dp(24)
                val pickFrame=near && (edge || minOf(td,md)>.5f && selection==Selection.VISUAL && selectedId==frame?.id)
                if(minOf(td,md)>.5f && !pickFrame) return@setOnTouchListener false
                pause(); original=state; moved=false; factor=1f; previousSpan=0f
                if(pickFrame) { text=null; image=null; id=frame!!.id; kind="frame" } else if(td<=md) { frame=null; image=null; id=text!!.id; kind="text" } else { frame=null; text=null; id=image!!.id; kind="image" }
                selection=when(kind) { "text" -> Selection.TEXT; "frame" -> Selection.VISUAL; else -> Selection.STICKER }; selectedId=id
                selectionGlobal=if(kind=="frame") true else if(kind=="text") state.projectTexts.orEmpty().any { it.id==id } else state.projectOverlays.orEmpty().any { it.id==id }
                closePanel(); updateToolbar(); gestureActive=true
                overlay.layoutParams=FrameLayout.LayoutParams(surface.width,surface.height).apply { leftMargin=surface.left; topMargin=surface.top }
                overlay.setLayer(text,image); overlay.frame=frame; overlay.frameClip=frameClip; overlay.visibility=View.VISIBLE
                val background=original.copy(clips=original.clips.map { c -> c.copy(texts=c.texts.filter { it.id!=id },overlays=c.overlays.filter { it.id!=id }) },projectTexts=original.projectTexts.orEmpty().filter { it.id!=id },projectOverlays=original.projectOverlays.orEmpty().filter { it.id!=id },projectVisuals=original.projectVisuals.orEmpty().filter { it.id!=id })
                renderPausedFrame(background)
                lastX=event.x; lastY=event.y; view.parent.requestDisallowInterceptTouchEvent(true)
            }
            if(id.isEmpty()) return@setOnTouchListener false
            scale.onTouchEvent(event)
            when(event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if(event.pointerCount>=2) {
                        val span=kotlin.math.hypot(event.getX(1)-event.getX(0),event.getY(1)-event.getY(0))
                        // Keep small pinches responsive before the platform detector crosses its span threshold.
                        if(!scale.isInProgress && previousSpan>1f && span>1f) resize(span/previousSpan)
                        previousSpan=span
                    }
                    val x=focusX(); val y=focusY(); if(kotlin.math.abs(x-lastX)+kotlin.math.abs(y-lastY)>2) moved=true; update(x,y)
                }
                MotionEvent.ACTION_POINTER_DOWN -> { lastX=focusX(); lastY=focusY(); previousSpan=kotlin.math.hypot(event.getX(1)-event.getX(0),event.getY(1)-event.getY(0)) }
                MotionEvent.ACTION_POINTER_UP -> { val index=if(event.actionIndex==0) 1 else 0; lastX=event.getX(index); lastY=event.getY(index); previousSpan=0f }
                MotionEvent.ACTION_UP -> {
                    gestureActive=false; view.parent.requestDisallowInterceptTouchEvent(false)
                    val t=text; val i=image
                    val next=if(frame!=null) original.copy(projectVisuals=original.projectVisuals.orEmpty().map { if(it.id==id) frame!! else it }) else if(t!=null) original.copy(clips=original.clips.map { c -> c.copy(texts=c.texts.map { if(it.id==id) t else it }) },projectTexts=original.projectTexts.orEmpty().map { if(it.id==id) t else it }) else original.copy(clips=original.clips.map { c -> c.copy(overlays=c.overlays.map { if(it.id==id) i!! else it }) },projectOverlays=original.projectOverlays.orEmpty().map { if(it.id==id) i!! else it })
                    if(next!=state) commit(next) else renderPausedFrame()
                    id=""; view.performClick()
                    updateLayerControls()
                }
                MotionEvent.ACTION_CANCEL -> { gestureActive=false; id=""; overlay.visibility=View.GONE; renderPausedFrame(); view.parent.requestDisallowInterceptTouchEvent(false) }
            }
            true
        }
    }
    private fun selectionText()=if(selection==Selection.TEXT) (if(selectionGlobal) state.projectTexts.orEmpty() else state.clips.getOrNull(state.active)?.texts.orEmpty()).firstOrNull { it.id==selectedId } else null
    private fun selectionImage()=if(selection==Selection.STICKER) (if(selectionGlobal) state.projectOverlays.orEmpty() else state.clips.getOrNull(state.active)?.overlays.orEmpty()).firstOrNull { it.id==selectedId } else null
    private fun transformedLayer(snapshot: EditorState,t: TextLayer?,i: ImageLayer?): EditorState = snapshot.copy(
        clips=snapshot.clips.map { c -> c.copy(texts=c.texts.map { if(it.id==t?.id) t else it },overlays=c.overlays.map { if(it.id==i?.id) i else it }) },
        projectTexts=snapshot.projectTexts.orEmpty().map { if(it.id==t?.id) t else it },projectOverlays=snapshot.projectOverlays.orEmpty().map { if(it.id==i?.id) i else it })
    private fun installLayerControls() {
        selectionChrome=FrameLayout(this).apply { visibility=View.GONE; isClickable=false; clipChildren=false; clipToPadding=false }
        deleteLayerHandle=studioIcon(this,R.drawable.ph_trash,"Delete selected layer") {
            if(selection==Selection.TEXT) textAction("Delete") else if(selection==Selection.STICKER) stickerAction("Delete")
        }
        rotateLayerHandle=studioIcon(this,R.drawable.ph_arrows_out,"Rotate selected layer").apply {
            setImageResource(android.R.drawable.ic_menu_rotate); isClickable=true; isFocusable=true
            setOnClickListener {
                val t=selectionText(); val i=selectionImage()
                if(t!=null || i!=null) commit(transformedLayer(state,t?.copy(rotation=(t.rotation+15)%360),i?.copy(rotation=(i.rotation+15)%360)))
            }
        }
        selectionChrome.addView(deleteLayerHandle,FrameLayout.LayoutParams(dp(48),dp(48)))
        selectionChrome.addView(rotateLayerHandle,FrameLayout.LayoutParams(dp(48),dp(48)))
        preview.addView(selectionChrome,FrameLayout.LayoutParams(-1,-1))
        var snapshot=state; var text: TextLayer?=null; var image: ImageLayer?=null; var angle=0f; var delta=0f; var downX=0f; var downY=0f
        fun pointerAngle(event: MotionEvent): Float {
            val location=IntArray(2); surface.getLocationOnScreen(location)
            val x=text?.x ?: image?.x ?: 0f; val y=text?.y ?: image?.y ?: 0f
            val cx=location[0]+surface.width*(x+1)/2; val cy=location[1]+surface.height*(1-y)/2
            return Math.toDegrees(kotlin.math.atan2((event.rawY-cy).toDouble(),(event.rawX-cx).toDouble())).toFloat()
        }
        rotateLayerHandle.setOnTouchListener { view,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    text=selectionText(); image=selectionImage()
                    if(text==null && image==null) return@setOnTouchListener false
                    pause(); snapshot=state; angle=pointerAngle(event); delta=0f; downX=event.rawX; downY=event.rawY; gestureActive=true
                    val overlay=layerGestureView!!; overlay.layoutParams=FrameLayout.LayoutParams(surface.width,surface.height).apply { leftMargin=surface.left; topMargin=surface.top }
                    overlay.setLayer(text,image); overlay.visibility=View.VISIBLE
                    val id=text?.id ?: image!!.id
                    renderPausedFrame(snapshot.copy(clips=snapshot.clips.map { c -> c.copy(texts=c.texts.filter { it.id!=id },overlays=c.overlays.filter { it.id!=id }) },projectTexts=snapshot.projectTexts.orEmpty().filter { it.id!=id },projectOverlays=snapshot.projectOverlays.orEmpty().filter { it.id!=id }))
                    view.parent.requestDisallowInterceptTouchEvent(true); true
                }
                MotionEvent.ACTION_MOVE -> {
                    delta=((pointerAngle(event)-angle+540)%360)-180
                    layerGestureView?.apply { this.text=text?.copy(rotation=text!!.rotation+delta); this.image=image?.copy(rotation=image!!.rotation+delta); invalidate() }; true
                }
                MotionEvent.ACTION_UP -> {
                    gestureActive=false; view.parent.requestDisallowInterceptTouchEvent(false)
                    if(kotlin.math.hypot(event.rawX-downX,event.rawY-downY)<dp(5)) view.performClick()
                    else commit(transformedLayer(snapshot,text?.copy(rotation=text!!.rotation+delta),image?.copy(rotation=image!!.rotation+delta)))
                    layerGestureView?.visibility=View.GONE; renderPausedFrame(); updateLayerControls(); true
                }
                MotionEvent.ACTION_CANCEL -> { gestureActive=false; view.parent.requestDisallowInterceptTouchEvent(false); layerGestureView?.visibility=View.GONE; renderPausedFrame(); updateLayerControls(); true }
                else -> true
            }
        }
        preview.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> updateLayerControls() }
    }
    private fun updateLayerControls() {
        if(!::selectionChrome.isInitialized || !::surface.isInitialized) return
        val t=selectionText(); val i=selectionImage()
        val position=if(selectionGlobal) player?.currentPosition ?: savedPosition else selectedPosition()
        val start=t?.startMs ?: i?.startMs ?: 0; val end=t?.endMs ?: i?.endMs ?: 0
        if(fullscreen || player?.isPlaying==true || (t==null && i==null) || position<start || end>0 && position>=end) { selectionChrome.visibility=View.GONE; return }
        val box=LayerSelectionGeometry.bounds(surface.width,surface.height,t,i) ?: return
        selectionChrome.visibility=View.VISIBLE
        val left=box.left+surface.left; val right=box.right+surface.left; val top=box.top+surface.top
        fun place(handle: View,x: Float) { handle.translationX=x.coerceIn(0f,(preview.width-dp(48)).coerceAtLeast(0).toFloat()); handle.translationY=(top-dp(42)).coerceIn(0f,(preview.height-dp(48)).coerceAtLeast(0).toFloat()) }
        place(deleteLayerHandle,left-dp(12)); place(rotateLayerHandle,maxOf(right-dp(36),left+dp(40)))
    }
    private fun renderPausedFrame(snapshot: EditorState=state) {
        if(snapshot.clips.isEmpty()) return
        val revision=++stillRevision; val position=savedPosition; val owner=generation
        stillRefresh?.let { handler.removeCallbacks(it) }
        stillRefresh=Runnable {
            worker.execute {
                if(revision!=stillRevision || owner!=generation) return@execute
                val rendered=runCatching { StillPreviewRenderer.render(applicationContext,snapshot,position) }
                val image=rendered.getOrNull()
                runOnUiThread {
                    if(isDestroyed || owner!=generation || revision!=stillRevision) { image?.recycle(); return@runOnUiThread }
                    if(image==null) { stillPreview.visibility=View.GONE; status.text="Preview failed: ${rendered.exceptionOrNull()?.message ?: "Unable to render this frame"}"; retry?.visibility=View.VISIBLE; return@runOnUiThread }
                    if(image!=null) { if(!gestureActive) layerGestureView?.visibility=View.GONE; stillPreview.setImageBitmap(image); stillPreview.visibility=View.VISIBLE; previewRendered=true; fitPreview(); updateLayerControls() }
                }
            }
        }.also { handler.postDelayed(it,16) }
    }
    private fun toggleFullscreen() { pause(); fullscreen=!fullscreen; buildScreen(); refreshPreview() }
    private fun pause() { player?.let { savedPosition=it.currentPosition; it.pause() } }
    private fun selectAt(position: Long) {
        val index=plan?.starts?.indexOfLast { it <= position } ?: return
        if(selection==Selection.NONE && index >= 0 && index != state.active && index < state.clips.size) { history.select(index); updateTimeline() }
    }
    private fun bindPreviewSurface() {
        val currentPlayer=player ?: return
        val output=surface.outputSurface
        if(output==null) {
            if(boundPlayer===currentPlayer && boundSurface!=null) currentPlayer.clearVideoSurface()
            boundPlayer=null; boundSurface=null; return
        }
        val size=surface.layoutParams
        if(surface.width<=0 || surface.height<=0 || (size.width>0 && size.width!=surface.width) || (size.height>0 && size.height!=surface.height)) return
        if(boundPlayer===currentPlayer && boundSurface===output && boundWidth==surface.width && boundHeight==surface.height) return
        currentPlayer.setVideoSurface(output)
        boundPlayer=currentPlayer; boundSurface=output; boundWidth=surface.width; boundHeight=surface.height
    }
    private fun fitPreview() {
        val p=plan ?: return; if(preview.width <= 0 || preview.height <= 0) return
        val scale=minOf(preview.width.toFloat()/p.width,preview.height.toFloat()/p.height); val w=(p.width*scale).toInt(); val h=(p.height*scale).toInt()
        if(surface.width != w || surface.height != h) surface.layoutParams=FrameLayout.LayoutParams(w,h,Gravity.CENTER)
        if(::stillPreview.isInitialized && (stillPreview.layoutParams.width!=w || stillPreview.layoutParams.height!=h)) stillPreview.layoutParams=FrameLayout.LayoutParams(w,h,Gravity.CENTER)
    }
    private var applyingTimelineCurve=false
    private fun commit(next: EditorState,position: Long?=null) {
        if(next==state) return
        fun timing(c: EditorClip)=listOf(c.id,c.startMs,c.endMs,c.speed,c.speedCurve,c.speedPositions,c.imageDurationMs)
        val resolved=if(!applyingTimelineCurve && next.clips.map(::timing)!=state.clips.map(::timing)) next.copy(projectSpeedPositions=null,projectSpeedValues=null) else next
        pause();if(position!=null) savedPosition=position; history.change(resolved); changed()
    }
    private fun edit(action: (EditorClip) -> EditorClip) { if(state.clips.isNotEmpty()) commit(ClipEdits.update(state,action)) }
    private fun changed() { savedPosition=TimelineBounds.position(state,savedPosition); seek.max=TimelineBounds.duration(state).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(); validateSelection(); saveDraft(); updateTimeline(); updateToolbar(); refreshPreview() }
    private fun updateHistoryControls() {
        undo.isEnabled=history.undo.isNotEmpty(); redo.isEnabled=history.redo.isNotEmpty(); undo.alpha=if(undo.isEnabled) 1f else .35f; redo.alpha=if(redo.isEnabled) 1f else .35f
    }
    private fun updateSourceMute() {
        if(!::sourceMute.isInitialized) return
        val c=state.clips.getOrNull(state.active)
        sourceMute.visibility=if(c?.video==true) View.VISIBLE else View.INVISIBLE
        sourceMute.isEnabled=c?.hasAudio!=false && c!=null
        sourceMute.alpha=if(sourceMute.isEnabled) 1f else .4f
        sourceMute.muted=c?.muted==true
        if(::musicMute.isInitialized) {
            val muted=state.musicMuted==true
            musicMute.setImageResource(if(muted) R.drawable.ph_speaker_slash else R.drawable.ph_music_notes)
            musicMute.contentDescription=if(muted) "Unmute soundtrack" else "Mute soundtrack"
            musicMute.isSelected=muted
            musicMute.isEnabled=state.projectSounds.orEmpty().isNotEmpty() || state.clips.any { it.sounds.isNotEmpty() }
            musicMute.alpha=if(musicMute.isEnabled) 1f else .4f
        }
    }
    private fun toggleSourceMute() {
        val c=state.clips.getOrNull(state.active) ?: return
        if(!c.video || c.hasAudio==false) return
        val next=ClipEdits.update(state) { it.copy(muted=!it.muted) }
        if(player is androidx.media3.exoplayer.ExoPlayer && canPreviewDirect(next)) {
            history.change(next); player?.volume=if(next.clips[next.active].muted) 0f else c.audioVolume
            saveDraft(); updateHistoryControls(); updateTimeline(); return
        }
        val wasPlaying=player?.isPlaying==true
        commit(next); playAfterRefresh=wasPlaying
    }
    private fun canPreviewDirect(input: EditorState): Boolean {
        val c=input.clips.singleOrNull() ?: return false
        return c.video && input.ratio=="Fit" && input.projectSounds.orEmpty().isEmpty() && input.projectTexts.orEmpty().isEmpty() && input.projectOverlays.orEmpty().isEmpty() && input.projectVisuals.orEmpty().isEmpty() &&
            c.sounds.isEmpty() && c.texts.isEmpty() && c.overlays.isEmpty() && c.filter=="Original" && c.effect=="None" && c.visuals.orEmpty().isEmpty() && !c.reverse &&
            c.animations==null && c.motion.isNullOrBlank() && c.speed==1f && c.speedCurve.orEmpty().isEmpty() && c.rotation==0f && c.zoom==1f && !c.flip && !c.flipVertical && c.opacity==1f &&
            c.catalogAdjustments.orEmpty().values.all { it==0f } && c.brightness==0f && c.contrast==1f && c.saturation==1f && c.exposure==0f && c.temperature==0f && c.tint==0f &&
            c.cropLeft== -1f && c.cropRight==1f && c.cropBottom== -1f && c.cropTop==1f && c.audioFadeInMs==0L && c.audioFadeOutMs==0L && c.audioVolume in 0f..1f
    }
    private fun refreshPreview() {
        resumePreview=false; playbackCache.cancel(); val current=++generation; player?.release(); player=null; previewRendered=false; stillRevision++; retry?.visibility=View.GONE
        updateHistoryControls(); updateSourceMute()
        retry?.apply { text="Retry preview"; setOnClickListener { refreshPreview() } }
        if(state.clips.isEmpty()) { plan=null; play.isEnabled=false; export.isEnabled=false; status.text="Add media to start editing."; return }
        preparing=true; play.isEnabled=false; export.isEnabled=false; status.text="Preparing preview…"
        val snapshot=state
        worker.execute {
            if(current!=generation) return@execute
            try {
                val prepared=TimelineEngine.build(applicationContext,snapshot)
                val direct=canPreviewDirect(prepared.state)
                val frame=if(!direct) runCatching { StillPreviewRenderer.render(applicationContext,prepared.state,savedPosition) }.getOrNull() else null
                runOnUiThread {
                    if(isDestroyed || current != generation) { frame?.recycle(); return@runOnUiThread }
                    plan=prepared
                    history.adoptPrepared(prepared.state)
                    fitPreview()
                    if(frame!=null) { if(!gestureActive) layerGestureView?.visibility=View.GONE; stillPreview.setImageBitmap(frame); stillPreview.visibility=View.VISIBLE; previewRendered=true }
                    if(!direct && !playAfterRefresh && frame!=null) {
                        preparing=false; play.isEnabled=true; export.isEnabled=true; status.text=""; seek.max=prepared.duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(); saveDraft(); updateTimeline(); return@runOnUiThread
                    }
                    if(direct) startPreviewPlayer(prepared,current)
                    else {
                        status.text="Preparing playback…"
                        retry?.apply { text="Cancel"; visibility=View.VISIBLE; setOnClickListener { playAfterRefresh=false; refreshPreview() } }
                        playbackCache.prepare(prepared.state,
                            onProgress={ progress -> if(current==generation) status.text="${Ui.t(this,"Preparing playback")} $progress%" },
                            onReady={ file -> if(!isDestroyed && current==generation) { retry?.visibility=View.GONE; startPreviewPlayer(prepared,current,file) } },
                            onError={ error -> if(!isDestroyed && current==generation) { preparing=false; playAfterRefresh=false; play.isEnabled=true; export.isEnabled=true; status.text="Playback preparation failed: ${error.message}"; retry?.apply { text="Retry preview"; visibility=View.VISIBLE; setOnClickListener { playAfterRefresh=true; refreshPreview() } } } })
                    }
                    seek.max=prepared.duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(); saveDraft(); updateTimeline(); fitPreview()
                }
            } catch(error: Exception) { runOnUiThread { if(!isDestroyed && current == generation) { preparing=false; status.text="Unable to prepare media: ${error.message}" } } }
        }
    }
    private fun startPreviewPlayer(prepared: TimelinePlan,current: Int,rendered: File?=null) {
        var playbackFrame=false
        retry?.apply { text="Retry preview"; setOnClickListener { playAfterRefresh=true; refreshPreview() } }
        player=androidx.media3.exoplayer.ExoPlayer.Builder(this)
            .setRenderersFactory(androidx.media3.exoplayer.DefaultRenderersFactory(this).setEnableDecoderFallback(true)).build().apply {
                addListener(object: Player.Listener {
                    override fun onPlaybackStateChanged(value: Int) {
                        if(current!=generation) return
                        if(value==Player.STATE_READY) { preparing=false; play.isEnabled=true; export.isEnabled=true; if(playAfterRefresh) { playAfterRefresh=false; seekTo(savedPosition); play() }; status.text=if(previewRendered) "" else "Loading video preview…"; fitPreview() }
                    }
                    override fun onRenderedFirstFrame() { if(current==generation) { playbackFrame=true; previewRendered=true; stillPreview.visibility=View.GONE; status.text=""; retry?.visibility=View.GONE } }
                    override fun onPlayerError(error: PlaybackException) { if(current==generation) { preparing=false; playAfterRefresh=false; play.isEnabled=true; export.isEnabled=true; status.text="Playback failed (${error.errorCodeName}). Try another video format."; retry?.visibility=View.VISIBLE } }
                })
                if(rendered!=null) setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(rendered)))
                else {
                    val item=prepared.state.clips.single()
                    setMediaItem(androidx.media3.common.MediaItem.Builder().setUri(item.uri).setClippingConfiguration(androidx.media3.common.MediaItem.ClippingConfiguration.Builder().setStartPositionMs(item.startMs.toLong()).setEndPositionMs(if(item.endMs>0) item.endMs.toLong() else androidx.media3.common.C.TIME_END_OF_SOURCE).build()).build())
                    volume=if(item.muted) 0f else item.audioVolume
                }
            }
        bindPreviewSurface(); player?.prepare(); player?.seekTo(savedPosition.coerceIn(0,(prepared.duration-1).coerceAtLeast(0)))
        handler.postDelayed({ if(current==generation && !playbackFrame && !isDestroyed) { preparing=false; playAfterRefresh=false; player?.pause(); play.isEnabled=true; export.isEnabled=true; status.text="No video frame received. Tap Retry."; retry?.visibility=View.VISIBLE } },15000)
    }
    private fun deselect() { selection=Selection.NONE; selectedId=""; selectionGlobal=false; editSubmenu=false; closePanel(); updateTimeline(); updateToolbar() }
    private fun selectClip(index: Int) {
        pause()
        if(selection==Selection.VIDEO && selectedId==state.clips[index].id) { deselect(); return }
        closePanel(); selection=Selection.VIDEO; selectionGlobal=false; selectedId=state.clips[index].id; editSubmenu=false; history.select(index)
        val start=plan?.starts?.getOrNull(index) ?: 0; val end=start+ClipEdits.duration(clip)
        if(savedPosition<start || savedPosition>=end) { savedPosition=start; player?.seekTo(start) }
        updateTimeline(); updateToolbar(); saveDraft(); if(player==null) renderPausedFrame()
    }
    private fun selectLayer(index: Int,kind: Selection,id: String) {
        pause(); closePanel(); if(index>=0) history.select(index); selectionGlobal=index<0; selection=kind; selectedId=id; selectedLayer=id; editSubmenu=false; if(kind==Selection.VISUAL) { state.projectVisuals.orEmpty().firstOrNull { it.id==id }?.let { savedPosition=it.startMs; player?.seekTo(savedPosition); renderPausedFrame() } }; updateToolbar(); updateTimeline()
    }
    private fun validateSelection() {
        val valid=when(selection) { Selection.VISUAL -> state.projectVisuals.orEmpty().any { it.id==selectedId }; Selection.NONE -> true; Selection.VIDEO -> state.clips.any { it.id==selectedId }; Selection.AUDIO -> (if(selectionGlobal) state.projectSounds.orEmpty() else state.clips.getOrNull(state.active)?.sounds.orEmpty()).any { it.id==selectedId }; Selection.TEXT -> (if(selectionGlobal) state.projectTexts.orEmpty() else state.clips.getOrNull(state.active)?.texts.orEmpty()).any { it.id==selectedId }; Selection.STICKER -> (if(selectionGlobal) state.projectOverlays.orEmpty() else state.clips.getOrNull(state.active)?.overlays.orEmpty()).any { it.id==selectedId } }
        if(!valid) { selection=Selection.NONE; selectedId=""; selectionGlobal=false; editSubmenu=false; closePanel() }
    }
    private fun canSplit(): Boolean {
        if(preparing || state.clips.isEmpty()) return false
        val position=player?.currentPosition ?: savedPosition; val start=plan?.starts?.getOrNull(state.active) ?: 0
        return if(selection==Selection.VIDEO || selection==Selection.NONE) clip.video && position>start && position<start+ClipEdits.duration(clip)
        else if(selection==Selection.AUDIO) selectedSound()?.let { val p=if(selectionGlobal) position else position-start; p>it.startMs && p<it.startMs+(plan?.soundLengths?.get(soundKey(it)) ?: 0) }==true else false
    }
    private fun updateSplitEnabled() {
        if(!::toolbar.isInitialized) return
        for(i in 0 until toolbar.childCount) { val item=toolbar.getChildAt(i) as? EditorToolItem ?: continue; if(item.title=="Split") { item.isEnabled=canSplit(); item.alpha=if(item.isEnabled) 1f else .35f } }
    }
    private fun updateToolbar() {
        if(!::toolbar.isInitialized) return
        updateLayerControls()
        val names=when(selection) {
            Selection.NONE -> if(editSubmenu) listOf("Back","Text","Stickers","Effects","Ratio","Adjust","Rotate","Flip","Replace","Duplicate","Opacity","Add Media") else listOf("Edit","Sound","Animation","Speed","Filters","Split","Delete","Crop")
            Selection.VIDEO -> if(editSubmenu) listOf("Back","Text","Stickers","Effects","Ratio","Adjust","Rotate","Flip","Replace","Duplicate","Opacity","Add Media") else listOf("Edit","Sound","Animation","Speed","Filters","Split","Delete","Crop")
            Selection.AUDIO -> listOf("Back","Split","Volume","Fade","Speed","Replace","None","Delete")
            Selection.TEXT -> listOf("Back","Edit Text","Font","Style","Text size","Color","Alignment","Spacing","Text background","Text outline","Text shadow","Text opacity","Text rotation","Position","Animation","Duration","Duplicate","None","Delete")
            Selection.STICKER -> listOf("Back","Replace","Size","Opacity","Duration","Flip","None","Delete")
            Selection.VISUAL -> if(state.projectVisuals.orEmpty().any { it.id==selectedId && it.name=="Frame" && it.kind=="effect" }) listOf("Back","Resize","Position","Thickness","Frame color","Frame style","Duration","Replace","Delete") else listOf("Back","Replace","Duration","Add filter","Add effect","Delete")
        }
        if(toolbar.childCount==names.size && names.indices.all { (toolbar.getChildAt(it) as? EditorToolItem)?.title==names[it] }) { updateSplitEnabled(); if(toolbar.width==0) toolbar.post { toolbar.requestLayout() }; return }
        toolbar.removeAllViews()
        names.forEach { name ->
            val available=name!="Reverse"
            val item=EditorToolItem(this,name,available) { toolbarAction(name) }
            val measurement=Paint().apply { textSize=10f*resources.displayMetrics.scaledDensity }; val width=measurement.measureText(Ui.t(this,if(available) name else "$name · Unavailable")).toInt().plus(dp(20)).coerceIn(dp(64),dp(132))
            toolbar.addView(item,LinearLayout.LayoutParams(width,dp(56)))
        }
        updateSplitEnabled()
    }
    private fun toolbarAction(name: String) {
        if(name=="Back") { if(currentPanel!=null) panelBack() else if(editSubmenu) { editSubmenu=false; updateToolbar() } else deselect(); return }
        if(state.clips.isEmpty()) { addMedia(); return }; if(currentPanel!=null) closePanel()
        when(selection) {
            Selection.NONE -> when(name) { "Edit" -> tools(); "Split" -> { selection=Selection.VIDEO; if(canSplit()) split() }; "Delete" -> commit(ClipEdits.delete(state)); "Animation" -> clipAnimationPanel(); "Speed","Crop","Adjust","Rotate","Flip","Replace","Opacity" -> tool(name); "Duplicate" -> commit(ClipEdits.duplicate(state)); "Audio","Sound" -> openSounds(); "Text" -> openTab("text"); "Stickers" -> stickerPicker(); "Effects" -> openTab("effects"); "Filters" -> filterPanel(); "Ratio" -> tool("Ratio"); "Add Media" -> addMedia() }
            Selection.VIDEO -> when(name) { "Animation" -> clipAnimationPanel(); "Edit" -> tools(); "Audio","Sound" -> openSounds(); "Text" -> openTab("text"); "Stickers" -> stickerPicker(); "Effects" -> effectPanel(); "Add Media" -> addMedia(); "Split" -> if(canSplit()) split(); "Filters" -> filterPanel(); "Adjust" -> tool("Adjust"); "Delete" -> commit(ClipEdits.delete(state)); "Duplicate" -> { val next=ClipEdits.duplicate(state); selectedId=next.clips[next.active].id; commit(next) }; else -> tool(name) }
            Selection.AUDIO -> audioAction(name)
            Selection.TEXT -> textAction(name)
            Selection.STICKER -> stickerAction(name)
            Selection.VISUAL -> visualAction(name)
        }
    }
    private fun syncTimeline(position: Long) {
        if(!::timelineHorizontal.isInitialized || timelineTouching || System.currentTimeMillis()-lastTimelineScrub<250) return
        timelineSync=true; timelineHorizontal.scrollTo((position*pxPerMs).toInt(),0); timelineSync=false
    }
    private fun updateTimeline() {
        if(!::timeline.isInitialized) return
        timelineSync=true; timeline.removeAllViews(); timelineLabels.removeAllViews()
        timelineLabels.addView(View(this),LinearLayout.LayoutParams(dp(48),dp(18)))
        fun laneIcon(resource: Int,label: String,height: Int) {
            val icon=if(resource==R.drawable.ph_music_notes && label=="Music track") {
                if(musicMute.parent==null) musicMute else studioIcon(this,if(state.musicMuted==true) R.drawable.ph_speaker_slash else R.drawable.ph_music_notes,if(state.musicMuted==true) "Unmute soundtrack" else "Mute soundtrack") { commit(state.copy(musicMuted=state.musicMuted!=true)) }
            } else studioIcon(this,resource,label,if(resource==R.drawable.ph_music_notes) ({ openSounds() }) else null)
            icon.setPadding(dp(12),dp(12),dp(12),dp(12));icon.background=null
            timelineLabels.addView(icon,LinearLayout.LayoutParams(dp(48),dp(height)))
        }
        (sourceMute.parent as? ViewGroup)?.removeView(sourceMute)
        timelineLabels.addView(sourceMute,LinearLayout.LayoutParams(dp(48),dp(66)))
        val viewport=timelineHorizontal.width.takeIf { it>0 } ?: (resources.displayMetrics.widthPixels/if(landscape) 2 else 1)
        val lead=viewport/2; val total=TimelineBounds.duration(state); val content=maxOf(1,(total*pxPerMs).toInt())
        val whole=content+lead*2
        timeline.layoutParams=FrameLayout.LayoutParams(whole,-2)
        timeline.addView(object : View(this) {
            private val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Ui.foreground(this@EditorActivity,Color.rgb(144,155,170)); textSize=dp(8).toFloat() }
            override fun onDraw(canvas: Canvas) {
                val step=if(timelineZoom<24) 5 else if(timelineZoom>100) 1 else 2
                val bounds=canvas.clipBounds
                val first=maxOf(0,((bounds.left-lead)/pxPerMs/1000/step).toInt()*step)
                val last=minOf((total/1000).toInt(),((bounds.right-lead)/pxPerMs/1000).toInt()+step)
                for(second in first..last step step) { val x=lead+second*1000*pxPerMs; canvas.drawLine(x,height-4f,x,height.toFloat(),ink); canvas.drawText("%02d:%02d".format(second/60,second%60),x+2,dp(10).toFloat(),ink) }
            }
        },LinearLayout.LayoutParams(whole,dp(18)))
        val clips=LinearLayout(this); clips.addView(View(this),LinearLayout.LayoutParams(lead,dp(64)))
        var start=0L
        state.clips.forEachIndexed { index,c ->
            val width=maxOf(1,(ClipEdits.duration(c)*pxPerMs).toInt())
            val card=FrameLayout(this).apply { background=shape(Color.rgb(29,33,39)); clipToOutline=true; contentDescription="Clip ${index+1}: ${c.name}"; isFocusable=true }
            val thumbnail=RepeatingThumbnailView(this).apply { tag="media" }
            card.addView(thumbnail,FrameLayout.LayoutParams(-1,-1)); PreviewImages.load(this,c.uri,c.video,thumbnail,c.startMs.toLong())
            card.addView(TextView(this).apply { tag="media"; text="${index+1} · ${"%.1f".format(ClipEdits.duration(c)/1000f)}s"; textSize=10f; setPadding(dp(8),dp(4),dp(8),dp(4)); setTextColor(Color.WHITE); setBackgroundColor(Color.argb(175,0,0,0)); maxLines=1 },FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
            val active=selection==Selection.VIDEO && selectedId==c.id
            card.foreground=GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius=dp(6).toFloat(); setStroke(dp(if(active) 2 else 1),if(active) green else Color.rgb(55,61,70)) }
            card.setOnClickListener { selectClip(index) }; dragSource(card,"clip",c.id,"")
            card.setOnDragListener { _,event -> val payload=event.localState as? LayerDrag; when(event.action) { DragEvent.ACTION_DRAG_STARTED -> payload!=null; DragEvent.ACTION_DROP -> { if(payload!=null) { dropLayer(payload,index); true } else false }; else -> true } }
            if(active && c.video) listOf(true,false).forEach { left ->
                val handle=View(this).apply { setBackgroundColor(Color.WHITE); contentDescription=if(left) "Trim start handle" else "Trim end handle" }
                var down=0f
                handle.setOnTouchListener { view,event ->
                    when(event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { pause(); down=event.rawX; view.parent.requestDisallowInterceptTouchEvent(true); true }
                        MotionEvent.ACTION_MOVE -> { view.translationX=event.rawX-down; true }
                        MotionEvent.ACTION_UP -> {
                            view.parent.requestDisallowInterceptTouchEvent(false); view.translationX=0f
                            val outputDelta=((event.rawX-down)/pxPerMs).toLong()
                            val edge=if(left) 0L else ClipEdits.duration(c)
                            val newOutput=edge+outputDelta
                            val sourceEdge=if(newOutput<0) c.startMs+(newOutput*ClipEdits.speedAt(c,0.0)).toInt() else if(newOutput>ClipEdits.duration(c)) (if(c.endMs>0) c.endMs else c.sourceDurationMs.toInt())+((newOutput-ClipEdits.duration(c))*ClipEdits.speedAt(c,1.0)).toInt() else ClipEdits.sourcePosition(c,newOutput)
                            val delta=sourceEdge-(if(left) c.startMs else if(c.endMs>0) c.endMs else c.sourceDurationMs.toInt())
                            val end=if(c.endMs>0) c.endMs else c.sourceDurationMs.toInt()
                            val a=if(left) (c.startMs+delta).coerceIn(0,end-1) else c.startMs
                            val b=if(left) end else (end+delta).coerceIn(c.startMs+1,c.sourceDurationMs.toInt())
                            if(kotlin.math.abs(delta)>5) edit { ClipEdits.trim(it,a,b) }; true
                        }
                        MotionEvent.ACTION_CANCEL -> { view.translationX=0f; view.parent.requestDisallowInterceptTouchEvent(false); true }
                        else -> true
                    }
                }
                card.addView(handle,FrameLayout.LayoutParams(dp(16),-1,if(left) Gravity.LEFT else Gravity.RIGHT))
            }
            clips.addView(card,LinearLayout.LayoutParams(width,dp(64))); start+=ClipEdits.duration(c)
        }
        clips.addView(View(this),LinearLayout.LayoutParams(lead,dp(64))); timeline.addView(clips,LinearLayout.LayoutParams(whole,dp(66)))
        data class Track(val index: Int,val id: String,val kind: Selection,val text: String,val start: Long,val duration: Long,val color: Int)
        val tracks=mutableListOf<Track>(); start=0
        state.clips.forEachIndexed { index,c ->
            c.sounds.forEach { tracks.add(Track(index,it.id,Selection.AUDIO,"♫ ${it.name}",start+it.startMs,plan?.soundLengths?.get("${c.id}:${it.id}") ?: (ClipEdits.duration(c)-it.startMs),Color.rgb(26,72,57))) }
            c.texts.forEach { tracks.add(Track(index,it.id,Selection.TEXT,"T ${it.text}",start+it.startMs,(if(it.endMs>0) minOf(it.endMs,ClipEdits.duration(c)) else ClipEdits.duration(c))-it.startMs,Color.rgb(58,50,81))) }
            c.overlays.forEach { tracks.add(Track(index,it.id,Selection.STICKER,"◇ Image overlay",start+it.startMs,(if(it.endMs>0) minOf(it.endMs,ClipEdits.duration(c)) else ClipEdits.duration(c))-it.startMs,Color.rgb(64,59,35))) }
            start+=ClipEdits.duration(c)
        }
        state.projectSounds.orEmpty().forEach { tracks.add(Track(-1,it.id,Selection.AUDIO,"♫ ${it.name}",it.startMs,plan?.soundLengths?.get("global:${it.id}") ?: (it.endMs-it.startMs),Color.rgb(26,72,57))) }
        state.projectTexts.orEmpty().forEach { tracks.add(Track(-1,it.id,Selection.TEXT,"T ${it.text}",it.startMs,(if(it.endMs>0) it.endMs else total)-it.startMs,Color.rgb(58,50,81))) }
        state.projectOverlays.orEmpty().forEach { tracks.add(Track(-1,it.id,Selection.STICKER,"◇ Image overlay",it.startMs,(if(it.endMs>0) it.endMs else total)-it.startMs,Color.rgb(64,59,35))) }
        state.projectVisuals.orEmpty().forEach { tracks.add(Track(-1,it.id,Selection.VISUAL,"${it.kind}: ${com.lumen.studio.graphics.GraphicsCatalog.current?.let { catalog -> catalog.effect(it.name)?.info?.name ?: catalog.filter(it.name)?.info?.name } ?: it.name}",it.startMs,it.endMs-it.startMs,0)) }
        val lanes=mutableListOf<Triple<FrameLayout,Long,Selection>>()
        tracks.map { it.copy(duration=TimelineBounds.visibleLength(it.start,it.duration,total)) }.filter { it.duration>0 }.sortedBy { it.start }.forEach { track ->
            val laneType=when(track.kind) { Selection.AUDIO -> "Music track"; Selection.TEXT -> "Text track"; Selection.STICKER -> "Overlay track"; else -> if(track.text.startsWith("filter",true)) "Filter track" else "Effect track" }
            var lane=lanes.indexOfFirst { it.second<=track.start && it.third==track.kind && it.first.contentDescription==laneType }
            if(lane<0) { lane=lanes.size; lanes.add(Triple(FrameLayout(this).apply { contentDescription=laneType },0L,track.kind)) }
            val origin=if(track.index<0) 0L else state.clips.take(track.index).sumOf { ClipEdits.duration(it) }
            val limit=if(track.index<0) total else origin+ClipEdits.duration(state.clips[track.index])
            val item=TimelineRegionView(this,track.text,track.start,track.start+track.duration,origin,limit,pxPerMs,lead,
                { selectLayer(track.index,track.kind,track.id) }, { a,b -> retimeLayer(track.index,track.kind,track.id,a-origin,b-origin); selectLayer(track.index,track.kind,track.id) }).apply {
                isSelected=selection==track.kind && selectedId==track.id && selectionGlobal==(track.index<0) && (track.index<0 || state.active==track.index)
            }
            lanes[lane].first.addView(item,FrameLayout.LayoutParams(maxOf(dp(60),(track.duration*pxPerMs).toInt()),dp(52)).apply { leftMargin=lead+(track.start*pxPerMs).toInt() })
            lanes[lane]=Triple(lanes[lane].first,track.start+maxOf(track.duration,(dp(44)/pxPerMs).toLong()),track.kind)
        }
        lanes.forEach {
            timeline.addView(it.first,LinearLayout.LayoutParams(whole,dp(54)))
            val label=it.first.contentDescription.toString()
            laneIcon(when(label) { "Music track" -> R.drawable.ph_music_notes; "Filter track" -> R.drawable.ph_funnel; "Effect track" -> R.drawable.ph_sparkle; "Text track" -> R.drawable.ph_pencil_simple; else -> R.drawable.ph_image },label,54)
        }
        run {
            val add=FrameLayout(this)
            add.addView(button("＋ Add sound") { openSounds() },FrameLayout.LayoutParams(dp(144),dp(48)).apply { leftMargin=lead })
            timeline.addView(add,LinearLayout.LayoutParams(whole,dp(54)))
            laneIcon(R.drawable.ph_music_notes,"Add sound",54)
        }
        timeline.addView(View(this),LinearLayout.LayoutParams(whole,dp(6)))
        Ui.style(timeline); timelineSync=false; timelineHorizontal.post { syncTimeline(TimelineBounds.position(state,player?.currentPosition ?: savedPosition)) }; updateToolbar(); updateSourceMute()
    }
    private data class LayerDrag(val kind: String,val source: String,val value: String)
    private fun dragSource(view: View,kind: String,source: String,value: String) {
        view.setOnLongClickListener { pause(); it.startDragAndDrop(android.content.ClipData.newPlainText("Lumen layer",kind),View.DragShadowBuilder(it),LayerDrag(kind,source,value),0) }
    }
    private fun dropLayer(payload: LayerDrag,target: Int) {
        if(payload.kind=="filter" || payload.kind=="effect") {
            if(target in state.clips.indices) applyVisual(payload.kind,payload.value,plan?.starts?.getOrNull(target) ?: 0)
            return
        }
        if(payload.source=="global" && target in state.clips.indices) {
            val start=plan?.starts?.getOrNull(target) ?: 0
            when(payload.kind) {
                "sound" -> commit(state.copy(projectSounds=state.projectSounds.orEmpty().map { if(it.id==payload.value) it.copy(startMs=start,endMs=if(it.endMs>0) start+it.endMs-it.startMs else 0) else it }))
                "text" -> commit(state.copy(projectTexts=state.projectTexts.orEmpty().map { if(it.id==payload.value) it.copy(startMs=start,endMs=if(it.endMs>0) start+it.endMs-it.startMs else 0) else it }))
                "image" -> commit(state.copy(projectOverlays=state.projectOverlays.orEmpty().map { if(it.id==payload.value) it.copy(startMs=start,endMs=if(it.endMs>0) start+it.endMs-it.startMs else 0) else it }))
            }; return
        }
        val source=state.clips.indexOfFirst { it.id==payload.source }; if(source<0 || target !in state.clips.indices || source==target) return
        if(payload.kind=="clip") { history.select(source); commit(ClipEdits.move(state,target)); return }
        val from=state.clips[source]; val to=state.clips[target]; val list=state.clips.toMutableList()
        when(payload.kind) {
            "text" -> { val layer=from.texts.firstOrNull { it.id==payload.value } ?: return; list[source]=from.copy(texts=from.texts-layer); list[target]=to.copy(texts=to.texts+layer.copy(startMs=layer.startMs.coerceAtMost(ClipEdits.duration(to)-1),endMs=if(layer.endMs>0) minOf(layer.endMs,ClipEdits.duration(to)) else 0)) }
            "image" -> { val layer=from.overlays.firstOrNull { it.id==payload.value } ?: return; list[source]=from.copy(overlays=from.overlays-layer); list[target]=to.copy(overlays=to.overlays+layer) }
            "sound" -> { val layer=from.sounds.firstOrNull { it.id==payload.value } ?: return; list[source]=from.copy(sounds=from.sounds-layer); list[target]=to.copy(sounds=to.sounds+layer.copy(startMs=0,endMs=0)) }
            "filter" -> list[target]=to.copy(filter=payload.value)
            "effect" -> list[target]=to.copy(effect=payload.value)
        }
        commit(state.copy(clips=list,active=target))
    }
    private fun defaultLayerEnd(start: Long)=minOf(start+4000,TimelineBounds.duration(state)).coerceAtLeast(start+1)
    private fun applyVisual(kind: String,name: String,start: Long) {
        val existing=state.projectVisuals.orEmpty().firstOrNull { it.id==selectedId && it.kind==kind }
        if(name in listOf("Original","None")) {
            commit(state.copy(projectVisuals=state.projectVisuals.orEmpty().filterNot { it.kind==kind && (it.id==existing?.id || start>=it.startMs && start<it.endMs) },clips=state.clips.map { if(kind=="filter") it.copy(filter="Original") else it.copy(effect="None") }))
            return
        }
        val catalog=com.lumen.studio.graphics.GraphicsCatalog.current
        val presetId=(if(kind=="filter") catalog?.filter(name)?.info else catalog?.effect(name)?.info)?.id ?: name
        AdminConnection.event(applicationContext,"feature_selected","$kind:$presetId")
        val layer=existing?.copy(name=presetId) ?: VisualLayer(kind=kind,name=presetId,startMs=start,endMs=defaultLayerEnd(start))
        selectedId=layer.id; selection=Selection.VISUAL; selectionGlobal=true
        commit(state.copy(projectVisuals=state.projectVisuals.orEmpty().filter { it.id!=layer.id }+layer))
    }
    private fun visualAction(name: String) {
        val layer=state.projectVisuals.orEmpty().firstOrNull { it.id==selectedId } ?: return
        fun change(transform: (VisualLayer)->VisualLayer) { commit(state.copy(projectVisuals=state.projectVisuals.orEmpty().map { if(it.id==layer.id) transform(it) else it })) }
        when(name) {
            "Resize" -> slider("Frame size (%)",25,150,((layer.frameScale ?: 1f)*100).toInt()) { value -> change { it.copy(frameScale=value/100f) } }
            "Position" -> position(layer.frameX ?: 0f,layer.frameY ?: 0f) { x,y -> change { it.copy(frameX=x,frameY=y) } }
            "Thickness" -> slider("Frame thickness (%)",1,10,((layer.frameWidth ?: .025f)*100).toInt()) { value -> change { it.copy(frameWidth=value/100f) } }
            "Frame color" -> visualChoices("Frame color",TextPresets.colors) { color -> change { it.copy(frameColor=TextPresets.color(color)) } }
            "Frame style" -> choices("Frame style",listOf("Outline","Rounded","Double")) { index -> change { it.copy(frameStyle=listOf("Outline","Rounded","Double")[index]) } }
            "Add filter" -> { selectedId=""; selection=Selection.NONE; filterPanel() }
            "Add effect" -> { selectedId=""; selection=Selection.NONE; effectPanel() }
            "Replace" -> if(layer.kind=="filter") filterPanel() else effectPanel()
            "Delete" -> commit(state.copy(projectVisuals=state.projectVisuals.orEmpty().filter { it.id!=layer.id }))
            "Duration" -> layerTiming(layer.startMs,layer.endMs) { a,b -> retimeLayer(-1,Selection.VISUAL,layer.id,a,b) }
        }
    }
    private fun retimeLayer(index: Int,kind: Selection,id: String,a: Long,b: Long) {
        pause()
        if(index<0) commit(when(kind) {
            Selection.TEXT -> state.copy(projectTexts=state.projectTexts.orEmpty().map { if(it.id==id) it.copy(startMs=a,endMs=b) else it })
            Selection.STICKER -> state.copy(projectOverlays=state.projectOverlays.orEmpty().map { if(it.id==id) it.copy(startMs=a,endMs=b) else it })
            Selection.AUDIO -> state.copy(projectSounds=state.projectSounds.orEmpty().map { if(it.id==id) it.copy(startMs=a,endMs=b) else it })
            Selection.VISUAL -> state.copy(projectVisuals=state.projectVisuals.orEmpty().map { if(it.id==id) it.copy(startMs=a,endMs=b) else it })
            else -> state
        }) else {
            val clips=state.clips.toMutableList(); val c=clips[index]
            clips[index]=when(kind) {
                Selection.TEXT -> c.copy(texts=c.texts.map { if(it.id==id) it.copy(startMs=a,endMs=b) else it })
                Selection.STICKER -> c.copy(overlays=c.overlays.map { if(it.id==id) it.copy(startMs=a,endMs=b) else it })
                Selection.AUDIO -> c.copy(sounds=c.sounds.map { if(it.id==id) it.copy(startMs=a,endMs=b) else it })
                else -> c
            }; commit(state.copy(clips=clips))
        }
    }
    private fun selectedPosition()=((player?.currentPosition ?: savedPosition)-(plan?.starts?.getOrNull(state.active) ?: 0)).coerceIn(0,ClipEdits.duration(clip))
    private fun split() {
        if(state.clips.isEmpty() || !clip.video) return
        val source=ClipEdits.sourcePosition(clip,selectedPosition())
        try { commit(ClipEdits.split(state,source)) } catch(_: IllegalArgumentException) { message("Move the playhead inside a video clip before splitting.") }
    }
    private fun openTab(key: String) {
        pause(); if(state.clips.isEmpty()) { addMedia(); return }; currentTab=key; updateTabColors()
        when(key) {
            "edit" -> tools(); "audio" -> openSounds()
            "text" -> choices("Text",listOf("Add text","Add caption","Import SRT captions")) { choice -> when(choice) {
                0 -> composeText()
                1 -> composeText(caption=true)
                2 -> pick("*/*",505)
            } }
            "overlay" -> gallery("photo",502,"Add image overlay")
            "effects" -> effectPanel()
            "filters" -> filterPanel()
        }
    }
    private fun tools() { closePanel(); selection=Selection.VIDEO; selectedId=clip.id; editSubmenu=true; updateToolbar() }
    private fun clipAnimationPanel() { clipAnimationPresets("In") }
    private fun clipAnimationPresets(stage: String) {
        val catalog=com.lumen.studio.graphics.GraphicsCatalog.current ?: return
        val presets=catalog.animations.filter { it.info.category=="Clip $stage" }
        val body=panelColumn(); val row=LinearLayout(this)
        body.addView(label("Clip ${state.active+1} · ${clip.name}",14f))
        body.addView(label("Applies only to this clip. Split the video to animate a different section.",12f))
        val tabs=LinearLayout(this)
        listOf("In","Out","Combo").forEach { name -> tabs.addView(button(name) { clipAnimationPresets(name) }.apply {
            isSelected=stage==name; background=if(isSelected) StudioDesign.shape(this@EditorActivity,StudioDesign.raised(this@EditorActivity),10,true) else null
        },LinearLayout.LayoutParams(0,dp(44),1f)) }; body.addView(tabs)
        fun settings()=(clip.animations ?: ClipAnimationSettings()).fittedTo(ClipEdits.duration(clip))
        fun seconds(ms: Long)=String.format(java.util.Locale.getDefault(),"%.1f s",ms/1000f)
        fun duration(a: ClipAnimationSettings)=when(stage) { "In" -> a.inMs; "Out" -> a.outMs; else -> a.periodMs }
        fun selected(a: ClipAnimationSettings)=when(stage) { "In" -> a.incoming; "Out" -> a.outgoing; else -> a.combo }
        val summary=label("",12f)
        lateinit var timeButton: Button
        fun refresh() {
            val a=settings()
            fun part(name: String,id: String?,ms: Long)="$name: ${catalog.animation(id)?.info?.name ?: "None"}"+(if(id!=null) " · ${seconds(ms)}" else "")
            summary.text=listOf(part("In",a.incoming,a.inMs),part("Out",a.outgoing,a.outMs),part("Combo",a.combo,a.periodMs)).joinToString("\n")
            timeButton.text=if(stage=="Combo") "Loop · ${seconds(a.periodMs)}" else "Duration · ${seconds(duration(a))}"
            for(i in 0 until row.childCount) {
                val card=row.getChildAt(i) as LinearLayout
                card.isSelected=card.tag==(selected(a) ?: "none")
                selectStudioCard(card,card.isSelected)
                (card.getChildAt(1) as TextView).text="${card.contentDescription.toString().substringAfter(": ")}\n${seconds(duration(a))}"
            }
        }
        timeButton=button("Duration") {
            val a=settings(); val length=ClipEdits.duration(clip)
            val other=when(stage) { "In" -> if(a.outgoing!=null) a.outMs else 0; "Out" -> if(a.incoming!=null) a.inMs else 0; else -> 0 }
            val maxSeconds=maxOf(.1f,(length-other)/1000f)
            number("$stage duration (seconds)",duration(a)/1000f,.1f,maxSeconds) { value ->
                val current=settings(); val ms=(value*1000).toLong().coerceAtLeast(1)
                edit { it.copy(animations=when(stage) { "In" -> current.copy(inMs=ms); "Out" -> current.copy(outMs=ms); else -> current.copy(periodMs=ms) }.fittedTo(ClipEdits.duration(it))) }
                refresh()
            }
        }
        body.addView(timeButton,LinearLayout.LayoutParams(-1,dp(44)))
        val options=listOf<com.lumen.studio.graphics.AnimationPreset?>(null)+presets
        options.forEach { preset ->
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; tag=preset?.info?.id ?: "none"; isFocusable=true; contentDescription="$stage animation: ${preset?.info?.name ?: "None"}" }
            val thumbnail=ClipAnimationThumbnail(this,preset); PreviewImages.load(this,clip.uri,clip.video,thumbnail,clip.startMs.toLong())
            card.addView(thumbnail,LinearLayout.LayoutParams(dp(108),dp(88))); card.addView(label("",11f).apply { gravity=Gravity.CENTER; maxLines=3 },LinearLayout.LayoutParams(dp(116),dp(48)))
            card.setOnClickListener {
                val current=settings()
                val next=when(stage) { "In" -> current.copy(incoming=preset?.info?.id); "Out" -> current.copy(outgoing=preset?.info?.id); else -> current.copy(combo=preset?.info?.id) }.fittedTo(ClipEdits.duration(clip))
                savedPosition=(plan?.starts?.getOrNull(state.active) ?: 0)+(if(stage=="Out") (ClipEdits.duration(clip)-next.outMs).coerceAtLeast(0) else 0)
                playAfterRefresh=preset!=null; edit { it.copy(animations=next,motion=null) }; refresh()
            }
            row.addView(card,LinearLayout.LayoutParams(dp(124),dp(140)))
        }
        body.addView(HorizontalScrollView(this).apply { addView(row); isHorizontalScrollBarEnabled=false })
        body.addView(summary)
        body.addView(button("Clear clip animations") { edit { it.copy(animations=null,motion=null) }; refresh() },LinearLayout.LayoutParams(-1,dp(44)))
        refresh(); showPanel("$stage animations",body)
    }
    private fun selectedSound()=(if(selectionGlobal) state.projectSounds.orEmpty() else clip.sounds).firstOrNull { it.id==selectedId }
    private fun soundKey(layer: SoundLayer)=if(selectionGlobal) "global:${layer.id}" else "${clip.id}:${layer.id}"
    private fun audioAction(name: String) {
        val layer=selectedSound() ?: return
        fun change(action: (SoundLayer)->SoundLayer) { if(selectionGlobal) commit(state.copy(projectSounds=state.projectSounds.orEmpty().map { if(it.id==layer.id) action(it) else it })) else edit { c -> c.copy(sounds=c.sounds.map { if(it.id==layer.id) action(it) else it }) } }
        when(name) {
            "Volume" -> slider("Soundtrack volume",0,200,(layer.volume*100).toInt()) { value -> change { it.copy(volume=value/100f) } }
            "Fade" -> choices("Fade",listOf("Fade In","Fade Out","Reset")) { which -> if(which==2) change { it.copy(fadeInMs=0,fadeOutMs=0) } else number(if(which==0) "Fade in (seconds)" else "Fade out (seconds)",if(which==0) layer.fadeInMs/1000f else layer.fadeOutMs/1000f,0f,(plan?.soundLengths?.get(soundKey(layer)) ?: (layer.endMs-layer.startMs).coerceAtLeast(1))/1000f) { value -> change { if(which==0) it.copy(fadeInMs=(value*1000).toLong()) else it.copy(fadeOutMs=(value*1000).toLong()) } } }
            "Speed" -> slider("Playback speed (%)",25,400,(layer.speed*100).toInt()) { value -> change { it.copy(speed=value/100f) } }
            "Replace" -> { selectedLayer=layer.id; openSounds(506) }
            "Delete","None" -> if(selectionGlobal) commit(state.copy(projectSounds=state.projectSounds.orEmpty().filter { it.id!=layer.id })) else edit { c -> c.copy(sounds=c.sounds.filter { it.id!=layer.id }) }
            "Split" -> if(canSplit()) {
                val split=if(selectionGlobal) (player?.currentPosition ?: savedPosition) else selectedPosition(); val end=layer.startMs+(plan?.soundLengths?.get(soundKey(layer)) ?: 0)
                val left=layer.copy(endMs=split,fadeOutMs=0)
                val right=layer.copy(id=UUID.randomUUID().toString(),startMs=split,sourceStartMs=layer.sourceStartMs+((split-layer.startMs)*layer.speed).toLong(),endMs=end,fadeInMs=0)
                if(selectionGlobal) commit(state.copy(projectSounds=state.projectSounds.orEmpty().flatMap { if(it.id==layer.id) listOf(left,right) else listOf(it) })) else edit { c -> c.copy(sounds=c.sounds.flatMap { if(it.id==layer.id) listOf(left,right) else listOf(it) }) }
            }
        }
    }
    private fun textAction(name: String) {
        val layer=(if(selectionGlobal) state.projectTexts.orEmpty() else clip.texts).firstOrNull { it.id==selectedId } ?: return
        fun change(action: (TextLayer)->TextLayer) { if(selectionGlobal) commit(state.copy(projectTexts=state.projectTexts.orEmpty().map { if(it.id==layer.id) action(it) else it })) else edit { c -> c.copy(texts=c.texts.map { if(it.id==layer.id) action(it) else it }) } }
        when(name) {
            "Edit Text" -> composeText(existing=layer)
            "Font" -> visualChoices("Font",TextPresets.fonts) { name -> change { it.copy(font=name) } }
            "Style" -> visualChoices("Style",TextPresets.styles,listOf("Text size","Color","Position")) { name ->
                if(name in listOf("Text size","Color","Position")) textAction(name) else { AdminConnection.event(applicationContext,"feature_selected","text-style:"+(com.lumen.studio.graphics.GraphicsCatalog.current?.type(name)?.info?.id ?: name));change { it.copy(style=com.lumen.studio.graphics.GraphicsCatalog.current?.type(name)?.info?.id ?: name,color=com.lumen.studio.graphics.GraphicsCatalog.current?.type(name)?.textColor ?: it.color) } }
            }
            "Text size" -> slider("Text size",12,96,layer.size.toInt()) { value -> change { it.copy(size=value.toFloat()) } }
            "Color" -> visualChoices("Color",TextPresets.colors) { name -> change { it.copy(color=TextPresets.color(name)) } }
            "Position" -> position(layer.x,layer.y) { x,y -> change { it.copy(x=x,y=y) } }
            "Alignment" -> choices("Alignment",listOf("Left","Center","Right")) { index -> change { it.copy(alignment=listOf("Left","Center","Right")[index]) } }
            "Spacing" -> choices("Spacing",listOf("Letter spacing","Line spacing")) { index ->
                if(index==0) slider("Letter spacing",-10,50,(layer.letterSpacing*100).toInt()) { value -> change { it.copy(letterSpacing=value/100f) } }
                else slider("Line spacing",0,50,layer.lineSpacing.toInt()) { value -> change { it.copy(lineSpacing=value.toFloat()) } }
            }
            "Text background" -> visualChoices("Text background",TextPresets.colors,listOf("Transparent")) { name -> change { it.copy(backgroundColor=if(name=="Transparent") 0 else TextPresets.color(name)) } }
            "Text outline" -> slider("Text outline",0,12,layer.outlineWidth.toInt()) { value -> change { it.copy(outlineWidth=value.toFloat()) } }
            "Text shadow" -> choices("Text shadow",listOf("On","Off")) { index -> change { it.copy(shadow=index==0) } }
            "Text opacity" -> slider("Text opacity",0,100,((layer.opacity ?: 1f)*100).toInt()) { value -> change { it.copy(opacity=value/100f) } }
            "Text rotation" -> slider("Text rotation",-180,180,layer.rotation.toInt()) { value -> change { it.copy(rotation=value.toFloat()) } }
            "Animation" -> visualChoices("Text animation",TextPresets.animations) { name ->
                if(name!="None") { savedPosition=(if(selectionGlobal) 0 else plan?.starts?.getOrNull(state.active) ?: 0)+layer.startMs; playAfterRefresh=true }
                change { it.copy(animation=com.lumen.studio.graphics.GraphicsCatalog.current?.animation(name)?.info?.id ?: name,animationOffsetMs=0) }
            }
            "Duration" -> layerTiming(layer.startMs,layer.endMs) { start,end -> change { it.copy(startMs=start,endMs=end) } }
            "Duplicate" -> { val copy=layer.copy(id=UUID.randomUUID().toString(),x=(layer.x+.05f).coerceAtMost(1f),y=(layer.y-.05f).coerceAtLeast(-1f)); selectedId=copy.id; if(selectionGlobal) commit(state.copy(projectTexts=state.projectTexts.orEmpty()+copy)) else edit { it.copy(texts=it.texts+copy) } }
            "Delete","None" -> if(selectionGlobal) commit(state.copy(projectTexts=state.projectTexts.orEmpty().filter { it.id!=layer.id })) else edit { c -> c.copy(texts=c.texts.filter { it.id!=layer.id }) }
        }
    }
    private fun stickerAction(name: String) {
        val layer=(if(selectionGlobal) state.projectOverlays.orEmpty() else clip.overlays).firstOrNull { it.id==selectedId } ?: return
        fun change(action: (ImageLayer)->ImageLayer) { if(selectionGlobal) commit(state.copy(projectOverlays=state.projectOverlays.orEmpty().map { if(it.id==layer.id) action(it) else it })) else edit { c -> c.copy(overlays=c.overlays.map { if(it.id==layer.id) action(it) else it }) } }
        when(name) {
            "Replace" -> { selectedLayer=layer.id; gallery("photo",503,"Replace overlay") }
            "Size" -> slider("Size (%)",2,300,(layer.scale*100).toInt()) { value -> change { it.copy(scale=value/100f) } }
            "Opacity" -> slider("Opacity (%)",0,100,(layer.opacity*100).toInt()) { value -> change { it.copy(opacity=value/100f) } }
            "Duration" -> layerTiming(layer.startMs,layer.endMs) { start,end -> change { it.copy(startMs=start,endMs=end) } }
            "Flip" -> change { it.copy(flip=!it.flip) }
            "Delete","None" -> if(selectionGlobal) commit(state.copy(projectOverlays=state.projectOverlays.orEmpty().filter { it.id!=layer.id })) else edit { c -> c.copy(overlays=c.overlays.filter { it.id!=layer.id }) }
        }
    }
    private fun layerTiming(start: Long,end: Long,action: (Long,Long)->Unit) {
        val body=panelColumn(); val a=EditText(this).apply { inputType=8194; setText((start/1000f).toString()); hint=Ui.t(this@EditorActivity,"Start seconds") }
        val b=EditText(this).apply { inputType=8194; setText(((if(end>0) end else if(selectionGlobal) state.clips.sumOf { ClipEdits.duration(it) } else ClipEdits.duration(clip))/1000f).toString()); hint=Ui.t(this@EditorActivity,"End seconds") }
        body.addView(label("Start seconds")); body.addView(a); body.addView(label("End seconds")); body.addView(b)
        var previous=start to end
        liveForm("Duration",body,listOf(a,b)) {
            val first=a.text.toString().toFloatOrNull(); val last=b.text.toString().toFloatOrNull()
            if(first==null || last==null || !first.isFinite() || !last.isFinite() || first<0 || last<=first || last*1000>(if(selectionGlobal) TimelineBounds.duration(state) else ClipEdits.duration(clip))) b.error=Ui.t(this,"End must be after start")
            else { b.error=null; val value=(first*1000).toLong() to (last*1000).toLong(); if(value!=previous) { previous=value; action(value.first,value.second) } }
        }
    }

    private fun stickerPicker() {
        if(state.clips.isEmpty()) { addMedia(); return }
        val packs=linkedMapOf("Symbols" to EditorPresets.stickers.take(6),"Faces" to EditorPresets.stickers.subList(8,13),"Celebrate" to (EditorPresets.stickers.subList(6,8)+EditorPresets.stickers.subList(13,24)),"Labels" to EditorPresets.stickers.drop(24))
        visualChoices("Stickers",packs,listOf("Add image")) { name ->
            if(name=="Add image") gallery("photo",502,"Add image overlay") else {
                val bitmap=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888)
                Canvas(bitmap).drawText(name,80f,if(name.length>2) 100f else 125f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=TextPresets.color("Green"); textSize=if(name.length>2) 48f else 140f; textAlign=Paint.Align.CENTER })
                val file=java.io.File(filesDir,"sticker-${UUID.randomUUID()}.png"); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
                val previous=if(selection==Selection.STICKER && selectionGlobal) selectedId else ""
                val layer=ImageLayer(uri=Uri.fromFile(file).toString(),x=0f,y=0f,startMs=savedPosition,endMs=defaultLayerEnd(savedPosition))
                selection=Selection.STICKER; selectionGlobal=true; selectedId=layer.id; selectedLayer=layer.id
                commit(state.copy(projectOverlays=state.projectOverlays.orEmpty().filter { it.id!=previous }+layer))
            }
        }
    }

    private fun visualChoices(title: String,categories: Map<String,List<String>>,extras: List<String> = emptyList(),action: (String)->Unit) {
        val body=panelColumn(); val tabs=LinearLayout(this); val cards=LinearLayout(this)
        fun display(category: String) {
            cards.removeAllViews()
            categories.getValue(category).forEach { name ->
                val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; contentDescription="$title preset: $name"; isClickable=true }
                card.addView(TextPresetThumbnail(this,title,name),LinearLayout.LayoutParams(dp(108),dp(96)))
                card.addView(label(name,12f).apply { gravity=Gravity.CENTER;maxLines=3;tooltipText=name },LinearLayout.LayoutParams(dp(132),dp(54)))
                card.setOnClickListener { action(name); for(i in 0 until cards.childCount) selectStudioCard(cards.getChildAt(i),cards.getChildAt(i)===card) }
                cards.addView(card,LinearLayout.LayoutParams(dp(144),dp(156)).apply { marginEnd=dp(10) })
            }
        }
        categories.keys.forEach { category -> tabs.addView(button(category) { display(category) },LinearLayout.LayoutParams(dp(88),dp(44))) }
        body.addView(HorizontalScrollView(this).apply { addView(tabs); isHorizontalScrollBarEnabled=false })
        body.addView(HorizontalScrollView(this).apply { addView(cards); isHorizontalScrollBarEnabled=false })
        if(extras.isNotEmpty()) { val row=LinearLayout(this); extras.forEach { name -> row.addView(button(name) { action(name) },LinearLayout.LayoutParams(0,dp(44),1f)) }; body.addView(row) }
        display(categories.keys.first()); showPanel(title,body)
    }
    private fun filterPanel() = presetPanel(false)
    private fun effectPanel() = presetPanel(true)
    private fun presetPanel(effects: Boolean) {
        if(state.clips.isEmpty()) { addMedia(); return }
        val categories=if(effects) EditorPresets.effects else EditorPresets.filters
        val body=panelColumn(); val categoryRow=LinearLayout(this); val cards=LinearLayout(this)
        val source=clip
        val kind=if(effects) "effect" else "filter"
        val active=state.projectVisuals.orEmpty().lastOrNull { it.kind==kind && savedPosition>=it.startMs && savedPosition<it.endMs }?.name ?: if(effects) clip.effect else clip.filter
        val selectedName=(if(effects) com.lumen.studio.graphics.GraphicsCatalog.current?.effect(active) else null)?.info?.name ?: (if(!effects) com.lumen.studio.graphics.GraphicsCatalog.current?.filter(active) else null)?.info?.name ?: active
        val initialCategory=categories.entries.firstOrNull { selectedName in it.value }?.key ?: categories.keys.first()
        fun display(category: String) {
            cards.removeAllViews()
            val values=listOf(if(effects) "None" else "Original")+categories.getValue(category)
            values.distinct().forEach { name ->
                val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; contentDescription=name;tag=name;isFocusable=true;isClickable=true;background=StudioDesign.shape(this@EditorActivity,StudioDesign.surface(this@EditorActivity),12,true) }
                val image=PresetThumbnail(this,name,effects)
                PreviewImages.load(this,source.uri,source.video,image,source.startMs.toLong())
                card.addView(image,LinearLayout.LayoutParams(dp(108),dp(96)))
                card.addView(label(name,12f).apply { gravity=Gravity.CENTER;maxLines=3;tooltipText=name },LinearLayout.LayoutParams(dp(132),dp(54)))
                card.setOnClickListener {
                    applyVisual(if(effects) "effect" else "filter",name,savedPosition)
                    for(i in 0 until cards.childCount) selectStudioCard(cards.getChildAt(i),cards.getChildAt(i)===card)
                }
                dragSource(card,if(effects) "effect" else "filter",clip.id,name)
                cards.addView(card,LinearLayout.LayoutParams(dp(144),dp(156)).apply { marginEnd=dp(10) });selectStudioCard(card,name==selectedName)
            }
        }
        categories.keys.forEach { category -> categoryRow.addView(button(category) { display(category);for(i in 0 until categoryRow.childCount) selectStudioCard(categoryRow.getChildAt(i),categoryRow.getChildAt(i).tag==category) }.apply { tag=category;selectStudioCard(this,category==initialCategory);setPadding(dp(14),0,dp(14),0) },LinearLayout.LayoutParams(-2,dp(48)).apply { marginEnd=dp(8) }) }
        body.addView(HorizontalScrollView(this).apply { addView(categoryRow); isHorizontalScrollBarEnabled=false })
        body.addView(HorizontalScrollView(this).apply { addView(cards); isHorizontalScrollBarEnabled=false })
        display(initialCategory); showPanel(if(effects) "Effects" else "Filters",body)
    }
    private fun ratioPanel() {
        val body=panelColumn(); val row=LinearLayout(this)
        listOf("Fit","9:16","16:9","1:1","4:3","3:4","4:5","5:4","21:9","2:3","3:2").forEach { ratio ->
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; contentDescription="Ratio $ratio" }
            card.addView(object: View(this) {
                private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeWidth=dp(2).toFloat(); color=green }
                override fun onDraw(canvas: Canvas) { val parts=ratio.split(':'); val r=if(parts.size==2) parts[0].toFloat()/parts[1].toFloat() else 1f; val w=minOf(width*.65f,height*.75f*r); val h=w/r; canvas.drawRoundRect((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2,dp(3).toFloat(),dp(3).toFloat(),paint) }
            },LinearLayout.LayoutParams(dp(64),dp(64)))
            card.addView(label(ratio).apply { gravity=Gravity.CENTER });selectStudioCard(card,state.ratio==ratio);card.tag=ratio;card.background=StudioDesign.shape(this,StudioDesign.surface(this),12,true); card.setOnClickListener { commit(state.copy(ratio=ratio));for(i in 0 until row.childCount) selectStudioCard(row.getChildAt(i),row.getChildAt(i).tag==ratio) }
            row.addView(card,LinearLayout.LayoutParams(dp(88),dp(108)).apply { marginEnd=dp(10) })
        }
        body.addView(HorizontalScrollView(this).apply { addView(row) }); showPanel("Canvas ratio",body)
    }
    private fun cropTool() {
        val source=clip
        cropView?.let { preview.removeView(it); it.image?.recycle() }
        val overlay=CropEditorView(this,RectF((source.cropLeft+1)/2,(1-source.cropTop)/2,(source.cropRight+1)/2,(1-source.cropBottom)/2))
        cropView=overlay; preview.addView(overlay,FrameLayout.LayoutParams(-1,-1)); fullscreenControl.bringToFront()
        overlay.onGestureStart={ history.beginGroup() }; overlay.onGestureEnd={ history.endGroup() }
        val body=panelColumn(); val presets=LinearLayout(this)
        listOf("Freeform","1:1","9:16","16:9","4:5","3:4").forEach { name -> presets.addView(button(name) { for(i in 0 until presets.childCount) selectStudioCard(presets.getChildAt(i),presets.getChildAt(i).tag==name);edit { it.copy(cropRatio=name) };if(name=="Freeform") overlay.reset() else { val parts=name.split(':').map { it.toFloat() }; overlay.setRatio(parts[0]/parts[1]) } }.apply { tag=name;selectStudioCard(this,name==(source.cropRatio ?: "Freeform")) },LinearLayout.LayoutParams(dp(80),dp(48)).apply { marginEnd=dp(8) }) }
        body.addView(HorizontalScrollView(this).apply { addView(presets); isHorizontalScrollBarEnabled=false }); body.addView(button("Reset") { overlay.reset() })
        overlay.onCropChanged={ r -> if(overlay.image!=null && cropView===overlay) edit { it.copy(cropLeft=r.left*2-1,cropRight=r.right*2-1,cropTop=1-r.top*2,cropBottom=1-r.bottom*2,zoom=1f) } }
        showPanel("Crop",body)
        val sourcePosition=ClipEdits.sourcePosition(source,selectedPosition())
        worker.execute {
            val bitmap=try {
                if(source.video) {
                    val retriever=android.media.MediaMetadataRetriever()
                    try { retriever.setDataSource(applicationContext,Uri.parse(source.uri)); if(android.os.Build.VERSION.SDK_INT>=27) retriever.getScaledFrameAtTime(sourcePosition*1000L,android.media.MediaMetadataRetriever.OPTION_CLOSEST,1024,1024) else retriever.getFrameAtTime(sourcePosition*1000L,android.media.MediaMetadataRetriever.OPTION_CLOSEST) } finally { retriever.release() }
                } else {
                    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }; contentResolver.openInputStream(Uri.parse(source.uri))?.use { BitmapFactory.decodeStream(it,null,bounds) }
                    contentResolver.openInputStream(Uri.parse(source.uri))?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=(maxOf(bounds.outWidth,bounds.outHeight)/1024).coerceAtLeast(1) }) }
                }
            } catch(_: Exception) { null }
            runOnUiThread { if(isDestroyed || cropView!==overlay) bitmap?.recycle() else if(bitmap!=null) { overlay.image=bitmap;overlay.restoreRatio(clip.cropRatio) } else status.text="Unable to load crop preview" }
        }
    }
    private fun adjustmentPanel() {
        val body=panelColumn()
        val controls=listOf("Brightness","Contrast","Saturation","Exposure","Temperature","Tint","Sharpness")
        controls.forEach { name ->
            val block=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));background=StudioDesign.shape(this@EditorActivity,StudioDesign.surface(this@EditorActivity),12,true) }
            val heading=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
            fun current()=when(name) { "Brightness" -> clip.brightness.toInt();"Contrast" -> ((clip.contrast-1)*100).toInt();"Saturation" -> ((clip.saturation-1)*100).toInt();"Exposure" -> (clip.exposure*100).toInt();"Temperature" -> (clip.temperature*100).toInt();"Tint" -> (clip.tint*100).toInt();else -> ((clip.catalogAdjustments.orEmpty()["video.sharpness"] ?: 0f)*100).toInt() }
            val value=label("$name · ${current()}",13f)
            heading.addView(value,LinearLayout.LayoutParams(0,-2,1f))
            val bar=SeekBar(this).apply { max=if(name=="Sharpness") 100 else 200;progress=if(name=="Sharpness") current() else current()+100;contentDescription=name }
            fun apply(v: Int) { edit { when(name) { "Brightness" -> it.copy(brightness=v.toFloat());"Contrast" -> it.copy(contrast=1+v/100f);"Saturation" -> it.copy(saturation=1+v/100f);"Exposure" -> it.copy(exposure=v/100f);"Temperature" -> it.copy(temperature=v/100f);"Tint" -> it.copy(tint=v/100f);else -> it.copy(catalogAdjustments=it.catalogAdjustments.orEmpty()+ ("video.sharpness" to v/100f)) } };value.text="${Ui.t(this,name)} · $v" }
            heading.addView(button("Reset") { apply(0);bar.progress=if(name=="Sharpness") 0 else 100 },LinearLayout.LayoutParams(dp(64),dp(44)))
            block.addView(heading);block.addView(bar,LinearLayout.LayoutParams(-1,dp(48)))
            bar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(view: SeekBar?,p: Int,user: Boolean) { if(user) apply(if(name=="Sharpness") p else p-100) }
                override fun onStartTrackingTouch(view: SeekBar?) { history.beginGroup() }
                override fun onStopTrackingTouch(view: SeekBar?) { history.endGroup() }
            })
            body.addView(block,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        }
        showPanel("Adjust",body)
    }
    private fun tool(name: String) {
        when(name) {
            "Adjust" -> adjustmentPanel()
            "Stickers" -> choices("Stickers",listOf("★","♥","✦","☀")) { index -> edit { it.copy(texts=it.texts+TextLayer(text=listOf("★","♥","✦","☀")[index],size=48f)) } }
            "Canvas","Ratio" -> ratioPanel()
            "Background" -> choices("Background",listOf("Black","White","Green","Charcoal")) { commit(state.copy(background=listOf(Color.BLACK,Color.WHITE,green,Color.rgb(30,32,37))[it])) }
            "Rotate" -> visualChoices("Rotate",linkedMapOf("Rotation" to listOf("0","90","180","270"))) { value -> edit { it.copy(rotation=value.toFloat()) } }; "Crop" -> cropTool()
            "Opacity" -> slider("Opacity (%)",0,100,(clip.opacity*100).toInt()) { value -> edit { it.copy(opacity=value/100f) } }; "Flip" -> choices("Flip",listOf("Horizontal","Vertical")) { index -> edit { if(index==0) it.copy(flip=!it.flip) else it.copy(flipVertical=!it.flipVertical) } }
            "Speed" -> if(clip.video) choices("Playback speed",listOf("Normal speed","Curve speed","Timeline curve speed")) { kind ->
                if(kind==0) slider("Playback speed (%)",25,400,(clip.speed*100).toInt()) { value -> changeClipSpeed { it.copy(speed=value/100f,speedCurve=emptyList(),speedPositions=null) } }
                else curveSpeed(kind==2)
            } else number("Image duration (seconds)",clip.imageDurationMs/1000f,.1f,3600f) { value -> edit { it.copy(imageDurationMs=(value*1000).toLong(),curveImageDurationMs=null) } }

            "Freeze" -> captureFrame { image ->
                val file=java.io.File(filesDir,"freeze-${System.currentTimeMillis()}.png"); file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
                val clips=state.clips.toMutableList(); clips.add(state.active+1,EditorClip(Uri.fromFile(file).toString(),"Freeze frame",false)); closePanel(); commit(state.copy(clips=clips,active=state.active+1))
            }
            "Replace" -> gallery(if(clip.video) "video" else "photo",504,"Replace media")
        }
    }
    private fun changeClipSpeed(action: (EditorClip)->EditorClip) {
        val source=ClipEdits.sourcePosition(clip,selectedPosition())
        val next=action(clip)
        val start=state.clips.take(state.active).sumOf { ClipEdits.duration(it) }
        val position=start+ClipEdits.outputPosition(next,(source-next.startMs).toDouble()).toLong()
        commit(ClipEdits.update(state) { next },position)
    }
    private fun curveSpeed(entireTimeline: Boolean=false) {
        val body=panelColumn()
        body.addView(label("Tap to add dots. Drag sideways for time, up/down for speed. Hold an interior dot to remove it.",13f))
        val summary=label("",12f)
        val graph=SpeedCurveView(this,if(entireTimeline) TimelineSpeed.profile(state) else clip) { positions,speeds ->
            if(entireTimeline) { applyingTimelineCurve=true; try { commit(TimelineSpeed.apply(state,positions,speeds)) } finally { applyingTimelineCurve=false } }
            else changeClipSpeed { it.copy(speed=1f,speedCurve=speeds,speedPositions=positions) }
            summary.text="${Ui.t(this,"Playback duration")}: ${if(entireTimeline) TimelineBounds.duration(state) else ClipEdits.duration(clip)} ms · ${Ui.t(this,"Full source content is preserved")}"
        }
        val rates=LinearLayout(this)
        listOf(.25f,.5f,1f,2f,4f).forEach { rate -> rates.addView(button("$rate×") { graph.setUniformSpeed(rate) },LinearLayout.LayoutParams(0,dp(44),1f).apply { marginEnd=dp(4) }) }
        body.addView(rates)
        body.addView(summary)
        body.addView(button("Preview speed") { savedPosition=if(entireTimeline) 0 else state.clips.take(state.active).sumOf { ClipEdits.duration(it) };playAfterRefresh=true;refreshPreview() },LinearLayout.LayoutParams(-1,dp(48)))
        val actions=LinearLayout(this)
        actions.addView(button("Add dot") { graph.addDot() },LinearLayout.LayoutParams(0,dp(48),1f))
        actions.addView(button("Remove dot") { graph.removeDot() },LinearLayout.LayoutParams(0,dp(48),1f))
        body.addView(actions)
        body.addView(graph,LinearLayout.LayoutParams(-1,dp(185)))
        showPanel(if(entireTimeline) "Timeline curve speed" else "Curve speed",body)
    }
    private fun updateTabColors() {
        fun update(view: View) { if(view is Button && view.tag?.toString()?.startsWith("editor-tab:")==true) { view.isSelected=view.tag=="editor-tab:$currentTab"; view.setTextColor(if(view.isSelected) green else Color.rgb(165,174,185)) }; if(view is ViewGroup) for(i in 0 until view.childCount) update(view.getChildAt(i)) }
        update(root)
    }
    private fun panelColumn()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(10),dp(4),dp(10),dp(10)) }
    private fun showPanel(title: String,body: View) {
        currentPanel?.onExit?.invoke(); pause(); currentPanel?.let { panelStack.addLast(it) }; mountPanel(ToolPage(title,body))
    }
    private fun clearPresetHighlights(view: View) {
        if(view is LinearLayout && view.isClickable) view.foreground=null
        if(view is ViewGroup) for(i in 0 until view.childCount) clearPresetHighlights(view.getChildAt(i))
    }
    private fun resetFor(title: String): (() -> Unit)? {
        if(state.clips.isEmpty()) return null
        fun textReset(): Unit {
            if(selectionGlobal) commit(state.copy(projectTexts=state.projectTexts.orEmpty().map { if(it.id==selectedId) PresetReset.text(title,it) else it }))
            else edit { it.copy(texts=it.texts.map { layer -> if(layer.id==selectedId) PresetReset.text(title,layer) else layer }) }
        }
        if(title=="Timeline curve speed") return { applyingTimelineCurve=true; try { commit(TimelineSpeed.apply(state,listOf(0f,1f),listOf(1f,1f))) } finally { applyingTimelineCurve=false } }
        if(title in listOf("Filters","Effects")) return { applyVisual(if(title=="Filters") "filter" else "effect","None",savedPosition); val action: (EditorClip)->EditorClip={ PresetReset.clip(title,it) }; if(selection==Selection.NONE) commit(state.copy(clips=state.clips.map(action))) else edit(action) }
        if(title=="Canvas ratio") return { commit(state.copy(ratio="Fit")) }
        if(title=="Background") return { commit(state.copy(background=Color.BLACK)) }
        if(title=="Stickers") return { if(selection==Selection.STICKER) stickerAction("None") }
        if(selection==Selection.TEXT && title in listOf("Font","Style","Color","Text animation","Text size","Position","Duration","Alignment","Letter spacing","Line spacing","Text background","Text outline","Text shadow","Text opacity","Text rotation")) return { textReset() }
        if(selection==Selection.STICKER && title in listOf("Size (%)","Opacity (%)","Position","Duration")) return {
            if(selectionGlobal) commit(state.copy(projectOverlays=state.projectOverlays.orEmpty().map { if(it.id==selectedId) PresetReset.image(title,it) else it }))
            else edit { it.copy(overlays=it.overlays.map { layer -> if(layer.id==selectedId) PresetReset.image(title,layer) else layer }) }
        }
        if(selection==Selection.AUDIO && title in listOf("Soundtrack volume","Fade","Fade in (seconds)","Fade out (seconds)","Playback speed (%)")) return {
            if(selectionGlobal) commit(state.copy(projectSounds=state.projectSounds.orEmpty().map { if(it.id==selectedId) PresetReset.sound(title,it) else it }))
            else edit { it.copy(sounds=it.sounds.map { layer -> if(layer.id==selectedId) PresetReset.sound(title,layer) else layer }) }
        }
        if(title in listOf("Adjust","Brightness","Contrast","Saturation","Exposure","Temperature","Tint","Playback speed","Playback speed (%)","Curve speed","Crop","Rotate","Flip","Opacity (%)","Sound","Clip volume (%)","Fade in (seconds)","Fade out (seconds)","Image duration (seconds)")) return { edit { PresetReset.clip(title,it) } }
        return null
    }
    private fun mountPanel(page: ToolPage) {
        currentPanel=page; toolPanel.removeAllViews(); toolPanel.visibility=View.VISIBLE
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(button("←") { panelBack() }.apply { contentDescription="Back to tools"; background=null },LinearLayout.LayoutParams(dp(44),dp(44)))
        header.addView(label(page.title,14f).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END },LinearLayout.LayoutParams(0,-2,1f))
        resetFor(page.title)?.let { reset -> header.addView(button("None") { history.endGroup(); page.onExit=null; playAfterRefresh=false; reset(); if(page.title !in listOf("Filters","Effects","Font","Style","Color","Text animation","Canvas ratio","Rotate","Flip","Adjust","Playback speed","Stickers")) closePanel() else clearPresetHighlights(page.body) }.apply { contentDescription="None: ${page.title}"; setTextColor(green); background=null },LinearLayout.LayoutParams(dp(48),dp(44))) }
        header.addView(StudioDesign.button(this,"Done",primary=true) { closePanel();saveDraft() }.apply { contentDescription="Done: ${page.title}" },LinearLayout.LayoutParams(dp(64),dp(44)))
        toolPanel.addView(header,LinearLayout.LayoutParams(-1,dp(48)))
        (page.body.parent as? ViewGroup)?.removeView(page.body)
        toolPanel.addView(ScrollView(this).apply { addView(page.body); isFillViewport=false },LinearLayout.LayoutParams(-1,0,1f))
        Ui.style(toolPanel); resizeWorkbench()
    }
    private fun panelBack() { if(panelStack.isEmpty()) closePanel() else { currentPanel?.onExit?.invoke(); mountPanel(panelStack.removeLast()) } }
    private fun closePanel() {
        currentPanel?.onExit?.invoke()
        history.endGroup()
        cropView?.let { preview.removeView(it); it.image?.recycle() }; cropView=null
        if(!::toolPanel.isInitialized) return
        currentPanel=null; panelStack.clear(); toolPanel.removeAllViews(); toolPanel.visibility=View.GONE; currentTab=""; updateTabColors()
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(root.windowToken,0); resizeWorkbench()
    }
    private fun choices(title: String,items: List<String>,action: (Int)->Unit) {
        val body=panelColumn()
        items.chunked(3).forEachIndexed { row,values ->
            val line=LinearLayout(this)
            values.forEachIndexed { column,value -> line.addView(button(value) { action(row*3+column) }.apply { textSize=11f; if(title=="Effects" && state.clips.isNotEmpty()) dragSource(this,"effect",clip.id,value) },LinearLayout.LayoutParams(0,dp(48),1f).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) }) }
            repeat(3-values.size) { line.addView(View(this),LinearLayout.LayoutParams(0,dp(48),1f)) }; body.addView(line)
        }
        showPanel(title,body)
    }
    private fun composeText(caption: Boolean=false,existing: TextLayer?=null) {
        closePanel()
        val global=if(existing!=null) selectionGlobal else true
        val start=savedPosition
        val base=existing ?: TextLayer(text="Your text",y=if(caption) -.7f else 0f,startMs=start,endMs=defaultLayerEnd(start))
        selection=Selection.TEXT; selectionGlobal=global; selectedId=base.id
        if(existing==null) { if(global) commit(state.copy(projectTexts=state.projectTexts.orEmpty()+base)) else edit { it.copy(texts=it.texts+base) } }
        val field=EditText(this).apply { setText(base.text); selectAll(); hint=Ui.t(this@EditorActivity,"Enter text"); minLines=2; maxLines=4; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE; filters=arrayOf(android.text.InputFilter.LengthFilter(2000)) }
        var previous=base.text
        fun update() {
            if(isDestroyed) return
            val value=field.text.toString(); if(value==previous) return; previous=value
            val old=if(global) state.projectTexts.orEmpty() else clip.texts
            val next=if(value.isBlank()) old.filter { it.id!=base.id } else if(old.any { it.id==base.id }) old.map { if(it.id==base.id) it.copy(text=value) else it } else old+base.copy(text=value)
            selection=Selection.TEXT; selectionGlobal=global; selectedId=base.id
            if(global) commit(state.copy(projectTexts=next)) else edit { it.copy(texts=next) }
        }
        val body=panelColumn(); body.addView(field,LinearLayout.LayoutParams(-1,dp(72)))
        val row=LinearLayout(this)
        listOf("Font","Style","Color","Animation").forEach { name -> row.addView(button(name) { if(field.text.isBlank()) field.setText("Your text"); field.clearFocus(); (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(field.windowToken,0); textAction(name) },LinearLayout.LayoutParams(0,dp(48),1f)) }
        body.addView(row)
        showPanel(if(caption) "Caption" else "Text",body)
        history.beginGroup(); currentPanel?.onExit={ history.endGroup() }
        field.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { update() }
        })
    }
    private fun liveForm(title: String,body: LinearLayout,fields: List<EditText>,apply: () -> Unit) {
        showPanel(title,body)
        history.beginGroup(); currentPanel?.onExit={ history.endGroup() }
        val watcher=object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if(currentPanel?.body===body) apply()
            }
        }
        fields.forEach { it.addTextChangedListener(watcher) }
    }
    private fun number(title: String,current: Float,min: Float,max: Float,action: (Float)->Unit) {
        val body=panelColumn(); val field=EditText(this).apply { inputType=8194; setText(current.toString()); setTextColor(Ui.foreground(this@EditorActivity,Color.WHITE)) }
        body.addView(field); body.addView(label("$min - $max")); var previous=current
        liveForm(title,body,listOf(field)) {
            val value=field.text.toString().toFloatOrNull()
            if(value==null || !value.isFinite() || value !in min..max) field.error="$min - $max"
            else { field.error=null; if(value!=previous) { previous=value; action(value) } }
        }
    }
    private fun slider(title: String,min: Int,max: Int,current: Int,action: (Int)->Unit) {
        val body=panelColumn(); val value=label(current.toString()); val bar=SeekBar(this).apply { this.max=max-min; progress=current.coerceIn(min,max)-min }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(v: SeekBar?,p: Int,u: Boolean) { value.text=(min+p).toString(); if(u) action(min+p) }; override fun onStartTrackingTouch(v: SeekBar?) { history.beginGroup() }; override fun onStopTrackingTouch(v: SeekBar?) { history.endGroup() } })
        body.addView(value); body.addView(bar); showPanel(title,body); currentPanel?.onExit={ history.endGroup() }
    }
    private fun position(x: Float,y: Float,action: (Float,Float)->Unit) {
        val body=panelColumn(); val horizontal=SeekBar(this).apply { max=200; progress=(x*100+100).toInt() }; val vertical=SeekBar(this).apply { max=200; progress=(y*100+100).toInt() }
        body.addView(label("Horizontal")); body.addView(horizontal); body.addView(label("Vertical")); body.addView(vertical)
        val listener=object: SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(v: SeekBar?,p: Int,u: Boolean) { if(u) action((horizontal.progress-100)/100f,(vertical.progress-100)/100f) }; override fun onStartTrackingTouch(v: SeekBar?) { history.beginGroup() }; override fun onStopTrackingTouch(v: SeekBar?) { history.endGroup() } }
        horizontal.setOnSeekBarChangeListener(listener); vertical.setOnSeekBarChangeListener(listener); showPanel("Position",body); currentPanel?.onExit={ history.endGroup() }
    }
    private fun captureFrame(action: (Bitmap)->Unit) {
        pause(); if(!previewRendered || surface.width<=0 || surface.height<=0) { status.text="Wait for the preview to load"; return }
        val image=if(stillPreview.visibility==View.VISIBLE) (stillPreview.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.copy(Bitmap.Config.ARGB_8888,false) else surface.bitmap
        if(image!=null) action(image) else status.text="Unable to capture this frame"
    }
    @Suppress("DEPRECATION") private fun gallery(mode: String,request: Int,title: String) { startActivityForResult(Intent(this,MediaPickerActivity::class.java).putExtra("mode",mode).putExtra("max_items",1).putExtra("locked_type",true).putExtra("picker_title",title),request) }
    @Suppress("DEPRECATION") private fun addMedia() { startActivityForResult(Intent(this,MediaPickerActivity::class.java).putExtra("mode","all"),500) }
    @Suppress("DEPRECATION") private fun openSounds(request: Int=501) { startActivityForResult(Intent(this,SoundLibraryActivity::class.java),request) }
    @Suppress("DEPRECATION") private fun pick(type: String,request: Int) { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { this.type=type; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) },request) }
    @Deprecated("Platform activity result API") override fun onActivityResult(request: Int,result: Int,data: Intent?) {
        super.onActivityResult(request,result,data); if(result != RESULT_OK) return
        if(request == 500) { val uris=data?.getStringArrayListExtra("uris") ?: return; val names=data.getStringArrayListExtra("names") ?: arrayListOf()
            commit(state.copy(clips=state.clips+uris.mapIndexed { index,uri -> EditorClip(uri,names.getOrNull(index) ?: "Media",TimelineEngine.isVideo(this,uri)) })); return }
        if(request == 501 || request==506) {
            val uri=data?.getStringExtra("sound_uri") ?: return
            val duration=try { TimelineEngine.duration(this,uri) } catch(_: Exception) { 0L }
            val layer=SoundLayer(uri=uri,name=data.getStringExtra("sound_name") ?: "Added soundtrack",startMs=savedPosition,endMs=minOf(defaultLayerEnd(savedPosition),savedPosition+duration))
            if(request==506) {
                if(selectionGlobal) commit(state.copy(projectSounds=state.projectSounds.orEmpty().map { if(it.id==selectedLayer) it.copy(uri=uri,name=layer.name,sourceStartMs=0,sourceEndMs=0) else it })) else edit { c -> c.copy(sounds=c.sounds.map { if(it.id==selectedLayer) it.copy(uri=uri,name=layer.name,sourceStartMs=0,sourceEndMs=0) else it }) }
                closePanel(); return
            }
            selection=Selection.AUDIO; selectionGlobal=true; selectedId=layer.id; selectedLayer=layer.id
            val clips=state.clips
            commit(state.copy(clips=clips,projectSounds=state.projectSounds.orEmpty()+layer)); closePanel()
            return
        }
        val uri=data?.data ?: data?.getStringArrayListExtra("uris")?.firstOrNull()?.let { Uri.parse(it) } ?: return
        try {
            val grants=if(request>=700) data?.flags ?: 0 else Intent.FLAG_GRANT_READ_URI_PERMISSION
            val read=grants and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0; val write=grants and Intent.FLAG_GRANT_WRITE_URI_PERMISSION!=0
            if(read && write) contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            else if(read) contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            else if(write) contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch(_: SecurityException) {}
        when(request) {
            505 -> {
                try { val captions=contentResolver.openInputStream(uri)!!.bufferedReader().use { CaptionParser.parse(it.readText(),state.clips.sumOf { ClipEdits.duration(it) }) }; require(captions.isNotEmpty()); commit(state.copy(projectTexts=state.projectTexts.orEmpty()+captions)) }
                catch(_: Exception) { message("Choose a valid SRT caption file.") }
            }
            502 -> { val layer=ImageLayer(uri=uri.toString(),startMs=savedPosition,endMs=defaultLayerEnd(savedPosition)); selection=Selection.STICKER; selectionGlobal=true; selectedId=layer.id; selectedLayer=layer.id; commit(state.copy(projectOverlays=state.projectOverlays.orEmpty()+layer)); closePanel() }
            503 -> if(selectionGlobal) commit(state.copy(projectOverlays=state.projectOverlays.orEmpty().map { if(it.id==selectedLayer) it.copy(uri=uri.toString()) else it })) else edit { c -> c.copy(overlays=c.overlays.map { if(it.id == selectedLayer) it.copy(uri=uri.toString()) else it }) }
            504 -> edit { c -> TimelineEngine.metadata(this,c.copy(uri=uri.toString(),name=data?.getStringArrayListExtra("names")?.firstOrNull() ?: "Replacement",video=TimelineEngine.isVideo(this,uri.toString()),startMs=0,endMs=0,sourceDurationMs=0,hasAudio=null,cropLeft=-1f,cropRight=1f,cropBottom=-1f,cropTop=1f,reverse=false,speedCurve=emptyList())).let { replacement -> replacement.copy(endMs=if(replacement.video) minOf(replacement.sourceDurationMs,(ClipEdits.duration(c)*replacement.speed).toLong()).toInt() else 0) } }
            700 -> { val image=pendingImage ?: return; pendingImage=null; worker.execute {
                val ok=try { contentResolver.openOutputStream(uri)?.use { image.compress(Bitmap.CompressFormat.PNG,100,it) } ?: false } catch(_: Exception) { false }
                runOnUiThread { if(!isDestroyed) message(if(ok) "Image exported successfully." else "Export failed. Please choose another destination.") } } }
            701 -> { val snapshot=pendingExport ?: state; pendingExport=null; lastExportMessage=""; try { player?.release(); player=null; VideoExport.start(this,projectId,snapshot,uri,exportResolution) } catch(error: Exception) { message(error.message ?: "Export failed.") } }
        }
    }
    @Suppress("DEPRECATION") private fun exportMenu() {
        pause(); if(state.clips.isEmpty() || preparing || VideoExport.busy) return
        val previewReward=AdsController.available("preview_reward")
        topExportChoices("Export resolution",listOf("360p","480p","720p","1080p","2000p")+if(previewReward) listOf("Watch ad · pause ads for 30 minutes") else emptyList()) { index ->
            if(index==5) {
                AdsController.reward(this,"preview_reward") { earned -> if(!isDestroyed) Toast.makeText(this,if(earned) "Ads paused for 30 minutes in this session" else "No reward earned. All editing and export options remain available.",Toast.LENGTH_LONG).show() }
                return@topExportChoices
            }
            exportResolution=listOf(360,480,720,1080,2000)[index]
            if(AdsController.offerExportReward(exportResolution)) {
                AppDialog(this).setTitle("${exportResolution}p export")
                    .setMessage("Watch an optional ad to pause ads for 30 minutes in this session, or export normally without the reward.")
                    .setNegativeButton("Export normally") { _,_ -> chooseExportDestination() }
                    .setNeutralButton("Back",null)
                    .setPositiveButton("Watch ad & export") { _,_ ->
                        AdsController.reward(this,"export_reward") { earned ->
                            if(!isDestroyed && !isFinishing) {
                                Toast.makeText(this,if(earned) "Ads paused for 30 minutes" else "No reward earned. You can still export normally.",Toast.LENGTH_LONG).show()
                                exportAfterReward=true
                                window.decorView.post { if(hasWindowFocus() && exportAfterReward) { exportAfterReward=false;chooseExportDestination() } }
                            }
                        }
                    }.show()
            } else chooseExportDestination()
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus && exportAfterReward && !isFinishing && !isDestroyed) { exportAfterReward=false;chooseExportDestination() }
    }
    @Suppress("DEPRECATION") private fun chooseExportDestination() {
            if(saveDraft()) {
                closePanel(); pendingExport=state; status.text="Choose where to save your ${exportResolution}p video"
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type="video/mp4"; addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    putExtra(Intent.EXTRA_TITLE,"Lumen_${exportResolution}p_${System.currentTimeMillis()}.mp4")
                },701)
            }
    }
    private fun topExportChoices(title: String,items: List<String>,action: (Int)->Unit) {
        toolsDialog?.dismiss()
        val dialog=Dialog(this)
        val body=panelColumn().apply { background=StudioDesign.shape(this@EditorActivity,StudioDesign.surface(this@EditorActivity),20); setPadding(dp(20),dp(16),dp(20),dp(20)) }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(label(title,21f),LinearLayout.LayoutParams(0,dp(52),1f))
        header.addView(StudioDesign.button(this,"×") { dialog.dismiss() }.apply { contentDescription="Close export options"; background=null },LinearLayout.LayoutParams(dp(48),dp(48)))
        body.addView(header)
        body.addView(StudioDesign.label(this,"Choose the video resolution for export",13f,secondary=true),LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(16) })
        var selected=listOf(360,480,720,1080,2000).indexOf(exportResolution).let { if(it<0) 2 else it }
        val rows=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val descriptions=listOf("Smaller file size, faster export","Good quality, balanced size","High quality, recommended","Full HD, best for sharing","Highest quality, larger file size")
        fun drawOptions() {
            rows.removeAllViews()
            items.take(5).forEachIndexed { index,item ->
                val chosen=index==selected
                val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(12),dp(10),dp(12),dp(10)); background=StudioDesign.shape(this@EditorActivity,if(chosen) StudioDesign.raised(this@EditorActivity) else StudioDesign.background(this@EditorActivity),14).apply { if(chosen) setStroke(dp(1),StudioDesign.accent(this@EditorActivity)) }; contentDescription="Export $item"; isSelected=chosen; setOnClickListener { selected=index; drawOptions() } }
                row.addView(StudioDesign.label(this,listOf("SD","SD","HD","FHD","2K")[index],13f,bold=true).apply { gravity=Gravity.CENTER; setTextColor(StudioDesign.accent(this@EditorActivity)); background=StudioDesign.shape(this@EditorActivity,StudioDesign.raised(this@EditorActivity),12) },LinearLayout.LayoutParams(dp(48),dp(48)))
                row.addView(LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(14),0,dp(4),0); addView(StudioDesign.label(this@EditorActivity,item,16f,bold=true)); addView(StudioDesign.label(this@EditorActivity,descriptions[index],12f,secondary=true)) },LinearLayout.LayoutParams(0,-2,1f))
                row.addView(StudioDesign.label(this,if(chosen) "◉" else "○",26f).apply { setTextColor(if(chosen) StudioDesign.accent(this@EditorActivity) else StudioDesign.muted(this@EditorActivity)); gravity=Gravity.CENTER },LinearLayout.LayoutParams(dp(32),dp(44)))
                rows.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
            }
        }
        drawOptions(); body.addView(rows)
        if(items.size>5) body.addView(StudioDesign.button(this,items[5]) { dialog.dismiss(); action(5) })
        body.addView(StudioDesign.button(this,"Apply",primary=true) { dialog.dismiss(); action(selected) }.apply { StudioDesign.referenceAction(this@EditorActivity,this) },LinearLayout.LayoutParams(-1,dp(52)).apply { topMargin=dp(20) })
        dialog.setContentView(ScrollView(this).apply { addView(body) })
        dialog.setOnDismissListener { if(toolsDialog===dialog) toolsDialog=null }
        toolsDialog=dialog; dialog.show()
        dialog.window?.apply { setBackgroundDrawableResource(android.R.color.transparent); setGravity(Gravity.BOTTOM); setLayout(-1,-2); addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); attributes=attributes.apply { dimAmount=.35f }; setWindowAnimations(android.R.style.Animation_Dialog) }
    }
    private fun openExport(uri: String) { pause(); startActivity(Intent(this,VideoPlayerActivity::class.java).putExtra("video_uri",uri)) }
    private fun saveDraft(): Boolean { if(!::history.isInitialized || editingEpoch!=EditingStorage.epoch(this)) return false; return try { ProjectStore.save(this,SavedProject(projectId,projectName,System.currentTimeMillis(),state),editingEpoch); true } catch(_: Exception) { if(::status.isInitialized) status.text="Project could not be saved. Check available storage."; false } }
    private val tick=object : Runnable { override fun run() {
        if(!isDestroyed && ::seek.isInitialized) {
            val position=player?.currentPosition ?: savedPosition; if(player?.isPlaying == true) selectAt(position)
            val end=TimelineBounds.duration(state); time.text="%02d:%02d / %02d:%02d".format(position/60000,position/1000%60,end/60000,end/1000%60); seek.progress=position.toInt(); play.text=if(player?.isPlaying == true) "Ⅱ" else "▶"
            if(VideoExport.projectId == projectId) { if(VideoExport.busy) { status.text="${Ui.t(this@EditorActivity,VideoExport.message)} ${VideoExport.progress}%"; export.isEnabled=false }
                else if(VideoExport.message.isNotEmpty() && lastExportMessage != VideoExport.message) { lastExportMessage=VideoExport.message; status.text=VideoExport.message
                    if(player==null) refreshPreview()
                    if(VideoExport.progress==100 && VideoExport.destination.isNotEmpty()) {
                        getSharedPreferences("exports",MODE_PRIVATE).edit().putString(projectId,VideoExport.destination).apply()
                        val reward=AdsController.available("export_reward")
                        choices("Export complete - ${VideoExport.outputSize}",listOf("Play exported video","Continue editing")+if(reward) listOf("Watch ad · pause ads for 30 minutes") else emptyList()) { index ->
                            when(index) {
                                0 -> openExport(VideoExport.destination)
                                2 -> AdsController.reward(this@EditorActivity,"export_reward") { earned -> if(!isDestroyed) Toast.makeText(this@EditorActivity,if(earned) "Ads paused for 30 minutes in this session" else "Reward not earned. Your exported video is already saved.",Toast.LENGTH_LONG).show() }
                                else -> closePanel()
                            }
                        }
                        val resultPanel=currentPanel
                        AdsController.afterExport(this@EditorActivity) { !isDestroyed && hasWindowFocus() && currentPanel===resultPanel && toolPanel.visibility==View.VISIBLE && player?.isPlaying!=true }
                    }
                    export.isEnabled=!preparing } }
            syncTimeline(position); updateSplitEnabled(); updateLayerControls(); handler.postDelayed(this,100)
        }
    } }
    private fun projectPanel() {
        // Metadata editing does not open the workbench or resize the video surface.
        val field=EditText(this).apply { setText(projectName); setSingleLine(); selectAll(); filters=arrayOf(android.text.InputFilter.LengthFilter(80)) }
        AppDialog(this).setTitle("Rename project").setView(field).setNegativeButton("Cancel",null)
            .setPositiveButton("Save") { _,_ -> val value=field.text.toString().trim(); if(value.isNotEmpty()) { ProjectPresentation.rename(this,projectId,value.take(80)); projectName=value.take(80); projectTitle.text=projectName; saveDraft() } }.show()
    }
    private fun navigateBack() { if(fullscreen) toggleFullscreen() else if(currentPanel!=null) panelBack() else if(editSubmenu) { editSubmenu=false; updateToolbar() } else if(selection!=Selection.NONE) deselect() else leave() }
    private fun leave() { pause(); saveDraft(); finish() }
    override fun handleBack() { navigateBack() }
    override fun onSaveInstanceState(out: Bundle) { out.putLong("editing_epoch",editingEpoch); out.putInt("export_resolution",exportResolution); out.putBoolean("selection_global",selectionGlobal); out.putString("selection",selection.name); out.putString("selected_id",selectedId); out.putBoolean("edit_submenu",editSubmenu); out.putFloat("timeline_zoom",timelineZoom); out.putSerializable("state",state); out.putSerializable("undo",ArrayList(history.undo)); out.putSerializable("redo",ArrayList(history.redo)); out.putString("project_id",projectId); out.putString("project_name",projectName); out.putLong("position",player?.currentPosition ?: savedPosition); out.putBoolean("fullscreen",fullscreen); out.putSerializable("pending_export",pendingExport); out.putString("selected_layer",selectedLayer); super.onSaveInstanceState(out) }
    override fun onPause() { pause(); if(::playbackCache.isInitialized) playbackCache.cancel(); saveDraft(); generation++; player?.release(); player=null; resumePreview=true; super.onPause() }
    override fun onResume() { super.onResume(); if(editingEpoch!=EditingStorage.epoch(this)) { finish(); return }; if(resumePreview && ::history.isInitialized && !VideoExport.busy) refreshPreview() }
    override fun onDestroy() { if(::playbackCache.isInitialized) playbackCache.release(); generation++; handler.removeCallbacksAndMessages(null); toolsDialog?.dismiss(); player?.release(); worker.execute { StillPreviewRenderer.releaseThreadResources() }; worker.shutdown(); super.onDestroy() }
}




