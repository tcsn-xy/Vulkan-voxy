package me.cortex.voxy.common.util;

import java.util.concurrent.atomic.AtomicLong;

/** A non-blocking reservation counter. Pending allocations count before allocation. */
public final class ByteBudget {
    private final long limit;
    private final AtomicLong used = new AtomicLong();
    private final AtomicLong peak = new AtomicLong();

    public ByteBudget(long limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative byte limit");
        this.limit = limit;
    }

    public boolean tryReserve(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("Negative reservation");
        long before;
        do {
            before = this.used.get();
            if (bytes > this.limit - before) return false;
        } while (!this.used.compareAndSet(before, before + bytes));
        this.peak.accumulateAndGet(before + bytes, Math::max);
        return true;
    }

    public void release(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("Negative release");
        long before;
        do {
            before = this.used.get();
            if (bytes > before) throw new IllegalStateException("Released unreserved bytes");
        } while (!this.used.compareAndSet(before, before - bytes));
    }

    public long usedBytes() { return this.used.get(); }
    public long limitBytes() { return this.limit; }
    public long peakBytes() { return this.peak.get(); }
}
