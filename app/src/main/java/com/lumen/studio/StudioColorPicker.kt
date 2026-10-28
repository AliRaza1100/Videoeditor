package com.lumen.studio

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*

/** Reusable selected swatches plus a live custom HEX preview. */
class StudioColorPicker(context: Context,initial: Int,private val changed: (Int)->Unit): LinearLayout(context) {
    private var selected=initial
    private val swatches=mutableListOf<Pair<Int,View>>()
    private val hex=EditText(context).apply { setSingleLine();hint="#RRGGBB or #AARRGGBB";textSize=13f;setTextColor(StudioDesign.text(context));setHintTextColor(StudioDesign.muted(context));filters=arrayOf(android.text.InputFilter.LengthFilter(9));contentDescription="Custom color hex code" }
    private val preview=TextView(context).apply { tag="media";gravity=Gravity.CENTER;setTextColor(StudioDesign.text(context));textSize=18f;contentDescription="Custom color preview" }
    init {
        tag="media";orientation=VERTICAL
        val row=LinearLayout(context)
        intArrayOf(Color.TRANSPARENT,Color.WHITE,Color.BLACK,0xFFE83D55.toInt(),0xFFFF852F.toInt(),0xFFFFD84A.toInt(),0xFF31C77B.toInt(),0xFF3A88FF.toInt(),0xFFA67AFF.toInt(),0xFFFF89C4.toInt(),0xFF00BCD4.toInt()).forEach { color ->
            val swatch=TextView(context).apply { tag="media";text=if(color==0) "∅" else "";gravity=Gravity.CENTER;setTextColor(StudioDesign.text(context));background=StudioDesign.shape(context,if(color==0) StudioDesign.raised(context) else color,12,true);contentDescription=if(color==0) "No color" else "Color ${format(color)}";isFocusable=true;setOnClickListener { choose(color) } }
            swatches.add(color to swatch);row.addView(swatch,LayoutParams(Ui.dp(context,48),Ui.dp(context,48)).apply { marginEnd=Ui.dp(context,8) })
        }
        addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled=false;addView(row) })
        addView(StudioDesign.label(context,"Custom color",13f,bold=true).apply { setPadding(0,Ui.dp(context,14),0,Ui.dp(context,8)) })
        val custom=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        custom.addView(preview,LayoutParams(Ui.dp(context,44),Ui.dp(context,44)).apply { marginEnd=Ui.dp(context,8) })
        val mixer=LinearLayout(context).apply { orientation=VERTICAL;visibility=View.GONE }
        listOf("Red","Green","Blue").forEachIndexed { channel,title ->
            val line=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
            line.addView(StudioDesign.label(context,title,12f),LayoutParams(Ui.dp(context,48),-2))
            line.addView(SeekBar(context).apply { max=255;progress=when(channel) { 0 -> Color.red(selected);1 -> Color.green(selected);else -> Color.blue(selected) };contentDescription="$title color channel"
                setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?,value: Int,user: Boolean) { if(user) { val current=runCatching { Color.parseColor(hex.text.toString()) }.getOrDefault(selected);val rgb=intArrayOf(Color.red(current),Color.green(current),Color.blue(current));rgb[channel]=value;hex.setText(format(Color.rgb(rgb[0],rgb[1],rgb[2]))) } }
                    override fun onStartTrackingTouch(bar: SeekBar?) {} override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            },LayoutParams(0,Ui.dp(context,48),1f));mixer.addView(line)
        }
        custom.addView(studioIcon(context,R.drawable.ph_palette,"Open RGB color mixer") { mixer.visibility=if(mixer.visibility==View.VISIBLE) View.GONE else View.VISIBLE },LayoutParams(Ui.dp(context,48),Ui.dp(context,48)))
        custom.addView(hex,LayoutParams(0,Ui.dp(context,48),1f))
        custom.addView(StudioDesign.button(context,"Apply color") { val color=runCatching { Color.parseColor(hex.text.toString()) }.getOrNull();if(color==null) hex.error=Ui.t(context,"Enter #RRGGBB or #AARRGGBB") else choose(color) },LayoutParams(-2,Ui.dp(context,48)).apply { marginStart=Ui.dp(context,8) })
        addView(custom);addView(mixer)
        hex.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { runCatching { Color.parseColor(s.toString()) }.getOrNull()?.let { preview.background=StudioDesign.shape(context,it,10,true) } }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        refresh()
    }
    private fun format(color: Int)=String.format(java.util.Locale.US,if(Color.alpha(color)==255) "#%06X" else "#%08X",if(Color.alpha(color)==255) color and 0xFFFFFF else color)
    private fun choose(color: Int) { selected=color;refresh();changed(color) }
    private fun refresh() { swatches.forEach { (color,view) -> selectStudioCard(view,color==selected) };hex.setText(format(selected));preview.text=if(selected==0) "∅" else "";preview.background=StudioDesign.shape(context,if(selected==0) StudioDesign.raised(context) else selected,10,true) }
}
