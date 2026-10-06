package me.cortex.voxy.client.mixin.vulkan;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.vulkan.VulkanInterop;
import me.cortex.voxy.client.vulkan.VulkanVoxyRenderer;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Extend the actual camera depth range before it constructs projection and culling matrices. */
@Mixin(Camera.class)
public class MixinVulkanProjection {
    @ModifyExpressionValue(method="update",at=@At(value="INVOKE",target="Ljava/lang/Math;max(FF)F",ordinal=0))
    private float voxy$far(float original){
        return VulkanInterop.active()&&VulkanVoxyRenderer.current!=null&&VoxyConfig.CONFIG.isRenderingEnabled()?Math.max(original,VoxyConfig.CONFIG.sectionRenderDistance*512+512):original;
    }
}
