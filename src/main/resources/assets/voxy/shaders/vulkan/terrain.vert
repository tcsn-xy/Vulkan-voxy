#version 450
struct Section {ivec4 origin;uvec4 ranges0;uvec4 ranges1;uvec4 info;};
layout(set=0,binding=0,std430) readonly buffer Quads {uvec2 quads[];};
layout(set=0,binding=1,std430) readonly buffer Models {uint modelData[];};
layout(set=0,binding=2,std430) readonly buffer Colours {uint colours[];};
layout(set=0,binding=3,std430) readonly buffer Sections {Section sections[];};
layout(set=0,binding=12,std430) readonly buffer OriginIds {uint originIds[];};
layout(set=0,binding=13,std430) readonly buffer Origins {ivec4 origins[];};
layout(set=0,binding=14,std430) readonly buffer IndexStream {uvec4 expandedQuads[];};
layout(set=0,binding=6) uniform sampler2D lightmap;
layout(push_constant) uniform Frame {mat4 mvp;vec4 camera;uvec4 params;vec4 fog;ivec4 coverage;} frame;
invariant gl_Position;
layout(location=0) out vec2 uv;
layout(location=1) out flat uvec4 attributes;
layout(location=2) out flat uint lighting;
layout(location=3) out vec3 localPos;
const uint corners[6]=uint[6](0,1,2,2,1,3);
vec3 axisPoint(uint axis,vec3 v){return axis==0?v.xzy:(axis==1?v:v.zxy);}
void main(){
    uint mode=frame.params.y;bool reference=(mode&2u)!=0u,fallback=(mode&4u)!=0u;
    uint vertex=uint(gl_VertexIndex),quad=reference?vertex/6u:vertex/4u;
    uvec2 q;ivec4 origin;
    if(!fallback){
        uvec4 packed=expandedQuads[uint(gl_InstanceIndex)];q=packed.xy;
        if((q.y&0x80000000u)!=0u){gl_Position=vec4(0,0,2,1);uv=vec2(0);attributes=uvec4(0);lighting=0u;localPos=vec3(0);return;}
        origin=ivec4(int(packed.z<<16u)>>16,int(packed.w<<4u)>>4,int(packed.z)>>16,int(packed.w>>28u));
    }else{q=quads[quad];origin=origins[originIds[quad]];origin.xyz-=frame.coverage.xyz;}
    uint face=q.x&7u,axis=face>>1u,id=(q.x>>26u)|((q.y&1023u)<<6u);
    uint fd=modelData[id*16u+face],flags=modelData[id*16u+6u],tint=modelData[id*16u+7u];
    uvec2 size=uvec2((q.x>>3u)&15u,(q.x>>7u)&15u)+1u;
    vec4 bounds=vec4(fd&15u,(fd>>4u)&15u,(fd>>8u)&15u,(fd>>12u)&15u)/16.0+vec4(0,1.0/16.0,0,1.0/16.0);
    vec2 begin=bounds.xz-vec2(0.00005),extent=bounds.yw-begin+vec2(size-1u);
    uint corner=reference?corners[vertex%6u]:(vertex&3u);vec2 mask=vec2(corner>>1u,corner&1u);
    float depth=float((fd>>16u)&63u)/64.0;
    vec3 start=vec3((q.x>>21u)&31u,(q.x>>16u)&31u,(q.x>>11u)&31u);
    float scale=float(1<<origin.w);
    localPos=vec3(origin.xyz)+(start+axisPoint(axis,vec3(begin+extent*mask,(face&1u)==0u?depth:1.0-depth)))*scale;
    
    if((flags&16u)==0u){
        vec3 delta=localPos-frame.camera.xyz;
        float side=axis==0u?delta.y:(axis==1u?delta.z:delta.x);
        bool back=(face&1u)==0u?side<-.00001:side>.00001;
        if(back){gl_Position=vec4(0,0,2,1);uv=vec2(0);attributes=uvec4(0);lighting=0u;return;}
    }
    gl_Position=frame.mvp*vec4(localPos-frame.camera.xyz,1);gl_Position.z=max(gl_Position.z,0.0000001);
    uv=begin+extent*mask;
    uint biome=(q.y>>14u)&511u;lighting=((q.y>>23u)&255u)|(((flags&8u)!=0u)?256u:0u);
    // Metal may flatten conditional loads; make the address safe even for constant tints (-1).
    uint lutIndex=(flags&2u)!=0u?tint+biome:0u;
    uint biomeTint=colours[min(lutIndex,65535u)];
    if((flags&2u)!=0u)tint=biomeTint;
    uint discardFlag=(fd>>22u)&1u;discardFlag|=uint(any(greaterThan(size,uvec2(1))))&((fd>>23u)&1u);
    attributes=uvec4(id,face,discardFlag|(((fd>>24u)&3u)<<2u)|(((flags>>2u)&1u)<<4u),tint);

}
