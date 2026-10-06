package me.cortex.voxy.client.vulkan;

import net.minecraft.client.renderer.fog.FogData;

/** Near and distant terrain share distance fog; environmental fog retains vanilla limits. */
public final class VulkanFog {
    private VulkanFog() {}
    public static void extendDistance(FogData data,float distance){
        data.renderDistanceEnd=Math.max(data.renderDistanceEnd,distance);
        data.renderDistanceStart=data.renderDistanceEnd*.85f;
    }
    static int packEnvironment(float start,float end){
        if(!Float.isFinite(start)||!Float.isFinite(end)||end<=start||end>=65504)return 0;
        return Short.toUnsignedInt(Float.floatToFloat16(start))|(Short.toUnsignedInt(Float.floatToFloat16(end))<<16);
    }
}
