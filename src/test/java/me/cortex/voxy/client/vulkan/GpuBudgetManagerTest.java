package me.cortex.voxy.client.vulkan;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GpuBudgetManagerTest {
    @Test void cannotConstructAnotherRendererAgainstPendingRetirement(){
        var manager=new GpuBudgetManager();var old=manager.forLimit(128);old.reserve(120);
        assertFalse(manager.readyForRenderer(128));
        assertThrows(GpuBudget.BudgetExceeded.class,()->old.reserve(33));
        old.release(80);assertFalse(manager.readyForRenderer(128));
        old.release(40);assertTrue(manager.readyForRenderer(128));
        manager.forLimit(128).reserve(33);assertEquals(33,manager.forLimit(128).used());
    }
    @Test void aChangedLimitNeverDiscountsPendingAllocations(){
        var manager=new GpuBudgetManager();var old=manager.forLimit(128);old.reserve(80);
        assertSame(old,manager.forLimit(64));assertEquals(128,manager.forLimit(64).limit());
        assertFalse(manager.readyForRenderer(64));old.release(80);
        var next=manager.forLimit(64);assertNotSame(old,next);assertEquals(64,next.limit());
        next.reserve(60);assertEquals(0,old.used());assertEquals(60,next.used());
    }
}
