package me.cortex.voxy.client.vulkan;
/** A new limit takes effect only after every allocation from the previous renderer retires. */
public final class GpuBudgetManager {
    private GpuBudget current;
    public GpuBudget forLimit(long bytes) {
        if(current==null||(current.used()==0&&current.limit()!=bytes))current=new GpuBudget(bytes);
        return current;
    }
    public boolean readyForRenderer(long bytes) { return forLimit(bytes).used()==0; }
}
