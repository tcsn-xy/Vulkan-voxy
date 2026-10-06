package me.cortex.voxy.client.mixin.vulkan;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FramerateLimitTracker.class)
public class MixinQaFramerate {
    @Inject(method="getFramerateLimit",at=@At("HEAD"),cancellable=true)
    private void voxy$backgroundLimit(CallbackInfoReturnable<Integer> cir){if(Boolean.getBoolean("voxy.qa.background"))cir.setReturnValue(Integer.getInteger("voxy.qa.backgroundFps",60));}
}
