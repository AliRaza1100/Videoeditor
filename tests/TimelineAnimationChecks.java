import com.lumen.studio.*;
import com.lumen.studio.graphics.*;
import java.lang.reflect.*;
import java.util.*;

/** Production normalized engine and stage evaluator, with a pure in-memory catalog. */
public final class TimelineAnimationChecks {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static ScalarTrack track(float a,float b){return new ScalarTrack(List.of(new Keyframe(0,a,new Curve(CurveKind.LINEAR,.25f,.1f,.25f,1)),new Keyframe(1,b,new Curve(CurveKind.LINEAR,.25f,.1f,.25f,1))));}
    static EditorClip changedAnimation(EditorClip c) {
        try { return (EditorClip)TimelineModelChecks.data(EditorClip.class,Map.of("uri",c.getUri(),"name",c.getName(),"video",true,"sourceDurationMs",c.getSourceDurationMs(),"animations",new ClipAnimationSettings(null,null,"combo-test",500,500,1000))); }
        catch(Exception error) { throw new RuntimeException(error); }
    }
    public static void main(String[] args) throws Exception {
        AnimationPreset incoming=new AnimationPreset(new PresetInfo("in-test","In test","Clip In"),1000,Map.of("x",track(-1,0),"alpha",track(0,1)));
        AnimationPreset outgoing=new AnimationPreset(new PresetInfo("out-test","Out test","Clip Out"),1000,Map.of("alpha",track(1,0),"blur",track(0,1)));
        AnimationPreset combo=new AnimationPreset(new PresetInfo("combo-test","Combo test","Clip Combo"),1000,Map.of("rotation",track(0,360)));
        Constructor<?> constructor=Arrays.stream(GraphicsCatalog.class.getDeclaredConstructors()).filter(c->c.getParameterCount()==7).findFirst().orElseThrow();constructor.setAccessible(true);
        GraphicsCatalog catalog=(GraphicsCatalog)constructor.newInstance(List.of(),List.of(),List.of(),List.of(incoming,outgoing,combo),Map.of(),Map.of(),List.of());
        EditorClip clip=(EditorClip)TimelineModelChecks.data(EditorClip.class,Map.of("uri","file:///test.mp4","name","Clip","video",true,"sourceDurationMs",5000L,"animations",new ClipAnimationSettings("in-test","out-test","combo-test",1000,1000,2000)));
        ClipTimelineEvaluator timeline=new ClipTimelineEvaluator(clip,catalog);MotionFrame out=new MotionFrame();
        timeline.evaluate(0,out);check(out.getX()==-1 && out.getAlpha()==0,"In placement at clip start");
        timeline.evaluate(500,out);check(Math.abs(out.getX()+.5)<.001 && Math.abs(out.getAlpha()-.5)<.001,"In interpolation");
        timeline.evaluate(3000,out);check(out.getX()==0 && out.getAlpha()==1 && out.getRotation()==180,"Combo continues between entry/exit");
        timeline.evaluate(4500,out);check(Math.abs(out.getAlpha()-.5)<.001 && Math.abs(out.getBlur()-.5)<.001,"Out starts relative to clip end");
        timeline.evaluate(5000,out);check(out.getAlpha()==0 && out.getBlur()==1,"Out endpoint");
        timeline.evaluate(1000,out);float first=out.getRotation();timeline.evaluate(3000,out);check(first==out.getRotation(),"Combo period repeat");
        KeyframeMatrixEngine.INSTANCE.evaluate(null,.5f,out);check(out.getScaleX()==1 && out.getAlpha()==1 && out.getBlur()==0,"Scratch reset must clear previous effects");
        boolean rejected=false;try{KeyframeMatrixEngine.INSTANCE.evaluate(incoming,Float.NaN,out);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Invalid normalized time");
        for(CurveKind kind:List.of(CurveKind.LINEAR,CurveKind.EASE_IN_OUT_CUBIC,CurveKind.ELASTIC_BOUNCE,CurveKind.BACK_EASE_IN,CurveKind.BACK_EASE_OUT,CurveKind.SPRING_PHYSICS))for(int i=0;i<=1000;i++)check(Float.isFinite(Easing.INSTANCE.evaluate(i/1000f,new Curve(kind,.25f,.1f,.25f,1))),"Nonfinite easing");
        ClipAnimationSettings longEntry=new ClipAnimationSettings("in-test","out-test",null,3000,1000,2000);
        EditorClip timingClip=(EditorClip)TimelineModelChecks.data(EditorClip.class,Map.of("uri","file:///test.mp4","name","Clip","video",true,"sourceDurationMs",5000L,"animations",longEntry));
        ClipTimelineEvaluator precise=new ClipTimelineEvaluator(timingClip,catalog);
        precise.evaluate(2500,out);check(Math.abs(out.getX()+1f/6)<.001,"Renderer silently shortened a valid 3-second entry");
        ClipAnimationSettings fitted=EditorStateKt.fittedTo(longEntry,1000);
        check(fitted.getInMs()+fitted.getOutMs()<=1000,"Split clip animations overlap their clip length");
        EditorState project=(EditorState)TimelineModelChecks.data(EditorState.class,Map.of("clips",List.of(timingClip)));
        EditorState split=ClipEdits.INSTANCE.split(project,2500);
        check(split.getClips().size()==2,"Split failed");
        EditorState changed=ClipEdits.INSTANCE.update(split,c -> changedAnimation(c));
        check(changed.getClips().get(1).getAnimations().equals(longEntry),"Editing one split changed the other clip");
        System.out.println("PASS: normalized keyframes, independent In/Out stages, combo repetition, opacity/pixel channels, scratch reset and six easing families");
    }
}
