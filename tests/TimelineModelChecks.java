import com.lumen.studio.*;
import java.util.*;
import java.lang.reflect.*;
import java.io.*;

/** Host checks for production timing math, persistence, palettes and bitrate policy. */
public class TimelineModelChecks {
    static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    static Object data(Class<?> type,Map<String,Object> values) throws Exception {
        Field[] fields=Arrays.stream(type.getDeclaredFields()).filter(f->!Modifier.isStatic(f.getModifiers())).toArray(Field[]::new);
        int masks=(fields.length+31)/32;
        Constructor<?> constructor=Arrays.stream(type.getConstructors()).filter(c->c.getParameterCount()==fields.length+masks+1).findFirst().orElseThrow();
        Object[] args=new Object[constructor.getParameterCount()]; int[] bits=new int[masks]; Arrays.fill(bits,-1);
        for(int i=0;i<fields.length;i++) {
            Class<?> t=constructor.getParameterTypes()[i];
            if(t==boolean.class) args[i]=false;
            else if(t==int.class) args[i]=0;
            else if(t==long.class) args[i]=0L;
            else if(t==float.class) args[i]=0f;
            else if(t==double.class) args[i]=0d;
            if(values.containsKey(fields[i].getName())) { args[i]=values.get(fields[i].getName()); bits[i/32]&=~(1<<(i%32)); }
        }
        for(int i=0;i<masks;i++) args[fields.length+i]=bits[i];
        return constructor.newInstance(args);
    }
    static EditorClip clip(Map<String,Object> edits) throws Exception {
        Map<String,Object> fields=new HashMap<>(edits); fields.put("uri","file:///fixture.mp4"); fields.put("name","Fixture"); fields.put("video",true); fields.put("sourceDurationMs",10000L);
        return (EditorClip)data(EditorClip.class,fields);
    }
    static double luminance(int color) {
        double sum=0; double[] weights={.2126,.7152,.0722};
        for(int i=0;i<3;i++) { double c=((color>>(16-i*8))&255)/255.; sum+=weights[i]*(c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4)); } return sum;
    }
    static double contrast(int a,int b) { double x=luminance(a),y=luminance(b); return (Math.max(x,y)+.05)/(Math.min(x,y)+.05); }
    public static void main(String[] args) throws Exception {
        EditorClip curved=clip(Map.of("speedCurve",List.of(1f,.25f,2f,1f),"speedPositions",List.of(0f,.15f,.8f,1f)));
        check(Math.abs(ClipEdits.INSTANCE.speedAt(curved,.15)-.25)<.0001,"Nonuniform node timing ignored");
        double previous=-1;
        for(int t=0;t<=10000;t+=25) {
            double output=ClipEdits.INSTANCE.outputPosition(curved,t); check(output>previous,"Speed curve time reversed"); previous=output;
            check(Math.abs(ClipEdits.INSTANCE.sourcePosition(curved,(long)output)-t)<5,"Source/output time mapping drift");
        }
        EditorClip old=clip(Map.of("speedCurve",List.of(1f,.5f,1f)));
        check(Math.abs(ClipEdits.INSTANCE.speedAt(old,.5)-.5)<.0001,"Legacy uniform curve broken");
        EditorClip trimmed=ClipEdits.INSTANCE.trim(curved,2000,8000);
        check(trimmed.getSpeedPositions()==null,"Trim retained invalid original node positions");
        List<Float> many=new ArrayList<>(),positions=new ArrayList<>();
        for(int i=0;i<32;i++) { many.add(i%2==0?.5f:2f); positions.add(i/31f); }
        check(ClipEdits.INSTANCE.duration(clip(Map.of("speedCurve",many,"speedPositions",positions)))>0,"Many speed nodes failed");
        EditorState state=(EditorState)data(EditorState.class,new HashMap<>() {{ put("clips",List.of(curved)); put("projectVisuals",null); }});
        check(EditorStateKt.normalized(state).getProjectVisuals().isEmpty(),"Legacy draft null timed list not migrated");
        EditorState project=(EditorState)data(EditorState.class,Map.of("clips",List.of(clip(Map.of()),clip(Map.of()))));
        EditorState mapped=TimelineSpeed.INSTANCE.apply(project,List.of(0f,.5f,1f),List.of(1f,.25f,2f));
        check(Math.abs(ClipEdits.INSTANCE.speedAt(mapped.getClips().get(0),1)-.25)<.0001,"Timeline dot failed at first clip boundary");
        check(Math.abs(ClipEdits.INSTANCE.speedAt(mapped.getClips().get(1),0)-.25)<.0001,"Timeline curve discontinuity between clips");
        EditorClip image=(EditorClip)data(EditorClip.class,Map.of("uri","file:///image.png","name","Image","video",false,"imageDurationMs",4000L));
        EditorState images=(EditorState)data(EditorState.class,Map.of("clips",List.of(image,image)));
        EditorState slowed=TimelineSpeed.INSTANCE.apply(images,List.of(0f,1f),List.of(.5f,.5f));
        check(slowed.getClips().get(0).getImageDurationMs()==8000,"Timeline speed ignored image duration");
        EditorState repeated=TimelineSpeed.INSTANCE.apply(slowed,List.of(0f,1f),List.of(.5f,.5f));
        check(repeated.getClips().get(0).getImageDurationMs()==8000,"Reapplying timeline speed compounded image duration");
        EditorClip landscape=clip(Map.of());
        FrameBounds portraitFrame=FrameGeometry.INSTANCE.bounds(1920,1080,720,1280,landscape,null);
        check(Math.abs(portraitFrame.getWidth()-720)<.01 && Math.abs(portraitFrame.getHeight()-405)<.01,"Frame does not fit landscape video inside portrait canvas");
        check(Math.abs(portraitFrame.getTop()-437.5)<.01,"Frame attached to letterbox instead of video");
        FrameBounds landscapeFrame=FrameGeometry.INSTANCE.bounds(1080,1920,1280,720,landscape,null);
        check(Math.abs(landscapeFrame.getWidth()-405)<.01 && Math.abs(landscapeFrame.getLeft()-437.5)<.01,"Portrait video frame misses side letterboxes");
        FrameBounds rotated=FrameGeometry.INSTANCE.bounds(1920,1080,720,1280,clip(Map.of("rotation",90f)),null);
        check(Math.abs(rotated.getWidth()-720)<.01 && Math.abs(rotated.getHeight()-1280)<.01,"Frame ignores clip rotation");
        VisualLayer resized=(VisualLayer)data(VisualLayer.class,Map.of("kind","effect","name","Frame","frameScale",.5f,"frameX",.2f,"frameY",-.1f));
        FrameBounds edited=FrameGeometry.INSTANCE.bounds(1920,1080,720,1280,landscape,resized);
        check(Math.abs(edited.getWidth()-360)<.01,"Frame resize ignored");
        check(Math.abs((edited.getLeft()+edited.getRight())/2-432)<.01,"Frame reposition ignored");
        EditorState legacyFrame=(EditorState)data(EditorState.class,Map.of("clips",List.of(clip(Map.of("effect","Frame")))));
        EditorState migrated=FrameEditing.INSTANCE.migrate(legacyFrame);
        check(migrated.getProjectVisuals().size()==1 && migrated.getProjectVisuals().get(0).getEndMs()==10000,"Legacy frame lost editable duration");
        check(migrated.getClips().get(0).getEffect().equals("None"),"Legacy frame is duplicated after migration");
        check(FrameEditing.INSTANCE.migrate(migrated).getProjectVisuals().size()==1,"Frame migration is not idempotent");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); new ObjectOutputStream(bytes).writeObject(curved);
        EditorClip restored=(EditorClip)new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
        check(restored.getSpeedPositions().equals(curved.getSpeedPositions()),"Speed dots not persisted");
        check(((VisualLayer)data(VisualLayer.class,Map.of("kind","filter","name","Mono"))).getEndMs()==4000,"New filter default is not four seconds");
        for(String theme:List.of("light","dark","violet","gold")) {
            int[] p=ThemeColors.INSTANCE.palette(theme);
            for(int surface=0;surface<3;surface++) for(int label=3;label<=5;label++) check(contrast(p[surface],p[label])>=4.5,"Theme contrast below 4.5: "+theme+" slot "+label+" on "+surface);
            check(contrast(p[6],(theme.equals("gold")||theme.equals("light"))?p[3]:0xffffffff)>=4.5,"Primary button unreadable: "+theme);
        }
        check(ExportQuality.INSTANCE.bitrate(1920,1080,60)>ExportQuality.INSTANCE.bitrate(1920,1080,30),"High-frame-rate bitrate did not increase");
        check(ExportQuality.INSTANCE.bitrate(3840,2160,30)>ExportQuality.INSTANCE.bitrate(1280,720,30),"HD bitrate did not scale with pixels");
        System.out.println("PASS: speed math, persistence, four-second defaults, light/dark/violet/gold contrast, bitrate; Frame fit in both orientations, rotation, resize, position and legacy migration");
    }
}
