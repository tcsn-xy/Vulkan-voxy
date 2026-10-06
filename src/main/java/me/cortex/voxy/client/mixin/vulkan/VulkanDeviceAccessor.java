package me.cortex.voxy.client.mixin.vulkan;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=VulkanDevice.class, remap=false)
public interface VulkanDeviceAccessor {
    @Accessor("enabledFeatures") FeatureSet voxy$enabledFeatures();
}
