package me.cortex.voxy.client.vulkan;
/** Pixel coordinates avoid precision loss for large resource-pack atlases. */
public final class WaterSpriteRegion {
    private WaterSpriteRegion(){}
    public static int pack(int x,int y){if(x<0||y<0||x>65535||y>65535)throw new IllegalArgumentException();return x|(y<<16);}
}
