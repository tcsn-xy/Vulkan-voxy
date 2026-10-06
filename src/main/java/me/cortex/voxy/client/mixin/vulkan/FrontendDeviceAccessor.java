package me.cortex.voxy.client.mixin.vulkan;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=FrontendGpuDevice.class, remap=false)
public interface FrontendDeviceAccessor {
    @Accessor("backend") GpuDeviceBackend voxy$backend();
}
