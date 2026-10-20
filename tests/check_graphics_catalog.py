"""Host validation of shipped data; GPU execution is covered by GraphicsPipelineTest on Android."""
import json, math
from pathlib import Path

assets=Path(__file__).resolve().parents[1]/"app/src/main/assets"
c=json.loads((assets/"graphics/catalog.json").read_text(encoding="utf-8"))
assert c["schema"]==1
for section in ["filters","effects","typography","animations"]:
    items=c[section]
    assert len(items)>=40, (section,len(items))
    keys=[entry[k] for entry in items for k in ["id","name"]]
    assert len(keys)==len(set(keys)),section
    assert all(entry["category"] for entry in items)
for f in c["filters"]:
    assert len(f["matrix"])==20 and all(math.isfinite(x) for x in f["matrix"])
assert len({tuple(f["matrix"]) for f in c["filters"]})==len(c["filters"])
shapes={}
for e in c["effects"]:
    shader=e["shader"]
    assert shader in c["shaders"]
    shape={key:len(value) for key,value in e["uniforms"].items()}
    assert shapes.setdefault(shader,shape)==shape
    assert all(math.isfinite(x) for value in e["uniforms"].values() for x in value)
for source in c["shaders"].values():
    for path in source.values():
        assert path.startswith("graphics/") and ".." not in path and (assets/path).is_file()
for a in c["animations"]:
    assert a["durationMs"]>0
    for channel,keys in a["tracks"].items():
        assert channel in {"x","y","scaleX","scaleY","rotation","alpha","reveal","blur","wave","glitch"}
        assert keys and all(0<=k["t"]<=1 and math.isfinite(k["value"]) for k in keys)
        assert all(x["t"]<y["t"] for x,y in zip(keys,keys[1:]))
        for k in keys:
            easing=k["easing"]
            assert easing["kind"] in {"LINEAR","BEZIER","BOUNCE","ELASTIC","STEP","EASE_IN_OUT_CUBIC","ELASTIC_BOUNCE","BACK_EASE_IN","BACK_EASE_OUT","SPRING_PHYSICS"}
            assert all(0<=easing.get(x,0)<=1 for x in ["x1","x2"])
print("PASS: catalog counts, unique IDs/matrices, shader references/uniform shapes and animation tracks")
