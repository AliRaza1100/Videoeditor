import com.lumen.studio.*;
import java.util.*;
import java.io.*;

/** Host regression checks for photo adjustment state and Java draft compatibility. */
public final class PhotoAdjustmentChecks {
    static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        PhotoAdjustments engine=PhotoAdjustments.INSTANCE;
        EditorClip base=TimelineModelChecks.clip(Map.of("filter","Warm","zoom",1.7f,"catalogAdjustments",Map.of("legacy",.4f)));
        check(engine.getControls().size()>=18,"Incomplete adjustment controls");
        for(PhotoAdjustments.Control control:engine.getControls()) {
            for(int value:new int[]{control.getMin(),control.getDefault(),control.getMax()}) {
                EditorClip edited=engine.update(base,control.getId(),value);
                check(Math.abs(engine.value(edited,control.getId())-value)<=1,"Adjustment units drift: "+control.getId());
                check(edited.getFilter().equals("Warm") && edited.getZoom()==1.7f,"Adjustment changed unrelated edits");
            }
        }
        EditorClip reset=engine.reset(engine.update(engine.update(base,"brightness",70),"photo.highlights",80));
        check(reset.getBrightness()==0f && engine.value(reset,"photo.highlights")==0,"Reset failed");
        check(reset.getFilter().equals("Warm") && reset.getCatalogAdjustments().get("legacy")==.4f,"Reset discarded unrelated state");
        EditorState state=(EditorState)TimelineModelChecks.data(EditorState.class,Map.of("clips",List.of(base),"photoEditor",true,"photoCanvasStyle","Gradient","photoCanvasFit","Fill","photoCanvasColorEnd",0xff123456));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); new ObjectOutputStream(bytes).writeObject(state);
        EditorState loaded=(EditorState)new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
        check(loaded.getPhotoCanvasStyle().equals("Gradient") && loaded.getPhotoCanvasFit().equals("Fill") && loaded.getPhotoCanvasColorEnd()==0xff123456,"Canvas draft state lost");
        System.out.println("PASS: 18 adjustment ranges/units, isolated reset, canvas draft serialization");
    }
}
