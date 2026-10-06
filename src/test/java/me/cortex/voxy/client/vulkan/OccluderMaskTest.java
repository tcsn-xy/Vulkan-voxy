package me.cortex.voxy.client.vulkan;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;
class OccluderMaskTest {
    private static Matrix4f matrix(){return new Matrix4f().setPerspective((float)Math.toRadians(70),3440f/1440,.05f,4096,true);}
    @Test void frontObjectsAreExcludedAndRearObjectsDoNotDisableOcclusion(){
        var mask=new OccluderMask(matrix());mask.cover(-2,-2,20,2,2,24);assertEquals(0,mask.cells());
        mask.cover(-2,-2,-24,2,2,-20);assertTrue(mask.cells()>0);assertTrue(mask.cells()<1024);
        var buffer=ByteBuffer.allocate(OccluderMask.WORDS*4);mask.write(buffer);int count=0;for(int i=0;i<OccluderMask.WORDS;i++)count+=Integer.bitCount(buffer.getInt(i*4));assertEquals(mask.cells(),count);
    }
    @Test void nearPlaneCrossingsDisableEntireHistoryConservatively(){var mask=new OccluderMask(matrix());mask.cover(-2,-2,-2,2,2,2);assertEquals(128*128,mask.cells());}
}
