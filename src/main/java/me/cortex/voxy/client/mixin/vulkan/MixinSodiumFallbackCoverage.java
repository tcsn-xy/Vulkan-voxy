package me.cortex.voxy.client.mixin.vulkan;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import me.cortex.voxy.client.vulkan.SodiumNearCoverage;
import net.caffeinemc.mods.sodium.client.render.chunk.*;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.FallbackVisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=FallbackVisibleChunkCollector.class,remap=false)
public class MixinSodiumFallbackCoverage {
    @WrapOperation(method="visit",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/storage/SectionStorage;getCurrent(III)Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;"))
    private RenderSection voxy$visible(SectionStorage storage,int x,int y,int z,Operation<RenderSection> original){
        var section=original.call(storage,x,y,z);
        if(section!=null&&(section.getRegion().getSectionFlags(LocalSectionIndex.pack(x,y,z))&RenderSectionFlags.MASK_IS_BUILT)!=0)SodiumNearCoverage.mark(x,y,z);
        return section;
    }
}
