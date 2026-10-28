package com.lumen.studio

import java.io.Serializable
import java.util.UUID

data class VisualLayer(val id: String=UUID.randomUUID().toString(),val kind: String,val name: String,val startMs: Long=0,val endMs: Long=4000,
    val frameScale: Float?=null,val frameX: Float?=null,val frameY: Float?=null,val frameWidth: Float?=null,val frameColor: Int?=null,val frameStyle: String?=null): Serializable { companion object { private const val serialVersionUID=1L } }
data class TextLayer(val id: String = UUID.randomUUID().toString(), val text: String, val size: Float = 28f,
    val x: Float = 0f, val y: Float = 0f, val color: Int = -1,
    val startMs: Long = 0, val endMs: Long = 0, val animation: String? = "None", val font: String? = "Sans", val style: String? = "Bold", val animationOffsetMs: Long = 0,
    val alignment: String? = "Center", val rotation: Float = 0f, val opacity: Float? = 1f,
    val letterSpacing: Float = 0f, val lineSpacing: Float = 0f, val backgroundColor: Int = 0,
    val outlineWidth: Float = 0f, val shadow: Boolean? = true,
    val bold: Boolean? = null, val italic: Boolean? = null, val underline: Boolean? = null, val backgroundPadding: Float? = null, val shadowStrength: Float? = null) : Serializable { companion object { private const val serialVersionUID = 1L } }
data class ImageLayer(val id: String = UUID.randomUUID().toString(), val uri: String, val scale: Float = .3f,
    val x: Float = .6f, val y: Float = .6f, val opacity: Float = 1f, val startMs: Long = 0, val endMs: Long = 0, val flip: Boolean = false, val rotation: Float = 0f) : Serializable { companion object { private const val serialVersionUID = 1L } }
data class SoundLayer(val id: String = UUID.randomUUID().toString(), val uri: String, val name: String,
    val volume: Float = 1f, val startMs: Long = 0, val sourceStartMs: Long = 0, val endMs: Long = 0, val speed: Float = 1f, val fadeInMs: Long = 0, val fadeOutMs: Long = 0, val sourceEndMs: Long = 0) : Serializable { companion object { private const val serialVersionUID = 1L } }

data class EditorClip(
    val uri: String, val name: String, val video: Boolean,
    val startMs: Int = 0, val endMs: Int = 0,
    val rotation: Float = 0f, val zoom: Float = 1f, val flip: Boolean = false,
    val opacity: Float = 1f, val brightness: Float = 0f, val contrast: Float = 1f,
    val saturation: Float = 1f, val speed: Float = 1f, val muted: Boolean = false,
    val catalogAdjustments: Map<String,Float>? = null,
    val filter: String = "Original", val effect: String = "None", val reverse: Boolean = false,
    val id: String = UUID.randomUUID().toString(), val sourceDurationMs: Long = 0,
    val imageDurationMs: Long = 4000, val texts: List<TextLayer> = emptyList(),
    val overlays: List<ImageLayer> = emptyList(), val sounds: List<SoundLayer> = emptyList(), val schema: Int = 3, val speedCurve: List<Float>? = emptyList(),
    val audioVolume: Float = 1f, val audioFadeInMs: Long = 0, val audioFadeOutMs: Long = 0, val hasAudio: Boolean? = null,
    val flipVertical: Boolean = false, val filterIntensity: Float = 1f, val exposure: Float = 0f, val temperature: Float = 0f, val tint: Float = 0f,
    val cropLeft: Float = -1f, val cropRight: Float = 1f, val cropBottom: Float = -1f, val cropTop: Float = 1f,
    val speedPositions: List<Float>? = null, val visuals: List<VisualLayer>? = emptyList(), val curveImageDurationMs: Long? = null, val motion: String? = null, val motionDurationMs: Long? = 4000, val animations: ClipAnimationSettings? = null, val photoPanX: Float? = null, val photoPanY: Float? = null, val cropRatio: String? = null
) : Serializable { companion object { private const val serialVersionUID = 1L } }

