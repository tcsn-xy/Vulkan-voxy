package me.cortex.voxy.client.vulkan;

import org.junit.jupiter.api.Test;
import java.nio.*;
import static org.junit.jupiter.api.Assertions.*;

class NearCoverageGridTest {
    private static ByteBuffer buffer(){return ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN);}
    private static boolean gpuCell(ByteBuffer b,int x,int y,int z){int i=x+z*32+y*1024;return (b.getInt((i>>>5)*4)&(1<<(i&31)))!=0;}
    @Test void negativeWorldCoordinatesUseTheShaderLayoutAndNativeByteOrder(){
        var grid=new NearCoverageGrid(-16,-4,-16);grid.mark(-16,-4,-16);grid.mark(-1,3,-1);grid.mark(15,59,15);grid.freeze();
        var bytes=buffer();grid.writeTo(bytes,-16,-4,-16);
        assertTrue(gpuCell(bytes,0,0,0));assertTrue(gpuCell(bytes,15,7,15));assertTrue(gpuCell(bytes,31,63,31));
        assertFalse(gpuCell(bytes,1,7,15));assertEquals(3,grid.cells());
    }
    @Test void movingTheCameraRebasesExistingVisibleSections(){
        var grid=new NearCoverageGrid(-16,-4,-16);grid.mark(-16,-4,-16);grid.mark(0,3,0);grid.mark(15,59,15);grid.freeze();
        var bytes=buffer();grid.writeTo(bytes,-14,0,-12);
        assertTrue(gpuCell(bytes,14,3,12));assertTrue(gpuCell(bytes,29,59,27));
        assertEquals(2,java.util.stream.IntStream.range(0,2048).map(i->Integer.bitCount(bytes.getInt(i*4))).sum());
    }
    @Test void aNewSnapshotDoesNotEraseThePublishedOne(){
        var old=new NearCoverageGrid(0,0,0);old.mark(1,2,3);old.freeze();
        var next=new NearCoverageGrid(0,0,0);next.mark(3,2,1);next.freeze();
        var bytes=buffer();old.writeTo(bytes,0,0,0);assertTrue(gpuCell(bytes,1,2,3));assertFalse(gpuCell(bytes,3,2,1));
        assertThrows(IllegalStateException.class,()->old.mark(4,5,6));assertNotEquals(old.signature(),next.signature());
    }
    @Test void duplicateAndOutsideSectionsDoNotConsumeMoreSpace(){
        var grid=new NearCoverageGrid(0,0,0);grid.mark(1,2,3);grid.mark(1,2,3);grid.mark(32,0,0);grid.mark(-1,0,0);grid.mark(0,64,0);grid.freeze();assertEquals(1,grid.cells());
    }
    @Test void tallDimensionsKeepTheCameraInsideTheVerticalGrid(){
        assertEquals(-4,NearCoverageGrid.verticalBase(11,-4,19));
        int base=NearCoverageGrid.verticalBase(190,-128,255);assertTrue(base<=190&&190<base+64);
        assertEquals(192,NearCoverageGrid.verticalBase(255,-128,255));
    }
}
