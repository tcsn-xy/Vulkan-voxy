package me.cortex.voxy.client.vulkan;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GpuMemoryTest {
    @Test void retiredAllocationsRemainChargedUntilReleased(){var b=new GpuBudget(128);b.reserve(80);assertThrows(GpuBudget.BudgetExceeded.class,()->b.reserve(49));assertEquals(80,b.used());b.reserve(48);b.release(80);b.reserve(80);assertEquals(128,b.peak());}
    @Test void budgetRejectsInvalidRelease(){var b=new GpuBudget(100);b.reserve(20);assertThrows(IllegalStateException.class,()->b.release(21));assertThrows(GpuBudget.BudgetExceeded.class,()->b.reserve(-1));}
    @Test void rangesMergeAfterOutOfOrderRetirement(){var a=new RangeAllocator(256);int x=a.allocate(64),y=a.allocate(64),z=a.allocate(128);assertEquals(-1,a.allocate(1));a.release(y,64);a.release(x,64);a.release(z,128);assertEquals(0,a.allocate(256));}
    @Test void detectsOverlapAndDoubleFree(){var a=new RangeAllocator(256);int p=a.allocate(128);a.release(p,128);assertThrows(IllegalStateException.class,()->a.release(p,128));assertThrows(IllegalStateException.class,()->a.release(100,50));}
    @Test void randomAllocationAndRetirementNeverLoseBytes(){var a=new RangeAllocator(4096);var allocations=new HashMap<Integer,Integer>();var random=new Random(42);for(int i=0;i<10000;i++){
        if(allocations.isEmpty()||random.nextBoolean()){int size=(random.nextInt(16)+1)*8;int p=a.allocate(size);if(p>=0)allocations.put(p,size);}else{int p=allocations.keySet().iterator().next();a.release(p,allocations.remove(p));}
        assertEquals(4096,a.freeBytes()+allocations.values().stream().mapToInt(Integer::intValue).sum());
    }for(var e:allocations.entrySet())a.release(e.getKey(),e.getValue());assertEquals(0,a.allocate(4096));}
}
