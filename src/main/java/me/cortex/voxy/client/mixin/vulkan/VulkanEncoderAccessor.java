package me.cortex.voxy.client.mixin.vulkan;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=VulkanCommandEncoder.class, remap=false)
public interface VulkanEncoderAccessor {
    @Accessor("currentRenderPass") com.mojang.renderpearl.backend.vulkan.VulkanRenderPass voxy$currentRenderPass();
    @Accessor("currentSubmitIndex") long voxy$submitIndex();
}
