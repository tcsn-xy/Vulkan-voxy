package me.cortex.voxy.common.util;

import java.util.concurrent.atomic.AtomicLong;

/** CPU payload budgets shared by all worlds; driver/JVM overhead is measured separately. */
public final class DataMemoryBudget {
    public static final long HEAP_LIMIT = mibProperty("heapBudgetMiB", 384, 128, 1024);
    public static final long NATIVE_LIMIT = mibProperty("nativeBudgetMiB", 256, 64, 1024);
    public static final long ARRAY_REUSE_LIMIT = Math.min(HEAP_LIMIT / 32, mibProperty("arrayReuseMiB", 4, 0, 32));
    public static final ByteBudget COLD_SECTIONS = new ByteBudget(Math.min(HEAP_LIMIT / 4, mibProperty("coldCacheMiB", 64, 0, 256)));
    public static final ByteBudget IMPORT_PAYLOADS = new ByteBudget(Math.min(HEAP_LIMIT / 8, mibProperty("importQueueMiB", 16, 4, 64)));
    public static final ByteBudget INGEST_SNAPSHOTS = new ByteBudget(Math.min(HEAP_LIMIT / 8, mibProperty("ingestQueueMiB", 32, 4, 128)));
    public static final long CHUNK_NBT_LIMIT = Math.min(HEAP_LIMIT / 16, mibProperty("chunkNbtMiB", 8, 2, 32));
    public static final long CHUNK_STREAM_LIMIT = mibProperty("chunkStreamMiB", 4, 1, 16);
    public static final ByteBudget DATABASE_CACHE = new ByteBudget(Math.min(NATIVE_LIMIT / 4, mibProperty("databaseCacheMiB", 64, 8, 256)));
    public static final ByteBudget DATABASE_MEMTABLES = new ByteBudget(Math.min(NATIVE_LIMIT / 8, mibProperty("databaseMemtablesMiB", 32, 8, 128)));
    public static final ByteBudget SECTION_ARRAYS = new ByteBudget(Math.min(HEAP_LIMIT / 3, mibProperty("activeSectionsMiB", 128, 16, 512)));
    private static final AtomicLong SECTION_ARRAY_BYTES = new AtomicLong();
    private static final AtomicLong REUSE_BYTES = new AtomicLong();
    private static volatile boolean gpuPressure;

    private DataMemoryBudget() {}

    public static int integerProperty(String name, int fallback, int min, int max) {
        int value = Integer.getInteger("voxy.vulkan." + name, fallback);
        return Math.clamp(value, min, max);
    }

    public static long mibProperty(String name, int fallback, int min, int max) {
        return (long) integerProperty(name, fallback, min, max) << 20;
    }

    public static void sectionAllocated(long bytes) { SECTION_ARRAY_BYTES.addAndGet(bytes); }
    public static void sectionDiscarded(long bytes) { SECTION_ARRAY_BYTES.addAndGet(-bytes); SECTION_ARRAYS.release(bytes); }
    public static void arrayReused(long bytes) { REUSE_BYTES.addAndGet(-bytes); }
    public static void arrayPooled(long bytes) { REUSE_BYTES.addAndGet(bytes); }
    public static long sectionArrayBytes() { return SECTION_ARRAY_BYTES.get(); }
    public static long arrayReuseBytes() { return REUSE_BYTES.get(); }
    public static long retainedPayloadBytes() {
        return sectionArrayBytes() + COLD_SECTIONS.usedBytes() + IMPORT_PAYLOADS.usedBytes() + INGEST_SNAPSHOTS.usedBytes();
    }
    public static void setGpuPressure(boolean value){gpuPressure=value;}
    public static boolean allowBackgroundImport() {
        // Reserve heap for model metadata, snapshots, NBT and render worker scratch.
        return !gpuPressure && retainedPayloadBytes() < HEAP_LIMIT * 3 / 4 && SECTION_ARRAYS.usedBytes() < SECTION_ARRAYS.limitBytes() * 7 / 8;
    }
}
