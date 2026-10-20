import json
from pathlib import Path
from collections import Counter

assets=Path(__file__).resolve().parents[1]/"app/src/main/assets"
layouts=json.loads((assets/"photo-layouts.json").read_text(encoding="utf-8"))
assert len(layouts)>=10
assert set(range(1,10)) <= {len(layout["cells"]) for layout in layouts}
for layout in layouts:
    cells=layout["cells"]
    assert 1<=len(cells)<=9
    assert abs(sum((r-l)*(b-t) for l,t,r,b in cells)-1)<1e-5
    for i,(l,t,r,b) in enumerate(cells):
        assert 0<=l<r<=1 and 0<=t<b<=1
        for ll,tt,rr,bb in cells[i+1:]:
            assert min(r,rr)<=max(l,ll) or min(b,bb)<=max(t,tt), "Overlapping collage cells"
catalog=json.loads((assets/"graphics/catalog.json").read_text(encoding="utf-8"))
presets=[p for p in catalog["animations"] if p["category"].startswith("Clip ")]
counts=Counter(p["category"] for p in presets)
assert len(presets)>=40 and all(counts[k]>=6 for k in ["Clip In","Clip Out","Clip Combo"])
defaults={"x":0,"y":0,"scaleX":1,"scaleY":1,"rotation":0,"alpha":1,"blur":0,"wave":0,"glitch":0}
for p in presets:
    for name,keys in p["tracks"].items():
        if p["category"]=="Clip In": assert abs(keys[-1]["value"]-defaults[name])<1e-5,(p["id"],name)
        if p["category"]=="Clip Out": assert abs(keys[0]["value"]-defaults[name])<1e-5,(p["id"],name)
        if p["category"]=="Clip Combo":
            delta=keys[-1]["value"]-keys[0]["value"]
            assert abs(delta%360 if name=="rotation" else delta)<1e-5,(p["id"],name)
curves={k["easing"]["kind"] for p in presets for keys in p["tracks"].values() for k in keys}
assert {"LINEAR","EASE_IN_OUT_CUBIC","ELASTIC_BOUNCE","BACK_EASE_IN","BACK_EASE_OUT","SPRING_PHYSICS"}<=curves
print(f"PASS: {len(layouts)} nonoverlapping collage layouts; {len(presets)} clip presets, stage endpoints, loop continuity, six easing families")