data class EditorState(
    val clips: List<EditorClip>, val active: Int = 0,
    val title: String = "", val titleSize: Float = 28f,
    val overlay: String? = null, val audio: String? = null, val audioVolume: Float = 1f, val audioName: String = "Added soundtrack",
    val ratio: String = "Fit", val background: Int = 0xFF0B0D10.toInt(),
    val projectSounds: List<SoundLayer>? = emptyList(), val projectTexts: List<TextLayer>? = emptyList(), val projectOverlays: List<ImageLayer>? = emptyList(), val projectVisuals: List<VisualLayer>? = emptyList(),
    val projectSpeedPositions: List<Float>? = null, val projectSpeedValues: List<Float>? = null, val musicMuted: Boolean? = false, val photoEditor: Boolean? = null, val photoLayout: String? = null, val photoGap: Int? = 12, val photoCorner: Int? = 12,
    val photoCanvasStyle: String? = null, val photoCanvasFit: String? = null, val photoCanvasColorEnd: Int? = null
) : Serializable { companion object { private const val serialVersionUID = 1L } }

fun EditorState.normalized(): EditorState = copy(projectVisuals=projectVisuals.orEmpty(),projectSounds=projectSounds.orEmpty().map { it.copy(speed=if(it.speed>0) it.speed else 1f) },projectTexts=projectTexts.orEmpty().map { it.copy(font=it.font ?: "Sans",style=it.style ?: "Bold") },projectOverlays=projectOverlays.orEmpty(),clips = clips.map { c ->
    if(c.schema > 0) c.copy(speedCurve=c.speedCurve ?: emptyList(),schema=3,
        audioVolume=if(c.schema<3) 1f else c.audioVolume, filterIntensity=if(c.schema<3) 1f else c.filterIntensity,
        cropLeft=if(c.schema<3) -1f else c.cropLeft,cropRight=if(c.schema<3) 1f else c.cropRight,cropBottom=if(c.schema<3) -1f else c.cropBottom,cropTop=if(c.schema<3) 1f else c.cropTop,
        texts=c.texts.map { it.copy(font=it.font ?: "Sans",style=it.style ?: "Bold") },sounds=c.sounds.map { it.copy(speed=if(it.speed>0) it.speed else 1f) }) else c.copy(id = UUID.randomUUID().toString(), imageDurationMs = 5000,
        texts = if(title.isEmpty()) emptyList() else listOf(TextLayer(text = title,size = titleSize)),
        overlays = overlay?.let { listOf(ImageLayer(uri = it)) } ?: emptyList(),
        sounds = audio?.let { listOf(SoundLayer(uri = it,name = audioName,volume = audioVolume)) } ?: emptyList(),schema = 3,speedCurve=emptyList())
},active = if(clips.isEmpty()) 0 else active.coerceIn(0,clips.lastIndex))

