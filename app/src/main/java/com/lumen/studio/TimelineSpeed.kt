package com.lumen.studio

/** Project dots are distributed into clip-local source-time curves, including clip boundaries. */
object TimelineSpeed {
    private fun length(c: EditorClip)=if(c.video) ClipEdits.sourceLength(c) else (c.curveImageDurationMs ?: c.imageDurationMs).toDouble()
    fun profile(state: EditorState): EditorClip = EditorClip("timeline","Entire timeline",true,
        sourceDurationMs=state.clips.sumOf { length(it).toLong() }.coerceAtLeast(1),
        speedCurve=state.projectSpeedValues.orEmpty(),speedPositions=state.projectSpeedPositions)
    fun apply(state: EditorState,positions: List<Float>,speeds: List<Float>): EditorState {
        require(positions.size==speeds.size && positions.size>=2 && positions.first()==0f && positions.last()==1f && positions.zipWithNext().all { it.first<it.second })
        val profile=profile(state).copy(speedCurve=speeds,speedPositions=positions)
        val total=ClipEdits.sourceLength(profile); var start=0.0
        val clips=state.clips.map { c ->
            val duration=length(c); val a=start/total; val b=(start+duration)/total; start+=duration
            val local=listOf(0f)+positions.filter { it>a && it<b }.map { ((it-a)/(b-a)).toFloat() }+listOf(1f)
            val values=local.map { ClipEdits.speedAt(profile,a+it*(b-a)) }
            if(c.video) c.copy(speed=1f,speedCurve=values,speedPositions=local)
            else {
                val image=EditorClip("image","Image",true,sourceDurationMs=duration.toLong(),speedCurve=values,speedPositions=local)
                c.copy(imageDurationMs=ClipEdits.outputPosition(image,duration).toLong().coerceAtLeast(100),curveImageDurationMs=duration.toLong())
            }
        }
        return state.copy(clips=clips,projectSpeedPositions=positions,projectSpeedValues=speeds)
    }
}
