package com.lumen.studio

/** Reset only the named property; never discard unrelated edits or source media. */
object PresetReset {
    fun clip(kind: String,c: EditorClip): EditorClip = when(kind) {
        "Filters" -> c.copy(filter="Original",filterIntensity=1f)
        "Effects" -> c.copy(effect="None")
        "Adjust" -> c.copy(brightness=0f,contrast=1f,saturation=1f,exposure=0f,temperature=0f,tint=0f)
        "Brightness" -> c.copy(brightness=0f)
        "Contrast" -> c.copy(contrast=1f)
        "Saturation" -> c.copy(saturation=1f)
        "Exposure" -> c.copy(exposure=0f)
        "Temperature" -> c.copy(temperature=0f)
        "Tint" -> c.copy(tint=0f)
        "Playback speed","Playback speed (%)","Curve speed" -> c.copy(speed=1f,speedCurve=emptyList(),speedPositions=null)
        "Crop" -> c.copy(cropLeft=-1f,cropRight=1f,cropTop=1f,cropBottom=-1f,zoom=1f)
        "Image duration (seconds)" -> c.copy(imageDurationMs=4000,curveImageDurationMs=null)
        "Fade in (seconds)" -> c.copy(audioFadeInMs=0)
        "Fade out (seconds)" -> c.copy(audioFadeOutMs=0)
        "Rotate" -> c.copy(rotation=0f)
        "Flip" -> c.copy(flip=false,flipVertical=false)
        "Opacity (%)" -> c.copy(opacity=1f)
        "Sound","Clip volume (%)" -> c.copy(audioVolume=1f,muted=false,audioFadeInMs=0,audioFadeOutMs=0)
        else -> error("Unsupported clip reset: $kind")
    }
    fun text(kind: String,t: TextLayer): TextLayer = when(kind) {
        "Text animation" -> t.copy(animation="None",animationOffsetMs=0)
        "Font" -> t.copy(font="Sans")
        "Style" -> t.copy(style="Regular")
        "Color" -> t.copy(color=-1)
        "Text size" -> t.copy(size=28f)
        "Alignment" -> t.copy(alignment="Center")
        "Letter spacing" -> t.copy(letterSpacing=0f)
        "Line spacing" -> t.copy(lineSpacing=0f)
        "Text background" -> t.copy(backgroundColor=0)
        "Text outline" -> t.copy(outlineWidth=0f)
        "Text shadow" -> t.copy(shadow=true)
        "Text opacity" -> t.copy(opacity=1f)
        "Text rotation" -> t.copy(rotation=0f)
        "Position" -> t.copy(x=0f,y=0f)
        "Duration" -> t.copy(startMs=0,endMs=0)
        else -> error("Unsupported text reset: $kind")
    }
    fun image(kind: String,i: ImageLayer): ImageLayer = when(kind) {
        "Size (%)" -> i.copy(scale=.3f)
        "Opacity (%)" -> i.copy(opacity=1f)
        "Position" -> i.copy(x=0f,y=0f)
        "Duration" -> i.copy(startMs=0,endMs=0)
        else -> error("Unsupported image reset: $kind")
    }
    fun sound(kind: String,s: SoundLayer): SoundLayer = when(kind) {
        "Soundtrack volume" -> s.copy(volume=1f)
        "Fade","Fade in (seconds)","Fade out (seconds)" -> s.copy(fadeInMs=0,fadeOutMs=0)
        "Playback speed (%)" -> s.copy(speed=1f)
        else -> error("Unsupported sound reset: $kind")
    }
}
