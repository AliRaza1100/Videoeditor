"""Reproducible bundled presets. Runtime reads JSON, never runs this generator."""
import json, math
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "app/src/main/assets/graphics"
root.mkdir(parents=True, exist_ok=True)

def info(name, category):
    return dict(id=name.lower().replace(" ", "-"), name=name, category=category)

def grade(r=1,g=1,b=1,offset=0,sat=1):
    lum=[.213,.715,.072]
    out=[]
    for row,gain in enumerate([r,g,b]):
        out += [round(gain*((1-sat)*lum[col]+(sat if row==col else 0)),6) for col in range(3)]+[0,offset]
    return out+[0,0,0,1,0]

filters=[]
legacy={
 "Warm": (1.1,1,.85,0,1),"Golden":(1.15,1.04,.8,8,1),"Sunset":(1.2,.95,.8,5,1.15),"Honey":(1.08,1.02,.85,12,.9),"Peach":(1.12,.99,.95,12,.85),
 "Cool":(.85,1,1.1,0,1),"Arctic":(.85,1.05,1.2,8,.8),"Ocean":(.8,1.07,1.15,0,1.1),"Steel":(.9,.97,1.08,-5,.6),"Moonlight":(.75,.83,1.1,-8,.7),
 "Vintage":(1.1,1,.8,8,.5),"Sepia":(1.2,1,.75,0,.25),"Faded":(.85,.85,.85,30,.7),"Retro":(1.08,1.02,.8,10,.7),"Film":(1.1,1.05,.95,-8,.8),
 "Cinematic":(1.15,1.08,1,-8,.8),"Teal":(.8,1.1,1.15,-5,.85),"Noir":(1.35,1.35,1.35,-35,0),"Drama":(1.3,1.2,1.15,-25,.8),"Matte":(.85,.9,.95,20,.8),
 "Natural":(1,1,1,0,.9),"Bright":(1,1,1,18,1),"High Contrast":(1.25,1.25,1.25,-32,1),"Mono":(1,1,1,0,0),"Vivid":(1.08,1.08,1.08,-8,1.4)}
