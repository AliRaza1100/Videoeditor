package com.lumen.studio

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.*

/** Device-private library with compact controls and a real, filter-aware empty state. */
class ProjectsView(context: Context,private val navigate: (String)->Unit,private val savedOnly: Boolean): LinearLayout(context) {
    constructor(context: Context,navigate: (String)->Unit): this(context,navigate,false)
    private val ui=ReferenceSurfaces(context)
    private fun dp(n: Int)=ui.dp(n)
    private val summary=StudioDesign.label(context,"",13f,secondary=true)
    private val column=LinearLayout(context).apply { orientation=VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(88)) }
    private val empty=LinearLayout(context).apply { orientation=VERTICAL;gravity=Gravity.CENTER;setPadding(dp(24),dp(16),dp(24),dp(20)) }
    private val newProject=StudioDesign.button(context,"＋ New project",primary=true) { navigate("new_video") }
    private var filter="All"
    private var sort=0
    private var grid=true
    private val chipViews=mutableListOf<Button>()
    init {
        orientation=VERTICAL;setBackgroundColor(StudioDesign.background(context))
        addView(StudioDesign.label(context,if(savedOnly) "Drafts" else "Projects",30f,bold=true).apply { setPaddingRelative(dp(20),dp(22),dp(20),dp(4)) })
        addView(summary.apply { setPaddingRelative(dp(20),0,dp(20),dp(20)) })
        val chips=LinearLayout(context).apply { setPadding(dp(20),dp(3),dp(20),dp(12)) }
        listOf("All","Recent","Photos","Videos").forEach { name ->
            val chip=StudioDesign.button(context,name) { filter=name;updateChips();render() }.apply {
                contentDescription=Ui.t(context,name);tag=name
                setPadding(dp(18),dp(4),dp(18),dp(4))
            }
            chipViews.add(chip);chips.addView(chip,LayoutParams(-2,dp(46)).apply { marginEnd=dp(8) })
        }
        addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled=false;addView(chips) },LayoutParams(-1,-2))
        val controls=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(20),dp(6),dp(20),dp(14)) }
        val sortButton=StudioDesign.button(context,"${Ui.t(context,"Sort")}: ${Ui.t(context,"Newest")}  ⌄") {}.apply { background=ui.card(radius=24);contentDescription=Ui.t(context,"Sort projects") }
        sortButton.setOnClickListener {
            val names=arrayOf("Newest","Oldest","Name","Duration")
            AppDialog(context).setTitle("Sort projects").setSingleChoiceItems(names,sort) { dialog,index ->
                sort=index;sortButton.text="${Ui.t(context,"Sort")}: ${Ui.t(context,names[index])}  ⌄";render();dialog.dismiss()
            }.show()
        }
        controls.addView(sortButton,LayoutParams(-2,dp(44)))
        controls.addView(View(context),LayoutParams(0,1,1f))
        controls.addView(StudioDesign.button(context,"☷  List") {
            grid=!grid
            (controls.getChildAt(2) as TextView).text=if(grid) "☷  ${Ui.t(context,"List")}" else "▦  ${Ui.t(context,"Grid")}"
            render()
        }.apply { background=ui.card(radius=12);contentDescription=Ui.t(context,"Change project layout") },LayoutParams(-2,dp(44)).apply { marginStart=dp(8) })
        addView(controls)
        val frame=FrameLayout(context)
        frame.addView(ScrollView(context).apply { isVerticalScrollBarEnabled=false;addView(column) },FrameLayout.LayoutParams(-1,-1))
        frame.addView(ScrollView(context).apply { isFillViewport=true;isVerticalScrollBarEnabled=false;addView(empty) },FrameLayout.LayoutParams(-1,-1))
        frame.addView(newProject,FrameLayout.LayoutParams(-2,dp(50),Gravity.END or Gravity.BOTTOM).apply { setMargins(dp(16),dp(8),dp(16),dp(16)) })
        addView(frame,LayoutParams(-1,0,1f))
        addView(AdBannerSlot(context,"projects_banner"),LayoutParams(-1,-2))
        addView(BottomNavigation(context,"projects",navigate));updateChips();render()
    }
    private fun updateChips() {
        chipViews.forEach { chip ->
            val active=chip.tag==filter;chip.isSelected=active
            chip.background=ui.shape(if(active) { if(ui.gold) 0xFFFFDD65.toInt() else StudioDesign.raised(context) } else ui.soft,18,if(active && !ui.gold) ui.accent else null)
            chip.setTextColor(StudioDesign.text(context))
        }
    }
    private fun render() {
        column.removeAllViews();empty.removeAllViews()
        val all=ProjectStore.list(context).filter { !savedOnly || ProjectPresentation.progress(context,it.id)<100 }
        val videos=all.count { it.state.photoEditor!=true };val photos=all.size-videos
        summary.text=Ui.t(context,"{0} video projects · {1} photo projects").replace("{0}",videos.toString()).replace("{1}",photos.toString())
        val matching=all.filter { when(filter) { "Photos" -> it.state.photoEditor==true;"Videos" -> it.state.photoEditor!=true;"Recent" -> it.updated>=System.currentTimeMillis()-7*86400000L;else -> true } }
        val projects=when(sort) { 1 -> matching.sortedBy { it.updated };2 -> matching.sortedBy { ProjectPresentation.name(context,it.id,it.name).lowercase() };3 -> matching.sortedByDescending { it.state.clips.sumOf(ClipEdits::duration) };else -> matching.sortedByDescending { it.updated } }
        empty.visibility=if(projects.isEmpty()) View.VISIBLE else View.GONE
        (empty.parent as? View)?.visibility=empty.visibility
        newProject.visibility=if(projects.isEmpty()) View.GONE else View.VISIBLE
        if(projects.isEmpty()) {
            empty.addView(ProjectEmptyArt(context),LayoutParams(dp(240),dp(185)))
            empty.addView(StudioDesign.label(context,if(all.isEmpty()) "No projects yet" else "No matching projects",23f,bold=true).apply { gravity=Gravity.CENTER;setPadding(0,dp(12),0,dp(10)) })
            empty.addView(StudioDesign.label(context,if(all.isEmpty()) "Create a project to start your next story." else "Create a project or change your filter to see your files.",15f,secondary=true).apply { gravity=Gravity.CENTER },LayoutParams(-1,-2))
            empty.addView(StudioDesign.button(context,"＋ New project",primary=true) { navigate("new_video") },LayoutParams(-2,dp(56)).apply { topMargin=dp(24);gravity=Gravity.CENTER_HORIZONTAL })
            return
        }
        if(filter=="Recent") column.addView(StudioDesign.label(context,"Edited in the last 7 days",12f,secondary=true).apply { setPadding(0,0,0,dp(8)) })
        if(grid) {
            val columns=when { resources.configuration.screenWidthDp>=1000 -> 4;resources.configuration.screenWidthDp>=650 -> 3;else -> 2 }
            projects.chunked(columns).forEach { group ->
                val row=LinearLayout(context)
                for(i in 0 until columns) {
                    val project=group.getOrNull(i)
                    val card=if(project==null) View(context) else ProjectRow(context,project,{ render() },compact=true,gridCard=true) { navigate("open:${project.id}") }
                    row.addView(card,LayoutParams(0,-2,1f).apply { if(i<columns-1) marginEnd=dp(8) })
                }
                column.addView(row,LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
            }
        } else projects.forEach { project -> column.addView(ProjectRow(context,project,{ render() },compact=true,gridCard=false) { navigate("open:${project.id}") }) }
    }
}
