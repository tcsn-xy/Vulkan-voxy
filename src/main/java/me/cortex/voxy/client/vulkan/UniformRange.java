package me.cortex.voxy.client.vulkan;
/** std140 struct tail padding, bounded by the actual parent allocation. */
public final class UniformRange {
    public static long padded(long length,long available){
        if(length<=0||length>Long.MAX_VALUE-15)return length;
        long padded=(length+15)&~15L;return padded<=available?padded:length;
    }
}
