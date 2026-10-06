package me.cortex.voxy.client.vulkan;

import me.cortex.voxy.client.config.VoxyConfig;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.multiplayer.ClientLevel;

/** CPU-only Sodium 0.9.2 adapter. Its renderer owns the actual near-section visibility. */
public final class SodiumNearCoverage {
    private record Snapshot(ClientLevel level,NearCoverageGrid grid) {}
    private static volatile Snapshot snapshot;
    private static ClientLevel buildingLevel;
    private static NearCoverageGrid builder;
    private SodiumNearCoverage() {}
    public static void begin(Viewport viewport,ClientLevel level){
        if(!VulkanInterop.active()||VulkanVoxyRenderer.current==null||!VoxyConfig.CONFIG.isRenderingEnabled()){reset();return;}
        var pos=viewport.getBlockCoord();buildingLevel=level;
        int y=NearCoverageGrid.verticalBase(Math.floorDiv(pos.getY(),16),Math.floorDiv(level.getMinY(),16),Math.floorDiv(level.getMaxY(),16));
        builder=new NearCoverageGrid(Math.floorDiv(pos.getX(),32)*2-16,y,Math.floorDiv(pos.getZ(),32)*2-16);
    }
    public static void mark(int x,int y,int z){if(builder!=null)builder.mark(x,y,z);}
    public static void publish(){if(builder==null)return;builder.freeze();snapshot=new Snapshot(buildingLevel,builder);builder=null;buildingLevel=null;}
    public static NearCoverageGrid forLevel(ClientLevel level){var state=snapshot;return state!=null&&state.level()==level?state.grid():null;}
    public static void reset(){snapshot=null;builder=null;buildingLevel=null;}
}
