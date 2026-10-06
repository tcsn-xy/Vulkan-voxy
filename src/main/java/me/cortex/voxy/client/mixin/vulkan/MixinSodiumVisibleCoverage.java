package me.cortex.voxy.client.mixin.vulkan;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import me.cortex.voxy.client.vulkan.SodiumNearCoverage;
import net.caffeinemc.mods.sodium.client.render.chunk.*;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.region.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=VisibleChunkCollector.class,remap=false)
public class MixinSodiumVisibleCoverage {
    @WrapOperation(method="visit",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegionManager;getForChunk(III)Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;"))
    private RenderRegion voxy$visible(RenderRegionManager manager,int x,int y,int z,Operation<RenderRegion> original){
        var region=original.call(manager,x,y,z);
        if(region!=null&&(region.getSectionFlags(LocalSectionIndex.pack(x,y,z))&RenderSectionFlags.MASK_IS_BUILT)!=0)SodiumNearCoverage.mark(x,y,z);
        return region;
    }
}
