package com.lumen.studio

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*

class ProjectRow(context: Context,private val project: SavedProject,private val changed: ()->Unit={},private val compact: Boolean=false,private val gridCard: Boolean=false,private val open: ()->Unit): LinearLayout(context) {
    val detailViews=mutableListOf<View>()
    private fun dp(n: Int)=Ui.dp(context,n)
    init { render() }
    private fun render() {
        if(compact) { renderCompact(); return }
        removeAllViews(); detailViews.clear(); orientation=VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12))
        background=StudioDesign.shape(context,StudioDesign.surface(context),16,true)
        layoutParams=LayoutParams(-1,-2).apply { setMargins(0,dp(4),0,dp(8)) }
        val name=ProjectPresentation.name(context,project.id,project.name)
        val thumb=FrameLayout(context)
        val image=ImageView(context).apply { tag="media"; scaleType=ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFF282F34.toInt()) }
        thumb.addView(image,FrameLayout.LayoutParams(-1,-1))
        (project.state.clips.firstOrNull { it.video } ?: project.state.clips.firstOrNull())?.let { PreviewImages.load(context,it.uri,it.video,image) }
        val seconds=project.state.clips.sumOf(ClipEdits::duration)/1000
        thumb.addView(TextView(context).apply { tag="media"; text=if(project.state.photoEditor==true) (if(project.state.clips.size>1) "Collage" else "Photo") else "%d:%02d".format(seconds/60,seconds%60); textSize=12f; setTextColor(Color.WHITE); setPadding(dp(8),dp(4),dp(8),dp(4)); setBackgroundColor(0xCC101018.toInt()) },FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.END))
        val resolution=TextView(context).apply { tag="media"; text="Source"; textSize=11f; setTextColor(Color.WHITE); setPadding(dp(8),dp(4),dp(8),dp(4)); setBackgroundColor(0xCC101018.toInt()) }
        thumb.addView(resolution,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START))
        ProjectResolution.load(context,project,resolution,"",true)
        thumb.setOnClickListener { open() }; addView(thumb,LayoutParams(-1,dp(120)))
        val title=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        title.addView(StudioDesign.label(context,name,16f,bold=true).apply { minLines=2; maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END },LayoutParams(0,-2,1f))
        title.addView(studioIcon(context,R.drawable.ph_pencil_simple,"Rename project") { rename() },LayoutParams(dp(48),dp(48)))
        addView(title)
        val count=project.state.clips.size
        val prefix="$count ${if(project.state.photoEditor==true) { if(count==1) "photo" else "photos" } else { if(count==1) "clip" else "clips" }}"
        val detail=StudioDesign.label(context,"$prefix · ${project.state.ratio} canvas",12f,secondary=true).apply { minLines=2; maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END }
        addView(detail); detailViews.add(detail); ProjectResolution.load(context,project,detail,prefix)
        val date=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(project.updated))
        addView(StudioDesign.label(context,"Edited: $date",11f,secondary=true).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END }.also { detailViews.add(it) })
        val progress=ProjectPresentation.progress(context,project.id)
        addView(StudioDesign.button(context,"Your progress · $progress%") { progressDialog() }.apply { background=null; textSize=12f; gravity=Gravity.START or Gravity.CENTER_VERTICAL; setPadding(0,0,0,0) },LayoutParams(-1,dp(48)))
        addView(ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal).apply { max=100; this.progress=progress; progressTintList=ColorStateList.valueOf(StudioDesign.accent(context)); contentDescription="Your progress $progress percent" },LayoutParams(-1,dp(6)))
        val actions=LinearLayout(context)
        actions.addView(StudioDesign.button(context,"Edit",action=open),LayoutParams(0,dp(48),1f))
        actions.addView(studioIcon(context,R.drawable.ph_trash,"Delete project") {
            AppDialog(context).setTitle("Delete project?").setMessage("Remove this draft? Original media files will be preserved.").setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_ -> ProjectStore.delete(context,project.id); (parent as? android.view.ViewGroup)?.removeView(this); changed() }.show()
        },LayoutParams(dp(48),dp(48)).apply { marginStart=dp(8) })
        addView(actions,LayoutParams(-1,-2).apply { topMargin=dp(10) })
        isFocusable=true; contentDescription="Open project: $name"; setOnClickListener { open() }
        fun motion(raised: Boolean) { if(android.animation.ValueAnimator.areAnimatorsEnabled()) animate().scaleX(if(raised) 1.01f else 1f).scaleY(if(raised) 1.01f else 1f).setDuration(140).start() }
        setOnHoverListener { _,event -> motion(event.action!=android.view.MotionEvent.ACTION_HOVER_EXIT); false }
        setOnTouchListener { _,event -> motion(event.action==android.view.MotionEvent.ACTION_DOWN); false }
    }
    private fun renderCompact() {
        removeAllViews(); detailViews.clear(); orientation=if(gridCard) VERTICAL else HORIZONTAL
        gravity=Gravity.CENTER_VERTICAL; setPadding(dp(6),dp(6),dp(6),dp(6))
        background=StudioDesign.shape(context,StudioDesign.surface(context),12,true)
        layoutParams=LayoutParams(-1,-2).apply { bottomMargin=dp(8) }
        val name=ProjectPresentation.name(context,project.id,project.name)
        val thumb=FrameLayout(context)
        val image=ImageView(context).apply { tag="media"; scaleType=ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFF282F34.toInt()) }
        thumb.addView(image,FrameLayout.LayoutParams(-1,-1))
        project.state.clips.firstOrNull()?.let { PreviewImages.load(context,it.uri,it.video,image) }
        thumb.addView(ImageView(context).apply { tag="media"; setImageResource(if(project.state.photoEditor==true) R.drawable.ph_image else R.drawable.ic_video_file); imageTintList=ColorStateList.valueOf(Color.WHITE); setPadding(dp(6),dp(6),dp(6),dp(6)); background=StudioDesign.shape(context,0xBB101018.toInt(),8); contentDescription=Ui.t(context,if(project.state.photoEditor==true) "Photo project" else "Video project") },FrameLayout.LayoutParams(dp(36),dp(36),Gravity.TOP or Gravity.START).apply { setMargins(dp(5),dp(5),dp(5),dp(5)) })
        val type=if(project.state.photoEditor==true) "Photo" else "Video"
        thumb.addView(TextView(context).apply { tag="media"; text=type; textSize=10f; setTextColor(Color.WHITE); setPadding(dp(5),dp(3),dp(5),dp(3)); setBackgroundColor(0xCC101018.toInt()) },FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.START))
        val more=studioIcon(context,android.R.drawable.ic_menu_more,"Project options: $name") {
            val items=arrayOf("Open project","Rename","Your progress","Delete project")
            AppDialog(context).setTitle(name).setItems(items) { _,index -> when(index) {
                0 -> open(); 1 -> rename(); 2 -> progressDialog()
                else -> AppDialog(context).setTitle("Delete project?").setMessage("Remove this draft? Original media files will be preserved.").setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_ -> ProjectStore.delete(context,project.id); changed() }.show()
            } }.show()
        }.apply { contentDescription="Project options: $name"; background=StudioDesign.shape(context,StudioDesign.surface(context),24); setPadding(dp(12),dp(12),dp(12),dp(12)) }
        if(gridCard) {
            thumb.addView(more,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.END))
            addView(thumb,LayoutParams(-1,dp(78)))
        } else addView(thumb,LayoutParams(dp(76),dp(76)).apply { marginEnd=dp(10) })
        val copy=LinearLayout(context).apply { orientation=VERTICAL; setPadding(dp(4),dp(6),dp(4),dp(2)) }
        copy.addView(StudioDesign.label(context,name,14f,bold=true).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END },LayoutParams(-1,dp(23)))
        val date=java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(project.updated))
        copy.addView(StudioDesign.label(context,"${project.state.clips.size} ${if(project.state.photoEditor==true) "photos" else "clips"} · $date",11f,secondary=true).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END }.also { detailViews.add(it) },LayoutParams(-1,dp(21)))
        addView(copy,if(gridCard) LayoutParams(-1,-2) else LayoutParams(0,-2,1f))
        if(!gridCard) addView(more,LayoutParams(dp(48),dp(48)))
        isFocusable=true; contentDescription="Open project: $name"; setOnClickListener { open() }
    }
    private fun rename() {
        val field=EditText(context).apply { setText(ProjectPresentation.name(context,project.id,project.name)); setSelectAllOnFocus(true); hint="Project name"; maxLines=1; filters=arrayOf(android.text.InputFilter.LengthFilter(80)) }
        val dialog=AppDialog(context).setTitle("Rename project").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Save",null).show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(field.text.toString().isBlank()) field.error="Enter a project name" else { ProjectPresentation.rename(context,project.id,field.text.toString()); dialog.dismiss(); render(); changed() }
        }
    }
    private fun progressDialog() {
        val body=LinearLayout(context).apply { orientation=VERTICAL; setPadding(dp(20),0,dp(20),0) }
        val value=StudioDesign.label(context,"${ProjectPresentation.progress(context,project.id)}%",18f); body.addView(value)
        body.addView(StudioDesign.label(context,"Set your own progress. Projects stay in your library at every percentage.",13f,secondary=true))
        val bar=SeekBar(context).apply { max=100; progress=ProjectPresentation.progress(context,project.id); contentDescription="Project progress"; setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(b: SeekBar?,v: Int,user: Boolean) { value.text="$v%" }; override fun onStartTrackingTouch(b: SeekBar?) {}; override fun onStopTrackingTouch(b: SeekBar?) {} }) }; body.addView(bar)
        AppDialog(context).setTitle("Your progress").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_ -> ProjectPresentation.progress(context,project.id,bar.progress); render(); changed() }.show()
    }
}

