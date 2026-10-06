package me.cortex.voxy.client.vulkan;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RefinementBudgetTest {
    @Test void pressurePreservesARefinedFrontier(){
        var b=new RefinementBudget(8192);assertTrue(b.pressure(100));assertTrue(b.limit()>7000);
        for(int i=0;i<100;i++)b.pressure(100);
        assertTrue(b.limit()>100);assertTrue(b.reduced());
        for(int i=0;i<100;i++)b.recover();assertEquals(8192,b.limit());
    }
    @Test void coarseRootsCanNeverBeDropped(){var b=new RefinementBudget(256);b.pressure(300);assertEquals(256,b.limit());}
}
