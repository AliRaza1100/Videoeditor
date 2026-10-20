package com.lumen.studio

import android.content.Context

/** Device-private presentation metadata, separate from queued editor snapshots. */
object ProjectPresentation {
    private fun prefs(c: Context)=c.getSharedPreferences("project-presentation",Context.MODE_PRIVATE)
    fun name(c: Context,id: String,fallback: String)=prefs(c).getString("$id.name",null) ?: fallback
    fun rename(c: Context,id: String,value: String) {
        require(value.trim().isNotEmpty() && value.trim().length<=80)
        prefs(c).edit().putString("$id.name",value.trim()).apply()
    }
    fun favorite(c: Context,id: String)=prefs(c).getBoolean("$id.favorite",false)
    fun favorite(c: Context,id: String,value: Boolean) { prefs(c).edit().putBoolean("$id.favorite",value).apply() }
    fun progress(c: Context,id: String)=prefs(c).getInt("$id.progress",0).coerceIn(0,100)
    fun progress(c: Context,id: String,value: Int) { prefs(c).edit().putInt("$id.progress",value.coerceIn(0,100)).apply() }
    fun remove(c: Context,id: String) { prefs(c).edit().remove("$id.name").remove("$id.favorite").remove("$id.progress").apply() }
}
