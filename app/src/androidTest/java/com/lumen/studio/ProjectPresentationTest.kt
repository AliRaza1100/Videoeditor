package com.lumen.studio

import android.test.InstrumentationTestCase
import java.util.UUID

/** Run in the isolated QA app: protects metadata against queued editor autosaves. */
@Suppress("DEPRECATION")
class ProjectPresentationTest: InstrumentationTestCase() {
    fun testRenameSurvivesAutosaveAndPresentationStaysProjectSpecific() {
        val context=instrumentation.targetContext
        assertTrue(context.packageName.endsWith(".qa"))
        val a="presentation-${UUID.randomUUID()}"; val b="presentation-${UUID.randomUUID()}"
        try {
            val initial=SavedProject(a,"Original",100,EditorState(listOf(EditorClip("file:///missing.mp4","Clip",true,sourceDurationMs=4000))))
            ProjectStore.save(context,initial)
            ProjectPresentation.rename(context,a,"My film")
            ProjectPresentation.favorite(context,a,true); ProjectPresentation.progress(context,a,42)
            ProjectStore.save(context,initial.copy(updated=200))
            assertEquals("My film",ProjectStore.load(context,a)!!.name)
            assertEquals(42,ProjectPresentation.progress(context,a)); assertTrue(ProjectPresentation.favorite(context,a))
            assertFalse(ProjectPresentation.favorite(context,b)); assertEquals(0,ProjectPresentation.progress(context,b))
            assertTrue(ProjectStore.list(context).any { it.id==a && ProjectPresentation.progress(context,it.id)<100 })
            ProjectPresentation.progress(context,a,100)
            assertFalse(ProjectStore.list(context).any { it.id==a && ProjectPresentation.progress(context,it.id)<100 })
            ProjectStore.delete(context,a)
            assertEquals(0,ProjectPresentation.progress(context,a)); assertFalse(ProjectPresentation.favorite(context,a))
            assertEquals("Fallback",ProjectPresentation.name(context,a,"Fallback"))
        } finally { ProjectStore.delete(context,a); ProjectPresentation.remove(context,b) }
    }
    fun testSoundtrackMuteSurvivesDraftSerialization() {
        val context=instrumentation.targetContext
        assertTrue(context.packageName.endsWith(".qa"))
        val id="music-${UUID.randomUUID()}"
        try {
            val state=EditorState(listOf(EditorClip("file:///missing.mp4","Clip",true,sourceDurationMs=4000)),musicMuted=true)
            ProjectStore.save(context,SavedProject(id,"Muted soundtrack",100,state))
            val loaded=ProjectStore.load(context,id)!!.state
            assertEquals(true,loaded.musicMuted)
            assertFalse("Source audio is a separate control",loaded.clips.single().muted)
            val history=EditHistory(loaded); history.change(loaded.copy(musicMuted=false)); history.undo()
            assertEquals(true,history.state.musicMuted)
        } finally { ProjectStore.delete(context,id) }
    }
}
