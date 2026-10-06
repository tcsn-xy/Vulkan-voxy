package me.cortex.voxy.client.mixin.vulkan;
import com.mojang.blaze3d.platform.Window;
import com.mojang.renderpearl.api.device.GpuBackend;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Opt-in QA windows are hidden and unfocusable at creation, with no foreground flash. */
@Mixin(Window.class)
public class MixinQaWindow {
    @Shadow @Final private com.mojang.blaze3d.platform.WindowEventHandler eventHandler;
    @Shadow private void refreshFramebufferSize(){}
    @Inject(method="setFullscreen",at=@At("HEAD"),cancellable=true)
    private void voxy$neverFullscreen(boolean fullscreen,CallbackInfo ci){if(Boolean.getBoolean("voxy.qa.background"))ci.cancel();}
    @Inject(method="setWindowed",at=@At("HEAD"),cancellable=true)
    private void voxy$virtualResize(int width,int height,CallbackInfo ci){
        if(Boolean.getBoolean("voxy.qa.background")){System.setProperty("voxy.qa.width",Integer.toString(width));System.setProperty("voxy.qa.height",Integer.toString(height));this.refreshFramebufferSize();this.eventHandler.framebufferSizeChanged();ci.cancel();}
    }
    @WrapOperation(method="createWindow",at=@At(value="INVOKE",target="Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"))
    private long voxy$hidden(GpuBackend backend,String title,int width,int height,long flags,Operation<Long> original){
        if(Boolean.getBoolean("voxy.qa.background"))flags|=SDLVideo.SDL_WINDOW_HIDDEN|SDLVideo.SDL_WINDOW_NOT_FOCUSABLE;
        return original.call(backend,title,width,height,flags);
    }
    @Inject(method="queryFramebufferSize",at=@At("HEAD"),cancellable=true)
    private void voxy$offscreenSize(CallbackInfoReturnable<Window.FramebufferSize> cir){if(Boolean.getBoolean("voxy.qa.background"))cir.setReturnValue(new Window.FramebufferSize(Integer.getInteger("voxy.qa.width",3440),Integer.getInteger("voxy.qa.height",1440)));}
    @Inject(method="isFocused",at=@At("HEAD"),cancellable=true)
    private void voxy$focus(CallbackInfoReturnable<Boolean> cir){if(Boolean.getBoolean("voxy.qa.background"))cir.setReturnValue(false);}
}
