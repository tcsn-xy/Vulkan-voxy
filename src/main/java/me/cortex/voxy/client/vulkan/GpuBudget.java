package me.cortex.voxy.client.vulkan;
/** Counts committed and retired allocations until the engine actually destroys them. */
public final class GpuBudget {
    private final long limit;
    private long used, peak;
    public GpuBudget(long limit) {if(limit<=0)throw new IllegalArgumentException();this.limit=limit;}
    public synchronized void reserve(long bytes) {
        if(bytes<0 || bytes>limit-used) throw new BudgetExceeded(bytes,used,limit);
        used+=bytes;peak=Math.max(peak,used);
    }
    public synchronized void release(long bytes) {if(bytes<0||bytes>used)throw new IllegalStateException("budget underflow");used-=bytes;}
    public synchronized long used(){return used;}
    public synchronized long remaining(){return limit-used;}
    public synchronized long peak(){return peak;}
    public long limit(){return limit;}
    public static final class BudgetExceeded extends RuntimeException {
        public BudgetExceeded(long request,long used,long limit){super("Voxy GPU budget: request="+request+" used="+used+" limit="+limit);}
    }
}
