package me.cortex.voxy.client.mixin.vulkan;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(EntityRenderer.class)
public interface EntityCullingBoundsAccessor {
    @Invoker("getBoundingBoxForCulling") AABB voxy$cullingBounds(Entity entity,float partialTick);
}
