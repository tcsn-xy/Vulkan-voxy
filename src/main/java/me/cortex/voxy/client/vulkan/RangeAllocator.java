package me.cortex.voxy.client.vulkan;
import java.util.TreeMap;
/** Render-thread allocator. Retired ranges enter it only after engine submit retirement. */
public final class RangeAllocator {
    private final TreeMap<Integer,Integer> free=new TreeMap<>();
    private final int capacity;
    public RangeAllocator(int capacity){if(capacity<=0)throw new IllegalArgumentException();this.capacity=capacity;free.put(0,capacity);}
    public int allocate(int bytes){
        if(bytes<=0)throw new IllegalArgumentException();
        for(var e:free.entrySet())if(e.getValue()>=bytes){int start=e.getKey(),size=e.getValue();free.remove(start);if(size>bytes)free.put(start+bytes,size-bytes);return start;}
        return -1;
    }
    public void release(int start,int bytes){
        if(bytes<=0||start<0||start>capacity-bytes)throw new IllegalArgumentException();
        var lo=free.floorEntry(start);var hi=free.ceilingEntry(start);
        if(lo!=null&&lo.getKey()+lo.getValue()>start || hi!=null&&start+bytes>hi.getKey())throw new IllegalStateException("double free/overlap");
        if(lo!=null&&lo.getKey()+lo.getValue()==start){start=lo.getKey();bytes+=lo.getValue();free.remove(lo.getKey());}
        if(hi!=null&&start+bytes==hi.getKey()){bytes+=hi.getValue();free.remove(hi.getKey());}
        free.put(start,bytes);
    }
    public int freeBytes(){return free.values().stream().mapToInt(Integer::intValue).sum();}
}
