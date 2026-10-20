package com.lumen.studio

/** SRT times are relative to the selected clip, in its edited playback time. */
object CaptionParser {
    fun parse(value: String,durationMs: Long): List<TextLayer> {
        val timestamp=Regex("(\\d{2}):(\\d{2}):(\\d{2})[,\\.](\\d{3})")
        fun milliseconds(v: String): Long {
            val p=timestamp.matchEntire(v.trim()) ?: error("Invalid caption timestamp")
            val n=p.groupValues.drop(1).map { it.toLong() }
            require(n[1]<60 && n[2]<60)
            return n[0]*3600000+n[1]*60000+n[2]*1000+n[3]
        }
        return value.removePrefix("\uFEFF").replace("\r\n","\n").trim().split(Regex("\n\\s*\n")).mapNotNull { block ->
            val lines=block.lines(); val index=lines.indexOfFirst { it.contains("-->") }
            if(index<0) return@mapNotNull null
            val times=lines[index].split("-->"); require(times.size==2)
            val start=milliseconds(times[0]); val end=milliseconds(times[1].trim().substringBefore(' '))
            require(end>start)
            val text=lines.drop(index+1).joinToString("\n").replace(Regex("<[^>]+>"),"")
            if(start>=durationMs || text.isBlank()) null else TextLayer(text=text,y=-.7f,startMs=start,endMs=minOf(end,durationMs))
        }
    }
}
