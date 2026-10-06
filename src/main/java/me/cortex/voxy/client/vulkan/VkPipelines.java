package me.cortex.voxy.client.vulkan;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.lwjgl.util.shaderc.Shaderc;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRPushDescriptor.*;
/** Push descriptors avoid mutable in-flight descriptor sets and allocator churn. */
public final class VkPipelines implements AutoCloseable {
    public final long descriptorLayout,layout,opaque,solid,transparent,opaqueFallback,solidFallback,transparentFallback,cull,hiz;
    private final long cache;
    private final Path cachePath;
    public VkPipelines(GpuFormat colour,GpuFormat depth){
        try(var s=MemoryStack.stackPush()){
            var props=VkPhysicalDeviceProperties.calloc(s);vkGetPhysicalDeviceProperties(VulkanInterop.device().vkDevice().getPhysicalDevice(),props);
            byte[] uuid=new byte[VK_UUID_SIZE];props.pipelineCacheUUID().get(uuid);
            cachePath=Minecraft.getInstance().gameDirectory.toPath().resolve(".voxy-vulkan-shaders").resolve("pipeline-"+java.util.HexFormat.of().formatHex(uuid)+".bin");
            ByteBuffer data=null;
            try{if(Files.isRegularFile(cachePath)&&Files.size(cachePath)<=32L*1024*1024){byte[] bytes=Files.readAllBytes(cachePath);data=org.lwjgl.system.MemoryUtil.memAlloc(bytes.length);data.put(bytes).flip();}}catch(java.io.IOException ignored){}
            var cacheInfo=VkPipelineCacheCreateInfo.calloc(s).sType$Default().pInitialData(data);var handle=s.mallocLong(1);
            int result=vkCreatePipelineCache(VulkanInterop.device().vkDevice(),cacheInfo,null,handle);
            if(result!=VK_SUCCESS){cacheInfo.pInitialData(null);VulkanInterop.check(vkCreatePipelineCache(VulkanInterop.device().vkDevice(),cacheInfo,null,handle),"pipeline cache");}
            cache=handle.get(0);if(data!=null)org.lwjgl.system.MemoryUtil.memFree(data);
            var bindings=VkDescriptorSetLayoutBinding.calloc(16,s);
            for(int i=0;i<16;i++) bindings.get(i).binding(i).descriptorCount(1).descriptorType(i==9?VK_DESCRIPTOR_TYPE_STORAGE_IMAGE:((i>=5&&i<=7)||i==15?VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER:VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)).stageFlags(VK_SHADER_STAGE_ALL);
            var dci=VkDescriptorSetLayoutCreateInfo.calloc(s).sType$Default().flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR).pBindings(bindings);
            var out=s.mallocLong(1);VulkanInterop.check(vkCreateDescriptorSetLayout(VulkanInterop.device().vkDevice(),dci,null,out),"descriptor layout");descriptorLayout=out.get(0);
            var range=VkPushConstantRange.calloc(1,s).stageFlags(VK_SHADER_STAGE_ALL).offset(0).size(128);
            var lci=VkPipelineLayoutCreateInfo.calloc(s).sType$Default().pSetLayouts(s.longs(descriptorLayout)).pPushConstantRanges(range);
            VulkanInterop.check(vkCreatePipelineLayout(VulkanInterop.device().vkDevice(),lci,null,out),"pipeline layout");layout=out.get(0);
        }
        long solidFrag=shader("terrain.frag",Shaderc.shaderc_fragment_shader,"#define VOXY_OPAQUE_NO_DISCARD\n");
        long vert=shader("terrain.vert",Shaderc.shaderc_vertex_shader),frag=shader("terrain.frag",Shaderc.shaderc_fragment_shader),comp=shader("cull.comp",Shaderc.shaderc_compute_shader,(VulkanInterop.subgroupBallot?"#define VOXY_USE_SUBGROUP_BALLOT\n":"")+(Boolean.getBoolean("voxy.qa.coarseHiZ")?"#define VOXY_HIZ_BIAS 0\n":"")),hz=shader("hiz.comp",Shaderc.shaderc_compute_shader);
        try{int topology="reference".equals(System.getProperty("voxy.qa.mode"))?VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST:VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
            solid=graphics(vert,solidFrag,colour,depth,false,topology);opaque=graphics(vert,frag,colour,depth,false,topology);transparent=graphics(vert,frag,colour,depth,true,topology);
            solidFallback=graphics(vert,solidFrag,colour,depth,false,VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);opaqueFallback=graphics(vert,frag,colour,depth,false,VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);transparentFallback=graphics(vert,frag,colour,depth,true,VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);cull=compute(comp);hiz=compute(hz);}finally{destroyShader(solidFrag);destroyShader(vert);destroyShader(frag);destroyShader(comp);destroyShader(hz);}
    }
    private static void destroyShader(long h){vkDestroyShaderModule(VulkanInterop.device().vkDevice(),h,null);}
    public static long shader(String name,int type){return shader(name,type,"");}
    private static long shader(String name,int type,String define){
        ByteBuffer spirv=null;
        try(var s=MemoryStack.stackPush()){
            String source;
            try(var in=VkPipelines.class.getResourceAsStream("/assets/voxy/shaders/vulkan/"+name)){if(in==null)throw new IllegalStateException(name);source=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
            if(!define.isEmpty()){int newline=source.indexOf('\n');source=source.substring(0,newline+1)+define+source.substring(newline+1);}
            String hash=java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            Path cache=Minecraft.getInstance().gameDirectory.toPath().resolve(".voxy-vulkan-shaders").resolve(hash+".spv");
            if(Files.exists(cache)){byte[] bytes=Files.readAllBytes(cache);spirv=org.lwjgl.system.MemoryUtil.memAlloc(bytes.length);spirv.put(bytes).flip();}
            else{
                long compiler=Shaderc.shaderc_compiler_initialize(),opts=Shaderc.shaderc_compile_options_initialize();
                Shaderc.shaderc_compile_options_set_target_env(opts,Shaderc.shaderc_target_env_vulkan,Shaderc.shaderc_env_version_vulkan_1_1);
                Shaderc.shaderc_compile_options_set_optimization_level(opts,Shaderc.shaderc_optimization_level_performance);
                long result=Shaderc.shaderc_compile_into_spv(compiler,source,type,name,"main",opts);
                try{
                    if(Shaderc.shaderc_result_get_compilation_status(result)!=Shaderc.shaderc_compilation_status_success)throw new IllegalStateException(name+": "+Shaderc.shaderc_result_get_error_message(result));
                    var bytes=Shaderc.shaderc_result_get_bytes(result);spirv=org.lwjgl.system.MemoryUtil.memAlloc(bytes.remaining());spirv.put(bytes).flip();
                    byte[] saved=new byte[spirv.remaining()];spirv.get(0,saved);Files.createDirectories(cache.getParent());Files.write(cache,saved);
                }finally{Shaderc.shaderc_result_release(result);Shaderc.shaderc_compile_options_release(opts);Shaderc.shaderc_compiler_release(compiler);}
            }
            var ci=VkShaderModuleCreateInfo.calloc(s).sType$Default().pCode(spirv);var out=s.mallocLong(1);
            VulkanInterop.check(vkCreateShaderModule(VulkanInterop.device().vkDevice(),ci,null,out),"shader module");return out.get(0);
        }catch(Exception e){throw new RuntimeException("Compile "+name,e);}finally{if(spirv!=null)org.lwjgl.system.MemoryUtil.memFree(spirv);}
    }
    private long compute(long shader){try(var s=MemoryStack.stackPush()){
        var stage=VkPipelineShaderStageCreateInfo.calloc(s).sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT).module(shader).pName(s.UTF8("main"));
        var ci=VkComputePipelineCreateInfo.calloc(1,s).sType$Default().stage(stage).layout(layout);var out=s.mallocLong(1);
        VulkanInterop.check(vkCreateComputePipelines(VulkanInterop.device().vkDevice(),cache,ci,null,out),"compute pipeline");return out.get(0);
    }}
    private long graphics(long vs,long fs,GpuFormat colour,GpuFormat depth,boolean blend,int topology){try(var s=MemoryStack.stackPush()){
        var stages=VkPipelineShaderStageCreateInfo.calloc(2,s);stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vs).pName(s.UTF8("main"));stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fs).pName(s.UTF8("main"));
        var vi=VkPipelineVertexInputStateCreateInfo.calloc(s).sType$Default();
        var ia=VkPipelineInputAssemblyStateCreateInfo.calloc(s).sType$Default().topology(topology);
        var viewport=VkPipelineViewportStateCreateInfo.calloc(s).sType$Default().viewportCount(1).scissorCount(1);
        var raster=VkPipelineRasterizationStateCreateInfo.calloc(s).sType$Default().polygonMode(VK_POLYGON_MODE_FILL).cullMode(VK_CULL_MODE_NONE).frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE).lineWidth(1);
        var samples=VkPipelineMultisampleStateCreateInfo.calloc(s).sType$Default().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
        var ds=VkPipelineDepthStencilStateCreateInfo.calloc(s).sType$Default().depthTestEnable(true).depthWriteEnable(true).depthCompareOp(VK_COMPARE_OP_GREATER_OR_EQUAL);
        var attach=VkPipelineColorBlendAttachmentState.calloc(1,s);attach.get(0).colorWriteMask(15).blendEnable(blend).srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA).dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA).colorBlendOp(VK_BLEND_OP_ADD).srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE).dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA).alphaBlendOp(VK_BLEND_OP_ADD);
        var blending=VkPipelineColorBlendStateCreateInfo.calloc(s).sType$Default().pAttachments(attach);
        var dynamic=VkPipelineDynamicStateCreateInfo.calloc(s).sType$Default().pDynamicStates(s.ints(VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR));
        var rendering=VkPipelineRenderingCreateInfo.calloc(s).sType$Default().pColorAttachmentFormats(s.ints(VulkanConst.toVk(colour))).depthAttachmentFormat(VulkanConst.toVk(depth));
        var ci=VkGraphicsPipelineCreateInfo.calloc(1,s).sType$Default().pNext(rendering).pStages(stages).pVertexInputState(vi).pInputAssemblyState(ia).pViewportState(viewport).pRasterizationState(raster).pMultisampleState(samples).pDepthStencilState(ds).pColorBlendState(blending).pDynamicState(dynamic).layout(layout);
        var out=s.mallocLong(1);VulkanInterop.check(vkCreateGraphicsPipelines(VulkanInterop.device().vkDevice(),cache,ci,null,out),"terrain pipeline");return out.get(0);
    }}
    public static void barrier(VkCommandBuffer cb,int srcStage,int srcAccess,int dstStage,int dstAccess){try(var s=MemoryStack.stackPush()){
        var m=VkMemoryBarrier.calloc(1,s).sType$Default().srcAccessMask(srcAccess).dstAccessMask(dstAccess);
        vkCmdPipelineBarrier(cb,srcStage,dstStage,0,m,null,null);
    }}
    private void saveCache(){
        try(var stack=MemoryStack.stackPush()){
            var size=stack.callocPointer(1);if(vkGetPipelineCacheData(VulkanInterop.device().vkDevice(),cache,size,null)!=VK_SUCCESS)return;
            if(size.get(0)>32L*1024*1024)return;
            ByteBuffer bytes=org.lwjgl.system.MemoryUtil.memAlloc((int)size.get(0));
            try{if(vkGetPipelineCacheData(VulkanInterop.device().vkDevice(),cache,size,bytes)==VK_SUCCESS){byte[] data=new byte[(int)size.get(0)];bytes.get(0,data);Files.createDirectories(cachePath.getParent());Files.write(cachePath,data);}}finally{org.lwjgl.system.MemoryUtil.memFree(bytes);}
        }catch(java.io.IOException ignored){}
    }
    @Override public void close(){saveCache();VulkanInterop.encoder().queueForDestroy(()->{var d=VulkanInterop.device().vkDevice();vkDestroyPipeline(d,opaqueFallback,null);vkDestroyPipeline(d,solidFallback,null);vkDestroyPipeline(d,transparentFallback,null);vkDestroyPipeline(d,opaque,null);vkDestroyPipeline(d,solid,null);vkDestroyPipeline(d,transparent,null);vkDestroyPipeline(d,cull,null);vkDestroyPipeline(d,hiz,null);vkDestroyPipelineCache(d,cache,null);vkDestroyPipelineLayout(d,layout,null);vkDestroyDescriptorSetLayout(d,descriptorLayout,null);});}
}
