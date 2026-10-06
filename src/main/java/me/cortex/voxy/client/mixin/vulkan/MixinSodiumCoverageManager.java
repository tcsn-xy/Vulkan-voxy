package me.cortex.voxy.client.mixin.vulkan;

import me.cortex.voxy.client.vulkan.SodiumNearCoverage;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=RenderSectionManager.class,remap=false)
public class MixinSodiumCoverageManager {
    @Shadow @Final private ClientLevel level;
    @Inject(method="renderOutOfGraph",at=@At("HEAD"))
    private void voxy$fallbackStart(Viewport viewport,FogParameters fog,CallbackInfo ci){SodiumNearCoverage.begin(viewport,level);}
    @Inject(method="readRenderListFromTree",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/VisibleChunkCollector;<init>(Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegionManager;I)V"))
    private void voxy$treeStart(Viewport viewport,FogParameters fog,CallbackInfo ci){SodiumNearCoverage.begin(viewport,level);}
    @Inject(method={"renderOutOfGraph","readRenderListFromTree"},at=@At("RETURN"))
    private void voxy$publish(Viewport viewport,FogParameters fog,CallbackInfo ci){SodiumNearCoverage.publish();}
    @Inject(method="updateSectionInfo",at=@At("RETURN"))
    private void voxy$nearGeometryChanged(net.caffeinemc.mods.sodium.client.render.chunk.RenderSection section,net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo info,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> ci){
        var renderer=me.cortex.voxy.client.vulkan.VulkanVoxyRenderer.current;if(renderer!=null)renderer.meshService.worldChanges.incrementAndGet();
    }
}
