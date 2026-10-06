package me.cortex.voxy.client.mixin.vulkan;
import com.mojang.renderpearl.api.device.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cortex.voxy.client.vulkan.QaOffscreenSurface;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.function.BooleanSupplier;
@Mixin(Minecraft.class)
public class MixinQaSurface {
    @WrapOperation(method="<init>",at=@At(value="INVOKE",target="Lcom/mojang/renderpearl/api/device/GpuDevice;createSurface(JLjava/util/function/BooleanSupplier;)Lcom/mojang/renderpearl/api/device/GpuSurface;"))
    private GpuSurface voxy$offscreen(GpuDevice device,long window,BooleanSupplier iconified,Operation<GpuSurface> original){return Boolean.getBoolean("voxy.qa.background")?new QaOffscreenSurface():original.call(device,window,iconified);}
}
