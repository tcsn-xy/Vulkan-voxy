package me.cortex.voxy.client.mixin.vulkan;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.cortex.voxy.client.vulkan.UniformRange;
import org.spongepowered.asm.mixin.Mixin;
import java.util.function.Supplier;
/** Allocate the std140 tail too when an engine UBO is a dedicated short allocation. */
@Mixin(value=VulkanDevice.class,remap=false)
public class MixinUniformAllocation {
    @WrapMethod(method="createBuffer(Ljava/util/function/Supplier;IJ)Lcom/mojang/renderpearl/backend/vulkan/VulkanGpuBuffer;")
    private VulkanGpuBuffer voxy$padding(Supplier<String> label,int usage,long size,Operation<VulkanGpuBuffer> original){
        if((usage&GpuBuffer.USAGE_UNIFORM)!=0)size=UniformRange.padded(size,Long.MAX_VALUE);
        return original.call(label,usage,size);
    }
}