for i,(name,values) in enumerate(legacy.items()):
    m=grade(*values)
    if name=="Warm": m[4]=6; m[14]=0
    if name=="Cool": m[14]=6
    if name=="Vintage": m=[.393,.769,.189,0,0,.349,.686,.168,0,0,.272,.534,.131,0,0,0,0,0,1,0]
    if name=="Cinematic":
        m=grade(1.15,1.08,1,-10,.8); m[9]=-8; m[14]=-5
    filters.append(dict(**info(name,["Warm","Cool","Vintage","Cinema","Essentials"][i//5]),matrix=m))
for i,name in enumerate(["Dawn","Amber","Coral","Saffron","Copper","Lagoon","Glacier","Denim","Midnight","Indigo","Olive","Moss","Forest","Jade","Emerald","Cream","Pearl","Linen","Silver","Graphite","Plum","Orchid","Blush","Mulberry","Rosewood"]):
    hue=i*math.pi/9
    filters.append(dict(**info(name,"Studio grades"),matrix=grade(1+.13*math.cos(hue),1+.1*math.cos(hue-2.1),1+.15*math.cos(hue+2.1),(i%5-2)*5,.65+(i%7)*.1)))

effects=[]
groups=[("Vignette",0,.65,1,1), ("Grain",1,.08,1,1), ("Scan",2,.25,1,160), ("Glitch",3,.018,5,35), ("Prism",4,.012,1,1), ("Mosaic",5,1,1,80), ("Wave",6,.015,2,25), ("Flare",7,.35,1,1), ("Poster",8,1,1,5), ("Mirror",9,1,1,4)]
for group,mode,amount,speed,freq in groups:
    for j,level in enumerate(["Soft","Classic","Deep","Pulse","Wide"]):
        name=f"{group} {level}"
        effects.append(dict(**info(name,group),shader="pixel",uniforms=dict(uMode=[mode],uAmount=[amount*(.45+j*.3)],uSpeed=[speed*(.7+j*.2)],uFrequency=[freq*(.7+j*.15)],uTint=[1,.7+.05*j,.45+.1*j])))

typography=[]
basic=[("Regular",400,False,0,False,False), ("Bold",700,False,0,False,False),("Italic",400,True,0,False,False),("Bold italic",700,True,0,False,False),("Outline",700,False,3,False,False),("Subtitle",700,False,0,False,False),("Underline",400,False,0,True,False),("Strikethrough",400,False,0,False,True)]
def typography_entry(name,category,family="sans-serif",weight=700,italic=False,stroke=0,glow=0,glow_color="#00000000",background="#00000000",spacing=0,underline=False,strike=False):
    return dict(**info(name,category),family=family,weight=weight,italic=italic,textColor="#FFFFFFFF",strokeColor="#FF000000",strokeWidth=stroke,glowColor=glow_color,glowRadius=glow,background=background,padding=8,letterSpacing=spacing,underline=underline,strike=strike)
for name,weight,italic,stroke,underline,strike in basic:
    typography.append(typography_entry(name,"Basic",weight=weight,italic=italic,stroke=stroke,background="#B4000000" if name=="Subtitle" else "#00000000",underline=underline,strike=strike))
for family,title in [("sans-serif","Studio"),("serif","Editorial"),("monospace","Terminal"),("sans-serif-condensed","Poster"),("cursive","Script")]:
    for i,look in enumerate(["Clean","Heavy","Fine","Outline","Neon cyan","Neon rose","Card","Spaced"]):
        typography.append(typography_entry(f"{title} {look}",title,family,weight=[400,900,200,700,700,700,600,500][i],stroke=2 if i==3 else 0,glow=12 if i in [4,5] else 0,glow_color="#FF00E5FF" if i==4 else "#FFFF45C0" if i==5 else "#00000000",background="#B4182030" if i==6 else "#00000000",spacing=.15 if i==7 else 0))

def track(start,end,kind="BEZIER"):
    return [dict(t=0,value=start,easing=dict(kind=kind,x1=.22,y1=1,x2=.36,y2=1)),dict(t=1,value=end,easing=dict(kind="LINEAR"))]
animations=[]
base=[("Fade",dict(alpha=track(0,1))), ("Slide",dict(x=track(-.2,0))), ("Rise",dict(y=track(.15,0))), ("Bounce",dict(y=track(-.2,0,"BOUNCE"))), ("Pop",dict(scaleX=track(.5,1,"ELASTIC"),scaleY=track(.5,1,"ELASTIC"))), ("Zoom",dict(scaleX=track(.2,1),scaleY=track(.2,1))), ("Typewriter",dict(reveal=track(0,1,"LINEAR")))]
for name,tracks in base: animations.append(dict(**info(name,"Classic"),durationMs=500,tracks=tracks))
for kind in ["BEZIER","ELASTIC","BOUNCE","LINEAR","STEP"]:
    motions=[("Left",{"x":track(-.3,0,kind)}),("Right",{"x":track(.3,0,kind)}),("Up",{"y":track(.25,0,kind)}),("Down",{"y":track(-.25,0,kind)}),("Spin",{"rotation":track(-150,0,kind)}),("Grow",{"scaleX":track(.1,1,kind),"scaleY":track(.1,1,kind)}),("Tilt",{"rotation":track(25,0,kind),"alpha":track(0,1,kind)}),("Stretch",{"scaleX":track(1.8,1,kind),"scaleY":track(.4,1,kind)})]
    for name,tracks in motions: animations.append(dict(**info(f"{kind.title()} {name}",kind.title()),durationMs=800,tracks=tracks))

identity=grade()
zero=[0]*20
adjustments=[]
def adjustment(id,minimum,maximum,default,base,slope,after=False,exponential=False):
    adjustments.append(dict(id=id,minimum=minimum,maximum=maximum,default=default,base=base,slope=slope,afterFilter=after,exponential=exponential))
saturation_base=grade(sat=0)
adjustment("saturation",0,3,1,saturation_base,[a-b for a,b in zip(identity,saturation_base)])
contrast_base=zero.copy();contrast_base[4]=contrast_base[9]=contrast_base[14]=128;contrast_base[18]=1
contrast_slope=zero.copy();contrast_slope[0]=contrast_slope[6]=contrast_slope[12]=1;contrast_slope[4]=contrast_slope[9]=contrast_slope[14]=-128
adjustment("contrast",0,3,1,contrast_base,contrast_slope)
brightness=zero.copy();brightness[4]=brightness[9]=brightness[14]=1
adjustment("brightness",-255,255,0,identity,brightness)
exposure_base=zero.copy();exposure_base[18]=1
exposure_slope=zero.copy();exposure_slope[0]=exposure_slope[6]=exposure_slope[12]=1
adjustment("exposure",-5,5,0,exposure_base,exposure_slope,True,True)
temperature=zero.copy();temperature[0]=.15;temperature[12]=-.15
adjustment("temperature",-1,1,0,identity,temperature,True)
tint=zero.copy();tint[6]=-.15
adjustment("tint",-1,1,0,identity,tint,True)

catalog=dict(schema=1,adjustments=adjustments,shaders=dict(pixel=dict(vertex="graphics/shaders/fullscreen.vert",fragment="graphics/shaders/pixel.frag")),fonts={"Sans":"sans-serif","Serif":"serif","Monospace":"monospace","Condensed":"sans-serif-condensed","Casual":"casual","Cursive":"cursive"},filters=filters,effects=effects,typography=typography,animations=animations)
(root/"catalog.json").write_text(json.dumps(catalog,indent=2)+"\n",encoding="utf-8")
print({k:len(catalog[k]) for k in ["filters","effects","typography","animations"]})
