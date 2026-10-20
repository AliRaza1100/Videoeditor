precision highp float;
uniform sampler2D uTexSampler;
uniform float uEnabled;
uniform float uBitmap;
uniform float uTime;
uniform vec2 uResolution;
uniform float uMode;
uniform float uAmount;
uniform float uSpeed;
uniform float uFrequency;
uniform vec3 uTint;
varying vec2 vTexCoord;

float noise(vec2 p) { return fract(sin(dot(p,vec2(12.9898,78.233))) * 43758.5453); }
vec3 toLinear(vec3 c) { return mix(c/12.92,pow((c+0.055)/1.055,vec3(2.4)),step(vec3(0.04045),c)); }
vec3 toSrgb(vec3 c) { return mix(c*12.92,1.055*pow(max(c,vec3(0.0)),vec3(1.0/2.4))-0.055,step(vec3(0.0031308),c)); }
vec4 sampleAt(vec2 uv) {
    vec4 c=texture2D(uTexSampler,clamp(uv,vec2(0.0),vec2(1.0)));
    if(uBitmap>0.5) c.rgb=toLinear(c.rgb);
    return c;
}
void main() {
    vec2 uv=vTexCoord;
    vec4 original=sampleAt(uv);
    if(uEnabled<0.5) { gl_FragColor=texture2D(uTexSampler,uv); return; }
    float t=uTime*uSpeed;
    vec3 rgb=original.rgb;
    if(uMode<0.5) {
        vec2 p=uv-0.5; p.x*=uResolution.x/uResolution.y;
        rgb*=1.0-clamp(dot(p,p)*uAmount*1.8,0.0,0.95);
    } else if(uMode<1.5) {
        float grain=noise(floor(uv*uResolution)+floor(t*24.0))-0.5;
        rgb+=grain*uAmount;
    } else if(uMode<2.5) {
        rgb*=1.0-uAmount*(0.5+0.5*sin(uv.y*uFrequency*6.28318+t));
    } else if(uMode<3.5) {
        float row=floor(uv.y*uFrequency);
        uv.x+=(noise(vec2(row,floor(t*12.0)))-0.5)*uAmount*step(0.65,noise(vec2(row,floor(t*5.0))));
        rgb=sampleAt(uv).rgb;
    } else if(uMode<4.5) {
        vec2 delta=vec2(uAmount*(0.7+0.3*sin(t)),0.0);
        rgb=vec3(sampleAt(uv+delta).r,original.g,sampleAt(uv-delta).b);
    } else if(uMode<5.5) {
        vec2 cells=max(vec2(2.0),vec2(uFrequency,uFrequency*uResolution.y/uResolution.x));
        rgb=sampleAt((floor(uv*cells)+0.5)/cells).rgb;
    } else if(uMode<6.5) {
        uv.x+=sin(uv.y*uFrequency+t)*uAmount;
        uv.y+=cos(uv.x*uFrequency+t)*uAmount*0.35;
        rgb=sampleAt(uv).rgb;
    } else if(uMode<7.5) {
        vec2 delta=uv-vec2(0.2,0.8); delta.x*=uResolution.x/uResolution.y;
        rgb+=uTint*exp(-dot(delta,delta)*8.0)*uAmount*(0.85+0.15*sin(t));
    } else if(uMode<8.5) {
        float levels=max(2.0,uFrequency);
        rgb=floor(rgb*levels+0.5)/levels;
    } else {
        float sections=max(2.0,floor(uFrequency));
        vec2 p=uv-0.5;
        float angle=atan(p.y,p.x)+t*0.1;
        float sector=6.283185/sections;
        angle=abs(mod(angle,sector)-sector*0.5);
        rgb=sampleAt(0.5+length(p)*vec2(cos(angle),sin(angle))).rgb;
    }
    rgb=clamp(rgb,0.0,1.0);
    if(uBitmap>0.5) rgb=toSrgb(rgb);
    gl_FragColor=vec4(rgb,original.a);
}
