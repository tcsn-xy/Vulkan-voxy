package me.cortex.voxy.common.world;

import java.util.concurrent.atomic.AtomicLongArray;

/** Fixed-memory live update protection. False positives skip imports; false negatives never overwrite live data. */
public final class LiveUpdateGuard {
    private final AtomicLongArray words;
    private final long bitMask;
    public LiveUpdateGuard(int wordBits) {
        if (wordBits < 4 || wordBits > 24) throw new IllegalArgumentException("Invalid word count");
        this.words = new AtomicLongArray(1 << wordBits);
        this.bitMask = ((long) this.words.length() << 6) - 1;
    }
    private static long mix(long seed) {
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }
    public void mark(long key) {
        long hash = mix(key), step = mix(key ^ 0x9E3779B97F4A7C15L) | 1;
        for (int i = 0; i < 4; i++, hash += step) {
            long bit = hash & this.bitMask;
            int index = (int) (bit >>> 6);
            long mask = 1L << (int) bit;
            this.words.getAndUpdate(index, old -> old | mask);
        }
    }
    public boolean contains(long key) {
        long hash = mix(key), step = mix(key ^ 0x9E3779B97F4A7C15L) | 1;
        for (int i = 0; i < 4; i++, hash += step) {
            long bit = hash & this.bitMask;
            if ((this.words.get((int) (bit >>> 6)) & (1L << (int) bit)) == 0) return false;
        }
        return true;
    }
    public long retainedBytes() { return (long) this.words.length() * Long.BYTES; }
}
