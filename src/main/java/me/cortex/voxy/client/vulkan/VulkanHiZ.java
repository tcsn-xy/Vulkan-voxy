package me.cortex.voxy.client.vulkan;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.*;
import org.lwjgl.vulkan.*;
import org.joml.Matrix4f;
import java.nio.ByteBuffer;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRPushDescriptor.*;
import static org.lwjgl.util.vma.Vma.*;
/** Reverse-Z min pyramid. History is consumed only with an unchanged camera/projection. */
public final class VulkanHiZ implements AutoCloseable {
    public final int width,height,levels;
    public final long image,allocation,view;
    private final long[] mipViews;
    private final long bytes;
    private final GpuBudget budget;
    private final Matrix4f lastMvp=new Matrix4f();
    private double lastX,lastY,lastZ;
    private boolean valid,initialized,closed;
    private final VulkanBuffer[] occluders=new VulkanBuffer[2];
    private final VulkanFrameTiming timing;
    public VulkanHiZ(GpuBudget budget,int renderWidth,int renderHeight){
        this.budget=budget;
        width=Math.max(1,(renderWidth+1)/2);height=Math.max(1,(renderHeight+1)/2);levels=32-Integer.numberOfLeadingZeros(Math.max(width,height));
        long estimate=0;for(int i=0;i<levels;i++)estimate+=(long)Math.max(1,width>>i)*Math.max(1,height>>i)*4;
        long charged=0,createdImage=0,createdAllocation=0,createdView=0;
        mipViews=new long[levels];
        VulkanFrameTiming createdTiming=null;
        try(var s=MemoryStack.stackPush()){
            // Reserve the image before ancillary resources; unwind every partial allocation on failure.
            budget.reserve(estimate);charged=estimate;
            var ci=VkImageCreateInfo.calloc(s).sType$Default().imageType(VK_IMAGE_TYPE_2D).format(VK_FORMAT_R32_SFLOAT).mipLevels(levels).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT).tiling(VK_IMAGE_TILING_OPTIMAL).usage(VK_IMAGE_USAGE_STORAGE_BIT|VK_IMAGE_USAGE_SAMPLED_BIT).sharingMode(VK_SHARING_MODE_EXCLUSIVE).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            ci.extent().set(width,height,1);
            var ai=VmaAllocationCreateInfo.calloc(s).usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);var out=s.mallocLong(1);var alloc=s.mallocPointer(1);var info=VmaAllocationInfo.calloc(s);
            VulkanInterop.check(vmaCreateImage(VulkanInterop.device().vma(),ci,ai,out,alloc,info),"HiZ image");createdImage=out.get(0);createdAllocation=alloc.get(0);
            if(info.size()>charged)budget.reserve(info.size()-charged);else budget.release(charged-info.size());charged=info.size();
            image=createdImage;allocation=createdAllocation;bytes=charged;
            createdView=createView(0,levels);view=createdView;for(int i=0;i<levels;i++)mipViews[i]=createView(i,1);
            for(int i=0;i<2;i++)occluders[i]=new VulkanBuffer(budget,OccluderMask.WORDS*4,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,true);
            createdTiming=new VulkanFrameTiming();timing=createdTiming;
            var cb=VulkanInterop.encoder().allocateAndBeginTransientCommandBuffer();
            var barrier=VkImageMemoryBarrier.calloc(1,s).sType$Default().oldLayout(VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK_IMAGE_LAYOUT_GENERAL).dstAccessMask(VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_SHADER_WRITE_BIT).srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).image(image);
            barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(levels).layerCount(1);
            vkCmdPipelineBarrier(cb,VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,0,null,null,barrier);
            VulkanInterop.endAndExecute(cb);initialized=true;
        }catch(Throwable e){
            if(createdTiming!=null)createdTiming.close();
            for(var mask:occluders)if(mask!=null)mask.close();
            var d=VulkanInterop.device().vkDevice();
            if(createdView!=0)vkDestroyImageView(d,createdView,null);
            for(long v:mipViews)if(v!=0)vkDestroyImageView(d,v,null);
            if(createdImage!=0)vmaDestroyImage(VulkanInterop.device().vma(),createdImage,createdAllocation);
            if(charged!=0)budget.release(charged);
            throw e;
        }
    }

    private long createView(int mip,int count){try(var s=MemoryStack.stackPush()){
        var ci=VkImageViewCreateInfo.calloc(s).sType$Default().image(image).viewType(VK_IMAGE_VIEW_TYPE_2D).format(VK_FORMAT_R32_SFLOAT);
        ci.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(mip).levelCount(count).baseArrayLayer(0).layerCount(1);
        var out=s.mallocLong(1);VulkanInterop.check(vkCreateImageView(VulkanInterop.device().vkDevice(),ci,null,out),"HiZ view");return out.get(0);
    }}
    public void invalidate(){valid=false;}
    public boolean canUse(CameraRenderState camera){return valid&&Math.abs(camera.pos.x()-lastX)<.00001&&Math.abs(camera.pos.y()-lastY)<.00001&&Math.abs(camera.pos.z()-lastZ)<.00001&&lastMvp.equals(new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix),.000001f);}
    public void reduce(VkPipelines pipelines,RenderTarget target,long sampler,CameraRenderState camera,OccluderMask mask){
        if(closed)return;
        var cb=VulkanInterop.encoder().allocateAndBeginTransientCommandBuffer();int slot=(int)(VulkanInterop.submitIndex()&1);timing.begin(cb,slot);mask.write(occluders[slot].mapped());
        try(var s=MemoryStack.stackPush()){
            if(!initialized){
                var barrier=VkImageMemoryBarrier.calloc(1,s).sType$Default().oldLayout(VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK_IMAGE_LAYOUT_GENERAL).srcAccessMask(0).dstAccessMask(VK_ACCESS_SHADER_WRITE_BIT).srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).image(image);
                barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(levels).baseArrayLayer(0).layerCount(1);
                vkCmdPipelineBarrier(cb,VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,0,null,null,barrier);initialized=true;
            }
            VkPipelines.barrier(cb,VK_PIPELINE_STAGE_HOST_BIT,VK_ACCESS_HOST_WRITE_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_ACCESS_SHADER_READ_BIT);
            VkPipelines.barrier(cb,VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT|VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT|VK_ACCESS_SHADER_READ_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_SHADER_WRITE_BIT);
            vkCmdBindPipeline(cb,VK_PIPELINE_BIND_POINT_COMPUTE,pipelines.hiz);
            for(int i=0;i<levels;i++){
                long src=i==0?((VulkanGpuTextureView)target.getDepthTextureView()).vkImageView():mipViews[i-1];
                var writes=VkWriteDescriptorSet.calloc(3,s);
                writes.get(0).sType$Default().dstBinding(7).descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(VkDescriptorImageInfo.calloc(1,s).imageView(src).imageLayout(VK_IMAGE_LAYOUT_GENERAL).sampler(sampler));
                writes.get(1).sType$Default().dstBinding(9).descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).pImageInfo(VkDescriptorImageInfo.calloc(1,s).imageView(mipViews[i]).imageLayout(VK_IMAGE_LAYOUT_GENERAL));
                writes.get(2).sType$Default().dstBinding(10).descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).pBufferInfo(VkDescriptorBufferInfo.calloc(1,s).buffer(occluders[slot].handle).offset(0).range(occluders[slot].size));
                vkCmdPushDescriptorSetKHR(cb,VK_PIPELINE_BIND_POINT_COMPUTE,pipelines.layout,0,writes);
                vkCmdPushConstants(cb,pipelines.layout,VK_SHADER_STAGE_ALL,0,s.ints(i==0?1:0));
                int w=Math.max(1,width>>i),h=Math.max(1,height>>i);vkCmdDispatch(cb,(w+7)/8,(h+7)/8,1);
                VkPipelines.barrier(cb,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_ACCESS_SHADER_WRITE_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_ACCESS_SHADER_READ_BIT);
            }
        }
        timing.end(cb,slot);VulkanInterop.endAndExecute(cb);
        lastMvp.set(camera.projectionMatrix).mul(camera.viewRotationMatrix);lastX=camera.pos.x();lastY=camera.pos.y();lastZ=camera.pos.z();valid=true;
    }
    public double gpuMillis(){return timing.gpuMillis;}
    @Override public void close(){if(closed)return;closed=true;timing.close();for(var mask:occluders)mask.close();VulkanInterop.encoder().queueForDestroy(()->{var d=VulkanInterop.device().vkDevice();vkDestroyImageView(d,view,null);for(long v:mipViews)vkDestroyImageView(d,v,null);vmaDestroyImage(VulkanInterop.device().vma(),image,allocation);budget.release(bytes);});}
}
