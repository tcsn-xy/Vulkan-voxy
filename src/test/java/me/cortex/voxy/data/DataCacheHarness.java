package me.cortex.voxy.common.world;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.DataMemoryBudget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Real tracker/section integration with a synthetic loader; no GPU or world registry needed. */
public final class DataCacheHarness {
    public static void main(String[] args) throws Exception {
        System.setProperty("voxy.vulkan.heapBudgetMiB", "128");
        System.setProperty("voxy.vulkan.activeSectionsMiB", "16");
        System.setProperty("voxy.vulkan.coldCacheMiB", "8");
        System.setProperty("voxy.vulkan.arrayReuseMiB", "1");
        Logger.SHUTUP = true;
        ActiveSectionTracker tracker = new ActiveSectionTracker(4, section -> {
            Arrays.fill(section._unsafeGetRawDataArray(), section.key ^ 0x1111111111111111L);
            return 0;
        }, 128);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            int worker = i;
            threads[i] = new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 1500; j++) {
                        WorldSection section = tracker.acquire(0, (j * 7 + worker) % 48, 0, 0, false);
                        long expected = section.key ^ 0x1111111111111111L;
                        if (section._unsafeGetRawDataArray()[0] != expected || section._unsafeGetRawDataArray()[32767] != expected) throw new AssertionError("Cache roundtrip/race corrupted section");
                        section.acquire();
                        section.release();
                        section.release();
                    }
                } catch (Throwable e) { failure.compareAndSet(null, e); }
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join(30000);
        for (Thread thread : threads) if (thread.isAlive()) throw new AssertionError("Concurrent tracker deadlocked");
        if (failure.get() != null) throw new AssertionError("Tracker concurrency failed", failure.get());
        if (tracker.getLoadedCacheCount() != 0 || DataMemoryBudget.COLD_SECTIONS.usedBytes() > DataMemoryBudget.COLD_SECTIONS.limitBytes()) throw new AssertionError("Reference/cache budget leak");
        tracker.trimColdCache();
        if (DataMemoryBudget.COLD_SECTIONS.usedBytes() != 0) throw new AssertionError("Cold cache not released");

        var references = new ArrayList<WorldSection>();
        try {
            for (int i = 0; i < 65; i++) references.add(tracker.acquire(0, 1000 + i, 0, 0, false));
            throw new AssertionError("Raw section budget did not reject allocation65");
        } catch (IllegalStateException expected) {
            if (references.size() != 64 || tracker.getLoadedCacheCount() != 64) throw new AssertionError("Failed load corrupted active count");
        }
        for (WorldSection section : references) section.release();
        tracker.trimColdCache();
        if (tracker.getLoadedCacheCount() != 0 || DataMemoryBudget.sectionArrayBytes() > (1L << 20)) throw new AssertionError("World exit retained raw arrays");
        if (DataMemoryBudget.arrayReuseBytes() > DataMemoryBudget.ARRAY_REUSE_LIMIT || DataMemoryBudget.SECTION_ARRAYS.usedBytes() != DataMemoryBudget.sectionArrayBytes()) throw new AssertionError("Array accounting incorrect");

        ActiveSectionTracker broken = new ActiveSectionTracker(2, section -> { throw new IllegalArgumentException("Synthetic load failure"); }, 2);
        for (int i = 0; i < 4; i++) {
            try { broken.acquire(0, 0, 0, 0, false); throw new AssertionError("Loader exception swallowed"); }
            catch (IllegalArgumentException expected) { /* failed futures and references must be removed */ }
        }
        if (broken.getLoadedCacheCount() != 0) throw new AssertionError("Failed loader retained a holder");
        System.out.println("PASS realsection cache:12000 concurrent cycles, strict16MiB rawcap,1MiB reusecap, clean cold eviction and failed-loader recovery");
    }
}
