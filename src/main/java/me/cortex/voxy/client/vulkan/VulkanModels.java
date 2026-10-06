package me.cortex.voxy.client.vulkan;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.*;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import me.cortex.voxy.client.core.model.ModelUploadSink;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.vulkan.VK10.*;
/** Uses the original 256-column tile format, allocating rows only for the active model limit. */
public final class VulkanModels implements ModelUploadSink,AutoCloseable {
    public static final int CAPACITY=4096;
    public final VulkanBuffer models,colours;
    public GpuTexture atlas;
    public VulkanGpuTextureView view;
    private final GpuBudget budget;
    private long atlasBytes;
    private int capacity=CAPACITY;
    public int uploadedModels;
    public VulkanModels(GpuBudget budget){
        this.budget=budget;
        models=new VulkanBuffer(budget,65536L*64,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT|VK_BUFFER_USAGE_TRANSFER_DST_BIT,false);
        colours=new VulkanBuffer(budget,65536L*4,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT|VK_BUFFER_USAGE_TRANSFER_DST_BIT,false);
        int w=256*48,h=(CAPACITY/256)*32;
        atlasBytes=(long)w*h*4*85/64;
        budget.reserve(atlasBytes);
        atlas=RenderSystem.getDevice().createTexture("Voxy model atlas",GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_COPY_SRC|GpuTexture.USAGE_TEXTURE_BINDING,GpuFormat.RGBA8_UNORM,w,h,1,4);
        view=(VulkanGpuTextureView)RenderSystem.getDevice().createTextureView(atlas);
    }
    @Override public boolean canUploadModel(int id){
        if(id<capacity)return true;
        int next=Math.min(65536,capacity*2);if(id>=next)return false;
        long nextBytes=atlasBytes*2;if(budget.remaining()<nextBytes)return false;
        budget.reserve(nextBytes);
        var oldAtlas=atlas;var oldView=view;long oldBytes=atlasBytes;
        var newAtlas=RenderSystem.getDevice().createTexture("Voxy model atlas "+next,GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_COPY_SRC|GpuTexture.USAGE_TEXTURE_BINDING,GpuFormat.RGBA8_UNORM,256*48,(next/256)*32,1,4);
        for(int mip=0;mip<4;mip++)RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(oldAtlas,newAtlas,mip,0,0,0,0,oldAtlas.getWidth(mip),oldAtlas.getHeight(mip));
        atlas=newAtlas;view=(VulkanGpuTextureView)RenderSystem.getDevice().createTextureView(atlas);capacity=next;atlasBytes=nextBytes;
        oldView.close();oldAtlas.close();VulkanInterop.encoder().queueForDestroy(()->budget.release(oldBytes));return true;
    }
    @Override public void uploadModel(int id,MemoryBuffer model,MemoryBuffer texture,boolean mips,int biomeIndex,MemoryBuffer biome){
        int modelFlags=MemoryUtil.memGetInt(model.address+24),tintIndex=MemoryUtil.memGetInt(model.address+28);
        if((modelFlags&2)!=0&&(tintIndex<0||tintIndex>=65536))throw new IllegalStateException("Invalid biome tint LUT base for model "+id+": "+tintIndex);
        if(id>=capacity)throw new IllegalStateException("Voxy Vulkan model atlas capacity reached: "+id);
        var cb=VulkanInterop.encoder().allocateAndBeginTransientCommandBuffer();
        models.update(cb,id*64L,MemoryUtil.memByteBuffer(model.address,64));
        if(biomeIndex>=0&&biome!=null)colours.update(cb,biomeIndex*4L,MemoryUtil.memByteBuffer(biome.address,Math.toIntExact(biome.size)));
        VkPipelines.barrier(cb,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_ACCESS_TRANSFER_WRITE_BIT,VK_PIPELINE_STAGE_VERTEX_SHADER_BIT,VK_ACCESS_SHADER_READ_BIT);
        VulkanInterop.endAndExecute(cb);
        if(Boolean.getBoolean("voxy.qa.dumpModels")&&id<64){
            try(var image=new com.mojang.blaze3d.platform.NativeImage(48,32,false)){
                MemoryUtil.memCopy(texture.address,image.getPointer(),48*32*4);
                var dir=net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("model-dumps");java.nio.file.Files.createDirectories(dir);image.writeToFile(dir.resolve("model-"+id+".png"));
            }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        }
        int x=(id&255)*48,y=(id>>>8)*32;
        long ptr=texture.address;
        for(int mip=0;mip<(mips?4:1);mip++){
            int w=48>>mip,h=32>>mip,n=w*h*4;
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(atlas,MemoryUtil.memByteBuffer(ptr,n),mip,0,x>>mip,y>>mip,w,h);
            ptr+=n;
        }
        uploadedModels=Math.max(uploadedModels,id+1);
    }
    @Override public void uploadBiomes(MemoryBuffer coloursIn,MemoryBuffer pairs){
        var cb=VulkanInterop.encoder().allocateAndBeginTransientCommandBuffer();
        colours.update(cb,0,MemoryUtil.memByteBuffer(coloursIn.address,Math.toIntExact(coloursIn.size)));
        for(long p=pairs.address;p<pairs.address+pairs.size;p+=8){long v=MemoryUtil.memGetLong(p);models.update(cb,(v&0xffffffffL)*64+28,MemoryUtil.memByteBuffer(p+4,4));}
        VkPipelines.barrier(cb,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_ACCESS_TRANSFER_WRITE_BIT,VK_PIPELINE_STAGE_VERTEX_SHADER_BIT,VK_ACCESS_SHADER_READ_BIT);
        VulkanInterop.endAndExecute(cb);
    }
    @Override public void close(){models.close();colours.close();view.close();atlas.close();VulkanInterop.encoder().queueForDestroy(()->budget.release(atlasBytes));}
}
