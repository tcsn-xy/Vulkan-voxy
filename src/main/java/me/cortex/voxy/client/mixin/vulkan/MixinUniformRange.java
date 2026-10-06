package me.cortex.voxy.client.mixin.vulkan;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPass;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cortex.voxy.client.vulkan.UniformRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** 26.3 binds vec3 std140 blocks with a twelve-byte range; Metal requires sixteen. */
@Mixin(value=VulkanRenderPass.class,remap=false)
public class MixinUniformRange {
    @WrapOperation(method="pushDescriptors",at=@At(value="INVOKE",target="Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;length()J",ordinal=0),require=1)
    private long voxy$pad(GpuBufferSlice slice,Operation<Long> original){return UniformRange.padded(original.call(slice),slice.buffer().size()-slice.offset());}
}