class BottomNavigation(context: Context,selected: String,navigate: (String)->Unit,homeAccent: Int?): LinearLayout(context) {
    constructor(context: Context,selected: String,navigate: (String)->Unit): this(context,selected,navigate,null)
    init {
        setPadding(Ui.dp(context,8),Ui.dp(context,8),Ui.dp(context,8),Ui.dp(context,8)); setBackgroundColor(StudioDesign.surface(context))
        listOf(Triple("Home","home",R.drawable.ph_house),Triple("Projects","projects",R.drawable.ph_folder_simple),Triple("Settings","settings",R.drawable.ph_gear_six)).forEach { (name,key,icon) ->
            val active=selected==key
            val selectedColor=homeAccent ?: StudioDesign.accent(context)
            val item=LinearLayout(context).apply {
                orientation=VERTICAL;gravity=Gravity.CENTER;isSelected=active;isFocusable=true;contentDescription=Ui.t(context,name);setOnClickListener { navigate(key) }
                if(active) background=StudioDesign.shape(context,if(AppPreferences.theme(context)=="gold") Color.rgb(255,247,229) else StudioDesign.raised(context),20)
            }
            item.addView(ImageView(context).apply { setImageResource(icon); imageTintList=ColorStateList.valueOf(if(active) selectedColor else StudioDesign.muted(context)); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LayoutParams(Ui.dp(context,22),Ui.dp(context,22)))
            item.addView(StudioDesign.label(context,name,11f,bold=active).apply { gravity=Gravity.CENTER; if(homeAccent!=null) { tag="media"; setTextColor(if(active) selectedColor else StudioDesign.muted(context)) }; setPadding(0,Ui.dp(context,5),0,0) })
            addView(item,LayoutParams(0,Ui.dp(context,56),1f))
        }
    }
}
