#version 450
layout(set=0,binding=15) uniform sampler2D blockAtlas;
layout(set=0,binding=1,std430) readonly buffer Models {uint modelData[];};
layout(set=0,binding=5) uniform sampler2D atlas;
layout(set=0,binding=6) uniform sampler2D lightmap;
layout(set=0,binding=8,std430) readonly buffer Coverage {ivec4 nearOrigin;uint nearCells[];};
layout(push_constant) uniform Frame {mat4 mvp;vec4 camera;uvec4 params;vec4 fog;ivec4 coverage;} frame;
layout(location=0) in vec2 uv;
layout(location=1) in flat uvec4 attributes;
layout(location=2) in flat uint lighting;
layout(location=3) in vec3 localPos;
layout(location=0) out vec4 outColour;
#ifdef VOXY_OPAQUE_NO_DISCARD
layout(early_fragment_tests) in;
#endif
void main(){
    #ifndef VOXY_OPAQUE_NO_DISCARD
    // Remove LOD only where a real near section has a compiled mesh, including empty sections.
    ivec3 cell=ivec3(floor((localPos-vec3(.0001))/16.0))-nearOrigin.xyz;
    if(all(greaterThanEqual(cell,ivec3(0)))&&all(lessThan(cell,ivec3(32,64,32)))){
        uint index=uint(cell.x+cell.z*32+cell.y*1024);if(length(localPos-frame.camera.xyz)<frame.camera.w&&(nearCells[index>>5u]&(1u<<(index&31u)))!=0u)discard;
    }
    #endif
    uint id=attributes.x,face=attributes.y;
    vec2 texSize=vec2(textureSize(atlas,0));
    vec2 base=vec2((id&255u)*48u+(face>>1u)*16u,(id>>8u)*32u+(face&1u)*16u);
    vec2 tile=fract(max(uv,vec2(0)));tile=clamp(tile,vec2(.001),vec2(.999));
    vec2 texUV=(base+tile*16.0)/texSize;
    vec4 sampled;
    if((attributes.z&32u)!=0u&&face==1u){
        uint origin=modelData[id*16u+9u],size=modelData[id*16u+10u];
        vec2 spriteOrigin=vec2(origin&65535u,origin>>16u),spriteSize=vec2(size&65535u,size>>16u),atlasSize=vec2(textureSize(blockAtlas,0));
        vec2 spriteUV=(spriteOrigin+tile*spriteSize)/atlasSize;
        sampled=textureGrad(blockAtlas,spriteUV,dFdx(uv)*spriteSize/atlasSize,dFdy(uv)*spriteSize/atlasSize);
    }else sampled=textureGrad(atlas,texUV,dFdx(uv)*16.0/texSize,dFdy(uv)*16.0/texSize);
    bool trans=(attributes.z&16u)!=0u;
    #ifndef VOXY_OPAQUE_NO_DISCARD
    if((attributes.z&1u)!=0u&&textureLod(atlas,texUV,0).a<=.1)discard;
    if(trans&&sampled.a==0)discard;
    #endif
    uint tintMode=(attributes.z>>2u)&3u;
    if(tintMode==2u||(tintMode==1u&&abs(sampled.r-sampled.g)<.02&&abs(sampled.g-sampled.b)<.02))sampled.rgb*=unpackUnorm4x8(attributes.w).zyx;
    uint levels=lighting&255u;vec3 light=textureLod(lightmap,(vec2(levels>>4u,levels&15u)+.5)/16.0,0).rgb;
    if((lighting&256u)!=0u){uint axis=face>>1u;light*=axis==2u?.6:(axis==1u?.8:(face==1u?1.0:.5));}
    sampled.rgb*=light;
    vec3 delta=localPos-frame.camera.xyz;float distance=length(delta);
    float cylindricalDistance=max(length(delta.xz),abs(delta.y));
    float fog=clamp((cylindricalDistance-uintBitsToFloat(frame.params.x)*.85)/(uintBitsToFloat(frame.params.x)*.15),0,1);
    vec2 environment=unpackHalf2x16(uint(frame.coverage.w));
    if(environment.y>environment.x)fog=max(fog,clamp((distance-environment.x)/(environment.y-environment.x),0,1));
    outColour=vec4(mix(sampled.rgb,frame.fog.rgb,fog*frame.fog.a),trans?sampled.a:1.0);
}
