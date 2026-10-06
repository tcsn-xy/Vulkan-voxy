package me.cortex.voxy.client.vulkan;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.*;
/** QA-only offscreen presentation: the normal game render target and GPU submission still run. */
public final class QaOffscreenSurface implements GpuSurface {
    private Configuration config;
    private boolean acquired;
    @Override public void configure(Configuration config){this.config=config;}
    @Override public Optional<Configuration> currentConfiguration(){return Optional.ofNullable(config);}
    @Override public Collection<PresentMode> supportedPresentModes(){return List.of(PresentMode.IMMEDIATE,PresentMode.FIFO);}
    @Override public boolean isSuboptimal(){return false;}
    @Override public boolean isAcquired(){return acquired;}
    @Override public void acquireNextTexture(){acquired=true;}
    @Override public void blitFromTexture(CommandEncoder encoder,GpuTextureView view){}
    @Override public void present(){acquired=false;}
    @Override public void close(){acquired=false;config=null;}
}
