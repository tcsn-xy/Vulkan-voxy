package me.cortex.voxy.client.mixin.vulkan;

import me.cortex.voxy.client.vulkan.VulkanQa;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinQaGameRenderer {
    @Inject(method="render",at=@At("HEAD"))
    private void voxy$qaStart(CallbackInfo ci){VulkanQa.beginGameFrame();}
    @Inject(method="render",at=@At("RETURN"))
    private void voxy$qaEnd(CallbackInfo ci){VulkanQa.endGameFrame();}
}
