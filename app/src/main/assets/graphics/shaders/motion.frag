precision mediump float;
uniform sampler2D uTexSampler;
uniform float uEnabled;
uniform float uTime;
uniform vec2 uResolution;
uniform float uBlur;
uniform float uWave;
uniform float uGlitch;
varying vec2 vTexCoord;
void main() {
    vec2 uv=vTexCoord;
    if(uEnabled<0.5) { gl_FragColor=texture2D(uTexSampler,uv); return; }
    uv.x+=sin(uv.y*28.0+uTime*6.0)*uWave*0.018;
    uv.x+=step(0.5,sin(uv.y*70.0+uTime*30.0))*uGlitch*0.025;
    vec2 d=vec2(1.0)/max(uResolution,vec2(1.0))*uBlur*12.0;
    vec4 color=texture2D(uTexSampler,uv);
    if(uBlur>0.001) {
    color*=0.2;
    color+=texture2D(uTexSampler,uv+vec2(d.x,0.0))*0.1;
    color+=texture2D(uTexSampler,uv-vec2(d.x,0.0))*0.1;
    color+=texture2D(uTexSampler,uv+vec2(0.0,d.y))*0.1;
    color+=texture2D(uTexSampler,uv-vec2(0.0,d.y))*0.1;
    color+=texture2D(uTexSampler,uv+d)*0.1;
    color+=texture2D(uTexSampler,uv-d)*0.1;
    color+=texture2D(uTexSampler,uv+vec2(d.x,-d.y))*0.1;
    color+=texture2D(uTexSampler,uv+vec2(-d.x,d.y))*0.1;
    }
    if(uGlitch>0.001) {
    color.r=mix(color.r,texture2D(uTexSampler,uv+vec2(uGlitch*0.012,0.0)).r,uGlitch);
    color.b=mix(color.b,texture2D(uTexSampler,uv-vec2(uGlitch*0.012,0.0)).b,uGlitch);
    }
    gl_FragColor=color;
}
