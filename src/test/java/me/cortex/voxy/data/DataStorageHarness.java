package me.cortex.voxy.common.world;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.voxy.common.util.DataMemoryBudget;
import me.cortex.voxy.common.util.MemoryBuffer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

/** Native RocksDB/upstream Voxy-format roundtrip, isolated temporary databases only. */
public final class DataStorageHarness {
    private static long value(int index, int salt) {
        return ((long) ((index + salt) & 255) << 56) | ((long) (1 + ((index >>> 5) + salt) % 31) << 27) | ((long) (salt & 127) << 47);
    }
    private static WorldSection raw(int number) {
        WorldSection section = WorldSection._createRawUntrackedUnsafeSection(0, number, 0, -7);
        section.acquire();
        return section;
    }
    private static void write(SectionSerializationStorage storage, int number) {
        WorldSection section = raw(number);
        try {
            long[] data = section._unsafeGetRawDataArray();
            for (int i = 0; i < data.length; i++) data[i] = value(i, number);
            section._unsafeSetNonEmptyChildren((byte) 255);
            section.recordLiveInput(number & 1, (number >>> 1) & 1, (number >>> 2) & 1);
            storage.saveSection(section);
        } finally { section.setNotDirty(); section.release(); }
    }
    private static void read(SectionSerializationStorage storage, int number) {
        WorldSection section = raw(number);
        try {
            if (storage.loadSection(section) != 0) throw new AssertionError("Missing section" + number);
            long[] data = section._unsafeGetRawDataArray();
            for (int i = 0; i < data.length; i++) if (data[i] != value(i, number)) throw new AssertionError("Changed Voxy storage format at" + number + ":" + i);
            if (!section.hasRecordedLiveInput(number & 1, (number >>> 1) & 1, (number >>> 2) & 1)) throw new AssertionError("Live input provenance did not survive storage/restart");
            if (section.getNonEmptyBlockCount() != 32768 || section.getNonEmptyChildren() != (byte) 255) throw new AssertionError("Section occupancy metadata was lost");
        } finally { section.release(); }
    }
    public static void main(String[] args) throws Exception {
        Logger.SHUTUP = true;
        Path root = Files.createTempDirectory("voxy-native-data-");
        long start = System.nanoTime(), peakBlocks = 0, peakWrites = 0;
        try {
            SectionSerializationStorage first = new SectionSerializationStorage(new RocksDBStorageBackend(root.resolve("first").toString()));
            SectionSerializationStorage second = new SectionSerializationStorage(new RocksDBStorageBackend(root.resolve("second").toString()));
            try {
                if (DataMemoryBudget.DATABASE_CACHE.usedBytes() != DataMemoryBudget.DATABASE_CACHE.limitBytes() || DataMemoryBudget.DATABASE_MEMTABLES.usedBytes() != DataMemoryBudget.DATABASE_MEMTABLES.limitBytes()) throw new AssertionError("Per-world native cache duplicated");
                for (int i = 0; i < 1000; i++) {
                    write((i & 1) == 0 ? first : second, i);
                    peakBlocks = Math.max(peakBlocks, RocksDBStorageBackend.getSharedBlockCacheUsage());
                    peakWrites = Math.max(peakWrites, RocksDBStorageBackend.getSharedMemtableUsage());
                }
                first.flush(); second.flush();
                for (int i = 0; i < 1000; i++) {
                    read((i & 1) == 0 ? first : second, i);
                    peakBlocks = Math.max(peakBlocks, RocksDBStorageBackend.getSharedBlockCacheUsage());
                    peakWrites = Math.max(peakWrites, RocksDBStorageBackend.getSharedMemtableUsage());
                }
                AtomicInteger positions = new AtomicInteger();
                first.iteratePositions(0, key -> positions.incrementAndGet());
                second.iteratePositions(0, key -> positions.incrementAndGet());
                if (positions.get() != 1000) throw new AssertionError("Stored-position iteration lost data");
                if (peakBlocks > DataMemoryBudget.DATABASE_CACHE.limitBytes() || peakWrites > DataMemoryBudget.DATABASE_MEMTABLES.limitBytes()) throw new AssertionError("Native cache capacity exceeded");
            } finally { first.close(); second.close(); }
            if (DataMemoryBudget.DATABASE_CACHE.usedBytes() != 0 || DataMemoryBudget.DATABASE_MEMTABLES.usedBytes() != 0) throw new AssertionError("Shared native reservations leaked at close");
            SectionSerializationStorage reopened = new SectionSerializationStorage(new RocksDBStorageBackend(root.resolve("first").toString()));
            try { for (int i = 0; i < 1000; i += 2) read(reopened, i); }
            finally { reopened.close(); }
            if (RocksDBStorageBackend.getSharedBlockCacheUsage() != 0 || RocksDBStorageBackend.getSharedMemtableUsage() != 0) throw new AssertionError("Closed native caches remained live");
            System.out.println("PASS native storage:1000 write/read roundtrips +500 reads after reopen; peak block cache=" + peakBlocks + ", peak memtable charge=" + peakWrites + ", tracked native scratch=" + MemoryBuffer.getTotalSize() + ", seconds=" + (System.nanoTime() - start) / 1e9);
        } finally {
            try (var files = Files.walk(root)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); }
        }
    }
}
