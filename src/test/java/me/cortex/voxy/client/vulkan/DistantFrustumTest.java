package me.cortex.voxy.client.vulkan;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
class DistantFrustumTest {
    private static Matrix4f projection(){return new Matrix4f().setPerspective((float)Math.toRadians(70),3440f/1440,.05f,4096f,true);}
    @Test void conservativePlanesAgreeWithGpuCornerRejection(){
        var random=new Random(42);
        for(int angle=0;angle<12;angle++){
            var matrix=projection().rotateY(angle*(float)Math.PI/6).rotateX(.4f);var frustum=new DistantFrustum(matrix);
            for(int n=0;n<2000;n++){
                float x=random.nextFloat()*8192-4096,y=random.nextFloat()*8192-4096,z=random.nextFloat()*8192-4096,size=32<<random.nextInt(5);
                int[] rejected=new int[5];
                for(int corner=0;corner<8;corner++){
                    var p=matrix.transform(new Vector4f(x+((corner&1)!=0?size:0),y+((corner&2)!=0?size:0),z+((corner&4)!=0?size:0),1));
                    if(p.x < -p.w)rejected[0]++;if(p.x>p.w)rejected[1]++;if(p.y < -p.w)rejected[2]++;if(p.y>p.w)rejected[3]++;if(p.z>p.w)rejected[4]++;
                }
                boolean gpuVisible=true;for(int count:rejected)gpuVisible&=count<8;
                if(gpuVisible)assertTrue(frustum.intersects(x,y,z,size),"CPU must not remove anything accepted by GPU");
            }
        }
    }
    @Test void retainsCameraIntersectionsAndRejectsRear(){
        var f=new DistantFrustum(projection());assertTrue(f.intersects(-16,-16,-16,32));assertTrue(f.intersects(-16,-16,-500,32));assertFalse(f.intersects(-16,-16,100,32));
    }
    @Test void initialNonFiniteProjectionDoesNotDiscardTerrain(){assertTrue(new DistantFrustum(new Matrix4f().m00(Float.POSITIVE_INFINITY)).intersects(0,0,0,32));}
}
