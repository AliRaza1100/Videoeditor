package com.lumen.studio

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

data class SavedProject(val id: String, val name: String, val updated: Long, val state: EditorState,
    val created: Long = System.currentTimeMillis(), val order: Long = 0) : Serializable {
    companion object { private const val serialVersionUID = 1L }
}
object ProjectStore {
    private fun directory(context: Context) = File(context.filesDir,"projects").apply { mkdirs() }
    private fun file(context: Context, id: String): AtomicFile {
        require(id.matches(Regex("[a-zA-Z0-9-]+")))
        return AtomicFile(File(directory(context),"$id.project"))
    }
    @Synchronized fun save(context: Context, project: SavedProject, expectedEpoch: Long?=null) {
        if(expectedEpoch!=null && expectedEpoch!=EditingStorage.epoch(context)) return
        if(File(directory(context),"${project.id}.deleted").exists()) return
        val previous = load(context,project.id)
        // A queued older autosave must not overwrite the final synchronous save.
        if((previous?.updated ?: 0L) > project.updated) return
        val prefs = context.getSharedPreferences("project-order",Context.MODE_PRIVATE)
        val order = previous?.order?.takeIf { it > 0 } ?: (prefs.getLong("next",0)+1).also { prefs.edit().putLong("next",it).commit() }
        val persisted = project.copy(name = ProjectPresentation.name(context,project.id,project.name),created = previous?.created ?: project.created,order = order,state = project.state.normalized())
        val file = file(context,project.id)
        val stream = file.startWrite()
        try { val output = ObjectOutputStream(stream); output.writeObject(persisted); output.flush(); file.finishWrite(stream) }
        catch(error: Exception) { file.failWrite(stream); throw error }
        // Drafts are user data: retention is explicit, never silently evict an older draft.
    }
    @Synchronized fun load(context: Context, id: String): SavedProject? = try {
        ObjectInputStream(file(context,id).openRead()).use { it.readObject() as? SavedProject }?.let {
            it.copy(name = ProjectPresentation.name(context,it.id,it.name),created = if(it.created > 0) it.created else it.updated,state = it.state.normalized())
        }
    } catch (_: Exception) { null }
    @Synchronized fun list(context: Context): List<SavedProject> = directory(context).listFiles()?.filter { it.name.endsWith(".project") }
        ?.mapNotNull { load(context,it.name.removeSuffix(".project")) }?.sortedByDescending { it.updated } ?: emptyList()
    @Synchronized fun delete(context: Context,id: String) {
        file(context,id).delete()
        ProjectPresentation.remove(context,id)
        File(directory(context),"$id.deleted").writeText("removed")
    }
}
