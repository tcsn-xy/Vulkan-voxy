package me.cortex.voxy.client.vulkan;

import net.minecraft.client.renderer.fog.FogData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VulkanFogTest {
    @Test void expandingDistancePreservesWaterAndEffectFog(){
        var fog=new FogData();fog.renderDistanceStart=115.2f;fog.renderDistanceEnd=128;
        fog.environmentalStart=-8;fog.environmentalEnd=96;fog.skyEnd=128;fog.cloudEnd=192;
        VulkanFog.extendDistance(fog,2048);
        assertEquals(2048,fog.renderDistanceEnd);assertEquals(2048*.85f,fog.renderDistanceStart);
        assertEquals(-8,fog.environmentalStart);assertEquals(96,fog.environmentalEnd);assertEquals(128,fog.skyEnd);assertEquals(192,fog.cloudEnd);
    }
    @Test void distantFogDoesNotShortenAnExistingLongerRange(){
        var fog=new FogData();fog.renderDistanceEnd=4096;VulkanFog.extendDistance(fog,2048);assertEquals(4096,fog.renderDistanceEnd);
    }
    @Test void environmentalLimitsSurviveGpuPacking(){
        for(float[] limits:new float[][]{{-8,96},{0,1},{.25f,6},{192,512},{8000,8192}}){
            int packed=VulkanFog.packEnvironment(limits[0],limits[1]);
            assertEquals(limits[0],Float.float16ToFloat((short)packed),.01);
            assertEquals(limits[1],Float.float16ToFloat((short)(packed>>>16)),.01);
        }
        assertEquals(0,VulkanFog.packEnvironment(0,Float.POSITIVE_INFINITY));assertEquals(0,VulkanFog.packEnvironment(10,5));
    }
}
