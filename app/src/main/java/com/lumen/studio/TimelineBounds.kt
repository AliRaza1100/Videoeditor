package com.lumen.studio

/** Primary media defines the project end; optional layers never leave a ghost tail. */
object TimelineBounds {
    fun duration(state: EditorState)=state.clips.sumOf { ClipEdits.duration(it) }
    fun position(state: EditorState,position: Long)=position.coerceIn(0,(duration(state)-1).coerceAtLeast(0))
    fun visibleLength(start: Long,length: Long,end: Long)=minOf(length,(end-start).coerceAtLeast(0)).coerceAtLeast(0)
}
