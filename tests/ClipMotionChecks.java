import com.lumen.studio.*;
import java.io.*;
import com.lumen.studio.graphics.*;
import java.util.*;

public final class ClipMotionChecks {
    static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Curve easing=new Curve(CurveKind.BEZIER,.42f,0,.58f,1);
        ScalarTrack zoom=new ScalarTrack(List.of(new Keyframe(0,1,easing),new Keyframe(1,1.45f,easing)));
        AnimationPreset preset=new AnimationPreset(new PresetInfo("clip-test","Zoom","Video motion"),4000,Map.of("scaleX",zoom,"scaleY",zoom));
        check(ClipMotion.INSTANCE.sample(preset,4000,6000,0).getScaleX()==1,"Initial frame must retain scale");
        check(Math.abs(ClipMotion.INSTANCE.sample(preset,4000,6000,2000).getScaleX()-1.225f)<.001,"Wrong mid-animation interpolation");
        check(Math.abs(ClipMotion.INSTANCE.sample(preset,4000,6000,9000).getScaleY()-1.45f)<.001,"Hold final pose after animation duration");
        check(Math.abs(ClipMotion.INSTANCE.sample(preset,4000,1000,1000).getScaleX()-1.45f)<.001,"Short clips must reach the endpoint");
        check(ClipMotion.INSTANCE.sample(preset,0,0,500).getScaleX()>1,"Legacy/degenerate timing must stay finite");
        check(ClipMotion.INSTANCE.sample(null,4000,4000,2000).getScaleX()==1,"None must restore identity");
        ScalarTrack combo=new ScalarTrack(List.of(new Keyframe(0,1,easing),new Keyframe(.5f,1.45f,easing),new Keyframe(1,1,easing)));
        AnimationPreset comboPreset=new AnimationPreset(new PresetInfo("combo-test","Combo","Video motion"),4000,Map.of("scaleX",combo));
        check(ClipMotion.INSTANCE.sample(comboPreset,4000,4000,2000).getScaleX()==1.45f,"Combo midpoint");
        check(ClipMotion.INSTANCE.sample(comboPreset,4000,4000,4000).getScaleX()==1,"Combo must return to original scale");
        EditorClip clip=(EditorClip)TimelineModelChecks.data(EditorClip.class,Map.of("uri","file:///test.mp4","name","Test","video",true,"sourceDurationMs",6000L,"motion","clip.combo-pan","motionDurationMs",2500L));
        ImageLayer layer=(ImageLayer)TimelineModelChecks.data(ImageLayer.class,Map.of("uri","file:///sticker.png","rotation",37f));
        EditorState state=(EditorState)TimelineModelChecks.data(EditorState.class,Map.of("clips",List.of(clip),"projectOverlays",List.of(layer)));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ObjectOutputStream out=new ObjectOutputStream(bytes)){out.writeObject(state);}
        EditorState restored;try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){restored=(EditorState)in.readObject();}
        check("clip.combo-pan".equals(restored.getClips().get(0).getMotion()) && restored.getClips().get(0).getMotionDurationMs()==2500L,"Motion persistence");
        check(restored.getProjectOverlays().get(0).getRotation()==37f,"Image rotation persistence");
        System.out.println("PASS: clip-motion time normalization, zoom interpolation, short clips, endpoint hold, identity and combo");
    }
}
