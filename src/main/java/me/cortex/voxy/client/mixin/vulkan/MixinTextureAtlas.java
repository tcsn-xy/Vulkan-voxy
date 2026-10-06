package me.cortex.voxy.client.mixin.vulkan;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.SpriteLoader;
import me.cortex.voxy.client.vulkan.CpuBlockAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(TextureAtlas.class)
public class MixinTextureAtlas {
    @Inject(method="upload", at=@At("HEAD"))
    private void voxy$capture(SpriteLoader.Preparations preparations, CallbackInfo ci) {
        if (((TextureAtlas)(Object)this).location().equals(TextureAtlas.LOCATION_BLOCKS)) CpuBlockAtlas.capture(preparations);
    }
}
