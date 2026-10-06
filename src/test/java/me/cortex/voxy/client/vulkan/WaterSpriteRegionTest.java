package me.cortex.voxy.client.vulkan;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WaterSpriteRegionTest {
    @Test void largeAtlasSpriteCoordinatesRoundTripExactly(){int p=WaterSpriteRegion.pack(16321,32000);assertEquals(16321,p&65535);assertEquals(32000,p>>>16);assertThrows(IllegalArgumentException.class,()->WaterSpriteRegion.pack(65536,0));}
}
