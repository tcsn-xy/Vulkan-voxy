package me.cortex.voxy.client.mixin.vitrail;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.vulkan.VulkanInterop;
import me.cortex.voxy.client.vulkan.VulkanVoxyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
/** Extend shader view distance without changing near chunk loading or shadow distance. */
@Pseudo
@Mixin(targets="dev.vitrail.render.ViewMatrices",remap=false)
public class MixinVitrailViewDistance {
    @ModifyVariable(method="advance",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float voxy$viewDistance(float original){
        return VulkanInterop.active()&&VulkanVoxyRenderer.current!=null&&VoxyConfig.CONFIG.isRenderingEnabled()?Math.max(original,VoxyConfig.CONFIG.sectionRenderDistance*512):original;
    }
}
