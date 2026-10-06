package me.cortex.voxy.client.mixin.vitrail;
import me.cortex.voxy.client.vulkan.VulkanVoxyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Keep basic Voxy distant geometry visible after a pack replaces the main world colour. */
@Pseudo
@Mixin(targets="dev.vitrail.render.PackChain",remap=false)
public class MixinVitrailComposition {
    @Inject(method="draw",at=@At("RETURN"))
    private static void voxy$distantAfterComposition(CallbackInfoReturnable<Boolean> ci){var r=VulkanVoxyRenderer.current;if(ci.getReturnValue()&&r!=null)r.drawAfterPack();}
}
