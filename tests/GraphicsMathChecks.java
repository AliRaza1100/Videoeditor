import com.lumen.studio.graphics.*;
import java.util.*;

/** Executes the production Kotlin interpolation code on the host JVM. */
public final class GraphicsMathChecks {
    static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    static Curve curve(CurveKind kind) { return new Curve(kind,.42f,0f,.58f,1f); }
    public static void main(String[] args) {
        for(CurveKind kind: CurveKind.values()) {
            check(Easing.INSTANCE.evaluate(0,curve(kind))==0,"Start endpoint: "+kind);
            check(Easing.INSTANCE.evaluate(1,curve(kind))==1,"End endpoint: "+kind);
            for(int i=0;i<=1000;i++) check(Float.isFinite(Easing.INSTANCE.evaluate(i/1000f,curve(kind))),"Nonfinite curve: "+kind);
        }
        check(Math.abs(Easing.INSTANCE.evaluate(.5f,curve(CurveKind.BEZIER))-.5f)<.0001,"Bezier inversion");
        Curve linear=curve(CurveKind.LINEAR);
        ScalarTrack track=new ScalarTrack(List.of(new Keyframe(0,0,linear),new Keyframe(.25f,10,linear),new Keyframe(1,20,linear)));
        check(track.sample(-1)==0 && track.sample(2)==20,"Hold endpoint values");
        check(Math.abs(track.sample(.625f)-15)<.0001,"Nonuniform keyframe intervals");
        boolean rejected=false;
        try { new ScalarTrack(List.of(new Keyframe(.5f,1,linear),new Keyframe(.5f,2,linear))); } catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"Reject duplicate key times");
        rejected=false;
        try { new ScalarTrack(List.of(new Keyframe(0,Float.NaN,linear))); } catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"Reject NaN values");
        AnimationPreset animation=new AnimationPreset(new PresetInfo("test-animation","Test","Math"),1000,Map.of("x",track));
        check(animation.pose(-100).getX()==0 && animation.pose(2000).getX()==20,"Animation endpoint persistence");
        check(animation.pose(500).getAlpha()==1 && animation.pose(500).getScaleX()==1,"Missing channels retain identity");
        System.out.println("PASS: all easing curves, Bezier inversion, keyframes, bounds, invalid data and animation poses");
    }
}
