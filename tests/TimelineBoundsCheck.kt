package com.lumen.studio

fun main() {
    val source=EditorClip("video","Twenty seconds",true,sourceDurationMs=20000)
    val audio=SoundLayer(uri="audio",name="Twenty seconds",endMs=20000)
    val original=EditorState(listOf(source),projectSounds=listOf(audio),projectTexts=listOf(TextLayer(text="Late caption",startMs=17000,endMs=20000)))
    val trimmed=ClipEdits.update(original) { ClipEdits.trim(it,0,14000) }
    check(TimelineBounds.duration(trimmed)==14000L)
    check(TimelineBounds.position(trimmed,19000)==13999L)
    check(TimelineBounds.visibleLength(audio.startMs,audio.endMs,TimelineBounds.duration(trimmed))==14000L)
    check(TimelineBounds.visibleLength(17000,3000,TimelineBounds.duration(trimmed))==0L)
    val next=ClipEdits.update(trimmed) { ClipEdits.trim(it,0,6000) }
    check(TimelineBounds.duration(next)==6000L && next.clips.single().sourceDurationMs==20000L)
    check(next.projectSounds==original.projectSounds)
    val h=EditHistory(original); h.change(trimmed); h.change(next)
    check(h.undo() && TimelineBounds.duration(h.state)==14000L)
    check(h.undo() && TimelineBounds.duration(h.state)==20000L)
    val muted=ClipEdits.update(original) { it.copy(muted=true) }
    check(muted.clips.single().muted && muted.projectSounds==original.projectSounds)
    println("PASS: 20s -> 14s -> 6s bounds, no audio/text ghost tail, original retained, undo and source-only mute")
}
