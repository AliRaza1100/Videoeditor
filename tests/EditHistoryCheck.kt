package com.lumen.studio

/** Run as a plain Kotlin/JVM program; no Android runtime or media permission required. */
fun main() {
    val initial = EditorState(listOf(EditorClip("content://a", "A", true), EditorClip("content://b", "B", false)))
    val history = EditHistory(initial)
    check(!history.undo() && !history.redo())
    val titled = initial.copy(title = "Summer Trip")
    history.change(titled)
    check(history.undo() && history.state == initial)
    check(history.redo() && history.state == titled)
    history.undo()
    history.change(initial.copy(title = "A new branch"))
    check(history.redo.isEmpty())
    history.select(99)
    check(history.state.active == 1)
    val beforeRotation = history.state
    history.change(beforeRotation.copy(clips = beforeRotation.clips.map { it.copy(rotation = 90f) }))
    check(history.undo() && history.state == beforeRotation)
    check(initial.clips.all { it.rotation == 0f })
    repeat(70) { history.change(history.state.copy(title = "Edit $it")) }
    check(history.undo.size == 50)
    val audio = history.state.copy(audio = "file:///private/sounds/test.wav",audioName = "آواز",audioVolume = .4f)
    history.change(audio)
    check(history.undo() && history.state.audio == null)
    check(history.redo() && history.state == audio)
    val bytes = java.io.ByteArrayOutputStream().also { stream -> java.io.ObjectOutputStream(stream).use { it.writeObject(audio) } }.toByteArray()
    val restored = java.io.ObjectInputStream(java.io.ByteArrayInputStream(bytes)).use { it.readObject() as EditorState }
    check(restored == audio && restored.audioName == "آواز")
    val continuous=EditHistory(initial)
    continuous.beginGroup()
    repeat(100) { step -> continuous.change(ClipEdits.update(continuous.state) { it.copy(opacity=step/100f) }) }
    continuous.adoptPrepared(continuous.state)
    continuous.change(ClipEdits.update(continuous.state) { it.copy(opacity=.25f) })
    continuous.endGroup()
    check(continuous.undo.size==1)
    check(continuous.undo() && continuous.state==initial)
    check(continuous.redo() && continuous.state.clips[0].opacity==.25f)
    val typography=TextLayer(text="Saved style",alignment="Right",rotation=23f,opacity=.4f,letterSpacing=.2f,lineSpacing=12f,backgroundColor=123,outlineWidth=4f,shadow=false)
    val styled=initial.copy(projectTexts=listOf(typography))
    val serialized=java.io.ByteArrayOutputStream().also { stream -> java.io.ObjectOutputStream(stream).use { it.writeObject(styled) } }.toByteArray()
    check(java.io.ObjectInputStream(java.io.ByteArrayInputStream(serialized)).use { it.readObject() }==styled)
    println("Edit history checks passed: undo, redo, branch reset, active clip bounds, immutable clips, history limit.")
}
