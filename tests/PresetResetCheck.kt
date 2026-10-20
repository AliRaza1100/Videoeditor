package com.lumen.studio

fun main() {
    val text=TextLayer(text="Keep these words",style="Outline",font="Serif",color=0xff00ff00.toInt(),animation="Bounce",animationOffsetMs=250,startMs=300,endMs=1800)
    val image=ImageLayer(uri="file:///sticker.png",scale=.8f,opacity=.4f,x=.2f,y=-.4f,startMs=700,endMs=2000)
    val sound=SoundLayer(uri="file:///audio.wav",name="Keep sound",volume=.3f,speed=2f,fadeInMs=500,fadeOutMs=600,sourceStartMs=100,startMs=900,endMs=2400)
    val clip=EditorClip("file:///video.mp4","Keep source",true,sourceDurationMs=8000,startMs=100,endMs=7000,filter="Golden",filterIntensity=.4f,effect="Vignette",brightness=40f,contrast=1.8f,saturation=.2f,rotation=90f,flip=true,speed=2f,speedCurve=listOf(1f,2f,1f),texts=listOf(text),overlays=listOf(image),sounds=listOf(sound))
    val filter=PresetReset.clip("Filters",clip)
    check(filter==clip.copy(filter="Original",filterIntensity=1f))
    check(PresetReset.clip("Effects",clip)==clip.copy(effect="None"))
    check(PresetReset.clip("Rotate",clip)==clip.copy(rotation=0f))
    check(PresetReset.clip("Playback speed",clip)==clip.copy(speed=1f,speedCurve=emptyList()))
    check(PresetReset.clip("Brightness",clip)==clip.copy(brightness=0f))
    check(PresetReset.text("Text animation",text)==text.copy(animation="None",animationOffsetMs=0))
    check(PresetReset.text("Style",text)==text.copy(style="Regular"))
    check(PresetReset.text("Font",text)==text.copy(font="Sans"))
    check(PresetReset.text("Color",text)==text.copy(color=-1))
    check(PresetReset.image("Size (%)",image)==image.copy(scale=.3f))
    check(PresetReset.image("Opacity (%)",image)==image.copy(opacity=1f))
    check(PresetReset.sound("Fade",sound)==sound.copy(fadeInMs=0,fadeOutMs=0))
    check(PresetReset.sound("Playback speed (%)",sound)==sound.copy(speed=1f))
    val initial=EditorState(listOf(clip,clip.copy(id="second",filter="Cool")),projectTexts=listOf(text),projectOverlays=listOf(image),projectSounds=listOf(sound))
    val history=EditHistory(initial)
    val cleared=ClipEdits.update(initial) { PresetReset.clip("Filters",it) }
    history.change(cleared)
    check(history.state.clips[1]==initial.clips[1])
    check(history.undo() && history.state==initial)
    check(history.redo() && history.state==cleared)
    for(remove in listOf(initial.copy(projectTexts=emptyList()),initial.copy(projectOverlays=emptyList()),initial.copy(projectSounds=emptyList()))) {
        val h=EditHistory(initial); h.change(remove); check(h.undo() && h.state==initial); check(h.redo() && h.state==remove)
    }
    check(runCatching { PresetReset.clip("Delete",clip) }.isFailure)
    println("PASS: scoped None resets, preserved source/timing/unrelated layers, selected-clip isolation, removal undo/redo")
}
