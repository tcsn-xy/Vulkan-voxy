package me.cortex.voxy.client.vulkan;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MeshClustersTest {
    @Test void reorderingPreservesEveryQuadAndConservativeBounds(){
        var random=new Random(42);
        for(int trial=0;trial<100;trial++){
            int[] ranges=new int[8];var input=new ArrayList<Long>();
            for(int group=0;group<8;group++){
                ranges[group]=input.size();
                for(int i=0;i<100;i++){
                    int face=group<2?random.nextInt(6):group-2;
                    long q=face|((long)random.nextInt(16)<<3)|((long)random.nextInt(16)<<7)|((long)random.nextInt(16)<<21)|((long)random.nextInt(16)<<16)|((long)random.nextInt(16)<<11)|((long)(i+group*100)<<32);
                    input.add(q);
                }
            }
            long[] original=input.stream().mapToLong(Long::longValue).toArray(),output=new long[original.length];
            var clusters=MeshClusters.reorder(ranges,original.length,i->original[(int)i],(i,q)->output[i]=q);
            var sorted=original.clone();var sortedOut=output.clone();Arrays.sort(sorted);Arrays.sort(sortedOut);assertArrayEquals(sorted,sortedOut);
            assertTrue(clusters.length<=5);assertEquals(original.length,Arrays.stream(clusters).mapToInt(MeshClusters.Cluster::count).sum());
            for(var cluster:clusters){
                for(int i=cluster.offsets()[0];i<cluster.end();i++){
                    int[] box=MeshClusters.bounds(output[i]);
                    for(int axis=0;axis<3;axis++){
                        int lo=(cluster.aabb()>>>(axis*5))&31,size=((cluster.aabb()>>>(15+axis*5))&31)+1;
                        assertTrue(lo<=box[axis]);assertTrue(lo+size>=box[axis+3]);
                    }
                }
            }
            var water=clusters[clusters.length-1];assertEquals(100,water.offsets()[1]-water.offsets()[0]);
            assertArrayEquals(Arrays.copyOfRange(original,0,100),Arrays.copyOfRange(output,water.offsets()[0],water.offsets()[1]));
        }
    }
}
