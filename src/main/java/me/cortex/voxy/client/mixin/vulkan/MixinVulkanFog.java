package me.cortex.voxy.client.mixin.vulkan;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.vulkan.*;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Run before Sodium captures its immutable fog snapshot at the same return point.
@Mixin(value=FogRenderer.class,priority=900)
public class MixinVulkanFog {
    @Inject(method="setupFog",at=@At("RETURN"))
    private void voxy$distanceFog(Camera camera,int chunks,DeltaTracker delta,float darken,ClientLevel level,CallbackInfoReturnable<FogData> ci){
        if(VulkanInterop.active()&&VulkanVoxyRenderer.current!=null&&VoxyConfig.CONFIG.isRenderingEnabled()&&!Boolean.getBoolean("voxy.qa.legacyFog"))
            VulkanFog.extendDistance(ci.getReturnValue(),VoxyConfig.CONFIG.sectionRenderDistance*512);
    }
}