object ClipEdits {
    fun sourceLength(c: EditorClip) = ((if(c.endMs>0) c.endMs.toLong() else c.sourceDurationMs)-c.startMs).coerceAtLeast(1).toDouble()
    fun positions(c: EditorClip): List<Float> {
        val curve=c.speedCurve.orEmpty()
        val stored=c.speedPositions.orEmpty()
        return if(stored.size==curve.size && stored.size>=2 && stored.first()==0f && stored.last()==1f && stored.zipWithNext().all { it.first<it.second }) stored else curve.indices.map { it.toFloat()/(curve.size-1).coerceAtLeast(1) }
    }
    fun speedAt(c: EditorClip,fraction: Double): Float {
        val curve=c.speedCurve.orEmpty(); if(curve.size<2) return c.speed.coerceIn(.25f,4f)
        val points=positions(c); val x=fraction.coerceIn(0.0,1.0).toFloat()
        val i=points.indexOfLast { it<=x }.coerceIn(0,points.size-2)
        return (curve[i]+(curve[i+1]-curve[i])*((x-points[i])/(points[i+1]-points[i]))).coerceIn(.25f,4f)
    }
    fun outputPosition(c: EditorClip,source: Double): Double {
        val length=sourceLength(c); val limit=source.coerceIn(0.0,length)
        val curve=c.speedCurve.orEmpty(); if(curve.size<2) return limit/c.speed.coerceIn(.25f,4f)
        val points=positions(c); var total=0.0
        for(i in 0 until curve.lastIndex) {
            val segment=(points[i+1]-points[i])*length
            val span=(limit-points[i]*length).coerceIn(0.0,segment); if(span<=0) break
            val a=curve[i].coerceIn(.25f,4f).toDouble(); val b=curve[i+1].coerceIn(.25f,4f).toDouble()
            val slope=(b-a)/segment
            total+=if(kotlin.math.abs(slope)<1e-9) span/a else kotlin.math.ln((a+slope*span)/a)/slope
        }
        return total
    }
    fun sourcePosition(c: EditorClip,output: Long): Int {
        var low=0.0; var high=sourceLength(c)
        repeat(24) { val middle=(low+high)/2; if(outputPosition(c,middle)<output) low=middle else high=middle }
        return c.startMs+((low+high)/2).toInt()
    }
    private fun sliceCurve(c: EditorClip,start: Double,end: Double): List<Float> =
        if(c.speedCurve.orEmpty().size<2) emptyList() else (0..20).map { speedAt(c,start+(end-start)*it/20) }

    fun duration(c: EditorClip): Long = if(!c.video) c.imageDurationMs.coerceAtLeast(1)
        else outputPosition(c,sourceLength(c)).toLong().coerceAtLeast(1)
    fun trim(c: EditorClip,start: Int,end: Int): EditorClip {
        require(c.video && start >= 0 && end > start && end <= c.sourceDurationMs) { "Invalid trim range" }
        val oldLength=sourceLength(c)
        return c.copy(startMs = start,endMs = end,speedPositions=null,speedCurve=sliceCurve(c,(start-c.startMs)/oldLength,(end-c.startMs)/oldLength))
    }
    fun split(state: EditorState,sourceMs: Int): EditorState {
        val c = state.clips[state.active]
        val sourceEnd = if(c.endMs > 0) c.endMs.toLong() else c.sourceDurationMs
        require(c.video && sourceMs > c.startMs && sourceMs.toLong() < sourceEnd)
        val offset = outputPosition(c,(sourceMs-c.startMs).toDouble()).toLong()
        val leftSounds = c.sounds.filter { it.startMs < offset }.map {
            it.copy(endMs = minOf(if(it.endMs > 0) it.endMs else Long.MAX_VALUE,offset))
        }
        val rightSounds = c.sounds.filter { it.endMs == 0L || it.endMs > offset }.map {
            it.copy(startMs = (it.startMs-offset).coerceAtLeast(0),sourceStartMs = it.sourceStartMs+((offset-it.startMs).coerceAtLeast(0)*it.speed).toLong(),endMs = if(it.endMs == 0L) 0 else (it.endMs-offset).coerceAtLeast(0))
        }
        val clips = state.clips.toMutableList()
        clips[state.active] = c.copy(endMs = sourceMs,texts=c.texts.filter { it.startMs<offset }.map { it.copy(endMs=minOf(if(it.endMs>0) it.endMs else offset,offset)) },audioFadeOutMs=0,sounds = leftSounds.map { it.copy(fadeOutMs=0) },speedPositions=null,speedCurve=sliceCurve(c,0.0,(sourceMs-c.startMs)/sourceLength(c)))
        clips.add(state.active+1,c.copy(id = UUID.randomUUID().toString(),startMs = sourceMs,texts=c.texts.filter { it.endMs==0L || it.endMs>offset }.map { it.copy(startMs=(it.startMs-offset).coerceAtLeast(0),endMs=if(it.endMs==0L) 0 else it.endMs-offset) },audioFadeInMs=0,sounds = rightSounds.map { it.copy(fadeInMs=0) },speedPositions=null,speedCurve=sliceCurve(c,(sourceMs-c.startMs)/sourceLength(c),1.0)))
        return state.copy(clips = clips)
    }
    fun duplicate(state: EditorState): EditorState {
        val c=state.clips[state.active]
        val copy=c.copy(id=UUID.randomUUID().toString(),texts=c.texts.map { it.copy(id=UUID.randomUUID().toString()) },overlays=c.overlays.map { it.copy(id=UUID.randomUUID().toString()) },sounds=c.sounds.map { it.copy(id=UUID.randomUUID().toString()) })
        val clips=state.clips.toMutableList(); clips.add(state.active+1,copy)
        return state.copy(clips=clips,active=state.active+1)
    }
    fun delete(state: EditorState): EditorState {
        val clips = state.clips.filterIndexed { index,_ -> index != state.active }
        return state.copy(clips = clips,active = if(clips.isEmpty()) 0 else state.active.coerceAtMost(clips.lastIndex))
    }
    fun update(state: EditorState,edit: (EditorClip) -> EditorClip) = state.copy(clips = state.clips.mapIndexed { index,c -> if(index == state.active) edit(c) else c })
    fun move(state: EditorState,to: Int): EditorState {
        require(to in state.clips.indices)
        val clips = state.clips.toMutableList(); val clip = clips.removeAt(state.active); clips.add(to,clip)
        return state.copy(clips = clips,active = to)
    }
}

