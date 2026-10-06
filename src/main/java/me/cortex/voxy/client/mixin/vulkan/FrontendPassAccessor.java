package me.cortex.voxy.client.mixin.vulkan;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=FrontendRenderPass.class, remap=false)
public interface FrontendPassAccessor {
    @Accessor("backend") RenderPassBackend voxy$backend();
    @Accessor("boundPipeline") void voxy$invalidatePipeline(FrontendRenderPipeline value);
}
