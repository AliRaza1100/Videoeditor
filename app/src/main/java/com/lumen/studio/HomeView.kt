package com.lumen.studio

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*

/** Reference-led home: direct imports, real local thumbnails, and compact navigation. */
class HomeView(context: Context,private val onAction: (String)->Unit = {}): LinearLayout(context) {
    private fun dp(n: Int)=Ui.dp(context,n)
    private val lime=when(AppPreferences.theme(context)) { "gold" -> StudioDesign.primary(context); "violet" -> 0xFFDDCFFF.toInt(); else -> 0xFFB6FF4D.toInt() }
    private val accent=when(AppPreferences.theme(context)) { "gold","violet" -> StudioDesign.accent(context); "light" -> 0xFF365F00.toInt(); else -> lime }
    private fun label(value: String,size: Float,bold: Boolean=false)=StudioDesign.label(context,value,size,bold=bold)
    private fun icon(resource: Int,color: Int)=ImageView(context).apply {
        setImageResource(resource); imageTintList=ColorStateList.valueOf(color)
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    init {
        orientation=VERTICAL; setBackgroundColor(StudioDesign.background(context))
        val header=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(16),dp(8),dp(12),dp(8)) }
        header.addView(StudioMark(context),LayoutParams(dp(28),dp(28)))
        header.addView(label("Lumen Studio",20f,true).apply { setPadding(dp(10),0,0,0) },LayoutParams(0,-2,1f))
        header.addView(studioIcon(context,R.drawable.ph_bell,"Notifications") { onAction("notifications") }.apply { background=null },LayoutParams(dp(48),dp(48)))
        addView(header,LayoutParams(-1,dp(64)))
        val content=LinearLayout(context).apply { orientation=VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(20)) }
        val hero=LinearLayout(context).apply {
            tag="media"; gravity=Gravity.CENTER_VERTICAL; minimumHeight=dp(88)
            background=StudioDesign.shape(context,lime,18); setPadding(dp(16),dp(16),dp(16),dp(16))
            isFocusable=true; contentDescription="New Video Project"; setOnClickListener { onAction("new_video") }
        }
        hero.addView(TextView(context).apply { text="⊕"; textSize=28f; setTextColor(0xFF101410.toInt()); gravity=Gravity.CENTER; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LayoutParams(dp(32),dp(40)))
        val heroCopy=LinearLayout(context).apply { orientation=VERTICAL; setPadding(dp(12),0,dp(8),0) }
        heroCopy.addView(label("New Project",21f,true).apply { setTextColor(0xFF101410.toInt()) })
        heroCopy.addView(label("Start editing your videos and photos",12f).apply { setTextColor(0xFF223016.toInt()); setPadding(0,dp(6),0,0) })
        hero.addView(heroCopy,LayoutParams(0,-2,1f))
        hero.addView(TextView(context).apply { text="↗"; textSize=22f; setTextColor(0xFF101410.toInt()); gravity=Gravity.CENTER; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LayoutParams(dp(24),dp(40)))
        content.addView(hero,LayoutParams(-1,-2))
        val editors=LinearLayout(context)
        fun editor(title: String,description: String,resource: Int,action: String)=LinearLayout(context).apply {
            orientation=VERTICAL; gravity=Gravity.CENTER; minimumHeight=dp(80)
            background=StudioDesign.shape(context,StudioDesign.raised(context),14)
            setPadding(dp(8),dp(12),dp(8),dp(12)); isFocusable=true; contentDescription=description
            setOnClickListener { onAction(action) }
            addView(icon(resource,accent),LayoutParams(dp(24),dp(24)))
            addView(label(title,14f,true).apply { gravity=Gravity.CENTER; setPadding(0,dp(8),0,0) })
        }
        editors.addView(editor("Video Editor","Video Editor",android.R.drawable.presence_video_online,"new_video"),LayoutParams(0,-1,1f).apply { marginEnd=dp(6) })
        editors.addView(editor("Photo Editor","Edit Photo. Crop, text & filters",R.drawable.ph_image,"edit_photo"),LayoutParams(0,-1,1f).apply { marginStart=dp(6) })
        content.addView(editors,LayoutParams(-1,dp(88)).apply { topMargin=dp(12) })
        val recent=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        recent.addView(label("Recent Projects",18f,true),LayoutParams(0,-2,1f))
        recent.addView(StudioDesign.button(context,"See All") { onAction("projects") }.apply { tag="media"; setTextColor(accent); background=null; textSize=12f; contentDescription="View all projects" },LayoutParams(-2,dp(48)))
        content.addView(recent,LayoutParams(-1,-2).apply { topMargin=dp(8) })
        val projects=ProjectStore.list(context).take(3)
        if(projects.isEmpty()) {
            val empty=LinearLayout(context).apply { orientation=VERTICAL; gravity=Gravity.CENTER; setPadding(dp(20),dp(24),dp(20),dp(24)); background=StudioDesign.shape(context,StudioDesign.raised(context),14) }
            empty.addView(icon(R.drawable.ph_folder_simple,accent),LayoutParams(dp(32),dp(32)))
            empty.addView(label("Your projects start here",16f,true).apply { gravity=Gravity.CENTER; setPadding(0,dp(12),0,dp(6)) })
            empty.addView(StudioDesign.label(context,"Create a project to see your recent edits.",13f,secondary=true).apply { gravity=Gravity.CENTER })
            content.addView(empty,LayoutParams(-1,-2))
        } else {
            val cards=LinearLayout(context)
            val cardWidth=dp(((resources.configuration.screenWidthDp-44)/2).coerceAtLeast(132))
            projects.forEachIndexed { index,project ->
                val name=ProjectPresentation.name(context,project.id,project.name)
                val card=LinearLayout(context).apply {
                    orientation=VERTICAL; background=StudioDesign.shape(context,StudioDesign.raised(context),12)
                    clipToOutline=true; outlineProvider=object: ViewOutlineProvider() { override fun getOutline(view: View,outline: Outline) { outline.setRoundRect(0,0,view.width,view.height,dp(12).toFloat()) } }
                    isFocusable=true; contentDescription="Open project: $name"; setOnClickListener { onAction("open:${project.id}") }
                }
                val thumb=ImageView(context).apply { tag="media"; scaleType=ImageView.ScaleType.CENTER_CROP; setBackgroundColor(StudioDesign.raised(context)); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
                project.state.clips.firstOrNull()?.let { PreviewImages.load(context,it.uri,it.video,thumb) }
                card.addView(thumb,LayoutParams(-1,cardWidth*3/4))
                val copy=LinearLayout(context).apply { orientation=VERTICAL; setPadding(dp(10),dp(10),dp(10),dp(10)) }
                copy.addView(label(name,13f,true).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END })
                val seconds=project.state.clips.sumOf(ClipEdits::duration)/1000
                val detail=if(project.state.photoEditor==true) "Draft · Photo" else "Draft · %02d:%02d".format(seconds/60,seconds%60)
                copy.addView(StudioDesign.label(context,detail,12f,secondary=true).apply { maxLines=1; setPadding(0,dp(6),0,0) })
                card.addView(copy); cards.addView(card,LayoutParams(cardWidth,-2).apply { if(index<projects.lastIndex) marginEnd=dp(12) })
            }
            content.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled=false; contentDescription="Recent projects. Swipe for more."; addView(cards) },LayoutParams(-1,-2))
        }
        val all=LinearLayout(context).apply {
            gravity=Gravity.CENTER_VERTICAL; setPadding(dp(16),dp(8),dp(12),dp(8)); minimumHeight=dp(56)
            background=StudioDesign.shape(context,StudioDesign.raised(context),12); isFocusable=true; contentDescription="All Projects"; setOnClickListener { onAction("projects") }
        }
        all.addView(icon(R.drawable.ph_folder_simple,accent),LayoutParams(dp(22),dp(22)))
        all.addView(label("All Projects",15f,true).apply { setPadding(dp(12),0,0,0) },LayoutParams(0,-2,1f))
        all.addView(label("›",26f),LayoutParams(dp(24),dp(36)))
        content.addView(all,LayoutParams(-1,-2).apply { topMargin=dp(12) })
        addView(ScrollView(context).apply { isFillViewport=true; isVerticalScrollBarEnabled=false; addView(content) },LayoutParams(-1,0,1f))
        addView(View(context).apply { setBackgroundColor(StudioDesign.border(context)) },LayoutParams(-1,dp(1)))
        addView(AdBannerSlot(context,"home_banner"),LayoutParams(-1,-2))
        addView(BottomNavigation(context,"home",onAction,homeAccent=accent))
    }
}