/** Immutable snapshots keep each undo/redo operation atomic. */
class EditHistory(initial: EditorState) {
    private var grouping=false
    private var groupChanged=false
    var state = initial
        private set
    val undo = mutableListOf<EditorState>()
    val redo = mutableListOf<EditorState>()
    fun beginGroup() { grouping=true; groupChanged=false }
    fun endGroup() { grouping=false; groupChanged=false }
    fun adoptPrepared(next: EditorState) { state=next }
    fun change(next: EditorState) {
        if (next == state) return
        if(!grouping || !groupChanged) undo.add(state)
        if(grouping) groupChanged=true
        if (undo.size > 50) undo.removeAt(0)
        redo.clear(); state = next
    }
    fun undo(): Boolean {
        endGroup()
        if (undo.isEmpty()) return false
        redo.add(state); state = undo.removeAt(undo.lastIndex); return true
    }
    fun redo(): Boolean {
        endGroup()
        if (redo.isEmpty()) return false
        undo.add(state); state = redo.removeAt(redo.lastIndex); return true
    }
    fun select(index: Int) { state = state.copy(active = if(state.clips.isEmpty()) 0 else index.coerceIn(0, state.clips.lastIndex)) }
}

/** Independent clip entry, exit and continuous motion. Serialized with the draft. */
data class ClipAnimationSettings(val incoming: String?=null,val outgoing: String?=null,val combo: String?=null,val inMs: Long=800,val outMs: Long=800,val periodMs: Long=4000): java.io.Serializable { companion object { private const val serialVersionUID=1L } }

/** Keep the displayed seconds and renderer timing identical, including after a clip is split. */
fun ClipAnimationSettings.fittedTo(durationMs: Long): ClipAnimationSettings {
    val length=durationMs.coerceAtLeast(1)
    var entry=inMs.coerceIn(1,length); var exit=outMs.coerceIn(1,length)
    if(incoming!=null && outgoing!=null && entry+exit>length && length>1) {
        entry=(length.toDouble()*entry/(entry+exit)).toLong().coerceIn(1,length-1)
        exit=length-entry
    }
    return copy(inMs=entry,outMs=exit,periodMs=periodMs.coerceIn(1,length))
}
