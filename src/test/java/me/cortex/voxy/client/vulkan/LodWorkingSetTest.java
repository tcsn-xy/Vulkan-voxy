package me.cortex.voxy.client.vulkan;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LodWorkingSetTest {
    @Test void frontAndRearTilesBothRefineBeforeATurn(){
        double front=LodWorkingSet.priority(2,0,0,-4,64,64,64,1440,104);
        double rear=LodWorkingSet.priority(2,0,0,4,64,64,64,1440,104);
        assertTrue(front>0);assertEquals(front,rear);
    }
    @Test void radialWorkingSetCoversAllAzimuths(){
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)
            assertTrue(LodWorkingSet.priority(2,x,0,z,64,64,64,1440,104)>0);
    }
}
