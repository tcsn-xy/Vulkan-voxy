package me.cortex.voxy.client.vulkan;
/** Compact streams retain only a source quad index; geometry and origin data stay in their pages. */
final class QuadStream {
    static final int BYTES=4;
    static long bytes(int capacity){if(capacity<0)throw new IllegalArgumentException();return Math.multiplyExact((long)capacity,BYTES);}
}
