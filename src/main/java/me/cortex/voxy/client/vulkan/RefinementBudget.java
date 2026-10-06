package me.cortex.voxy.client.vulkan;
/** Pressure shrinks the radial frontier gradually; it never replaces the whole view with roots. */
final class RefinementBudget {
    private final int maximum;
    private volatile int limit;
    RefinementBudget(int maximum){this.maximum=maximum;limit=maximum;}
    synchronized boolean pressure(int roots){
        int floor=Math.min(maximum,Math.max(roots+128,maximum/8));
        int next=Math.max(floor,limit-Math.max(8,limit/8));
        boolean changed=next<limit;limit=next;return changed;
    }
    synchronized void recover(){limit=Math.min(maximum,limit+Math.max(8,maximum/32));}
    int limit(){return limit;}
    boolean reduced(){return limit<maximum;}
}
