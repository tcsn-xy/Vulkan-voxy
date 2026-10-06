package me.cortex.voxy.common.world;

import me.cortex.voxy.common.util.ByteBudget;
import me.cortex.voxy.commonImpl.importers.LimitedInputStream;
import me.cortex.voxy.commonImpl.importers.RegionChunkReader;
import me.cortex.voxy.commonImpl.importers.ImportedSkyLight;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Run directly with Java25; no Minecraft bootstrap, GPU or JUnit required. */
public final class DataBoundsTest {
    private static int cases;
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); cases++; }
    @FunctionalInterface private interface IoRunnable { void run() throws IOException; }
    private static void rejects(IoRunnable run, String message) throws IOException {
        try { run.run(); throw new AssertionError(message); }
        catch (IOException expected) { cases++; }
    }
    public static void main(String[] args) throws Exception {
        ByteBudget budget = new ByteBudget(64);
        check(!budget.tryReserve(65), "Oversized reservation accepted");
        check(budget.tryReserve(64) && !budget.tryReserve(1), "Limit violated");
        budget.release(64);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 20000; j++) if (budget.tryReserve(7)) { Thread.onSpinWait(); budget.release(7); }
                } catch (Throwable e) { failure.set(e); }
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        check(failure.get() == null && budget.usedBytes() == 0 && budget.peakBytes() <= 64, "Concurrent reservations leaked/exceeded cap");
        try { budget.release(1); throw new AssertionError("Double release accepted"); } catch (IllegalStateException expected) { cases++; }

        long[] source = new long[32768], restored = new long[32768];
        Arrays.fill(source, 0xFFFFFFFFFFFFFFFFL);
        byte[] uniform = ColdSectionCodec.encode(source);
        ColdSectionCodec.decode(uniform, restored);
        check(Arrays.equals(source, restored) && uniform.length < 2048, "Uniform section did not roundtrip/compress");
        for (int i = 0; i < source.length; i++) source[i] = (i >>> 5) * 129L;
        ColdSectionCodec.decode(ColdSectionCodec.encode(source), restored);
        check(Arrays.equals(source, restored), "Varied section roundtrip failed");
        Random random = new Random(0x1234ABCDL);
        for (int i = 0; i < source.length; i++) source[i] = random.nextLong();
        byte[] noise = ColdSectionCodec.encode(source);
        ColdSectionCodec.decode(noise, restored);
        check(Arrays.equals(source, restored) && noise.length <= source.length * 8, "Incompressible raw fallback failed");

        LiveUpdateGuard guard = new LiveUpdateGuard(12);
        for (long i = 0; i < 10000; i++) guard.mark(i * 53711);
        for (long i = 0; i < 10000; i++) if (!guard.contains(i * 53711)) throw new AssertionError("Live update false negative");
        check(guard.retainedBytes() == 32768, "Live guard changed capacity");
        check(!new LiveUpdateGuard(12).contains(123), "Empty guard marked an update");

        check(Byte.toUnsignedInt(StoredLighting.compose(0xA7, -1, -1, true, false)) == 0xA7, "Unload erased known light");
        check(Byte.toUnsignedInt(StoredLighting.compose(0xA7, 0, 0, true, true)) == 0, "Explicit zero light was ignored");
        check(Byte.toUnsignedInt(StoredLighting.compose(-1, -1, -1, true, true)) == 15, "Exposed unload sky fallback missing");
        check(Byte.toUnsignedInt(StoredLighting.compose(-1, -1, -1, true, false)) == 0, "Unknown enclosed sky brightened");
        check(Byte.toUnsignedInt(StoredLighting.compose(0xA7, -1, -1, false, true)) == 0xA0, "No-sky unload preserved sky light");
        check(Byte.toUnsignedInt(StoredLighting.compose(0xA7, 5, -1, true, false)) == 0x57, "Known sky did not survive missing-light update");

        byte[] topSky = new byte[2048];
        Arrays.fill(topSky, (byte) 0xFF);
        topSky[0] = (byte) 0x75; // x0=5/x1=7 at the nearest upper layer's bottom plane.
        var implicit = new ImportedSkyLight(true, true, java.util.Map.of(4, topSky), null);
        check(implicit.resolve(4).get(0, 1, 0) == 15, "Explicit skylight y was flattened");
        check(implicit.resolve(3).get(0, 15, 0) == 5 && implicit.resolve(1).get(1, 7, 0) == 7, "Missing skylight did not inherit bottom plane");
        check(implicit.resolve(5).get(0, 0, 0) == 15, "Above top skylight became black");
        check(new ImportedSkyLight(false, true, java.util.Map.of(4, topSky), null).resolve(5).get(0, 0, 0) == 0, "No-skylight dimension brightened");
        check(new ImportedSkyLight(true, false, java.util.Map.of(4, topSky), null).resolve(5).get(0, 0, 0) == 0, "Unlit chunk was treated as light-correct");
        int[] heights = new int[256]; Arrays.fill(heights, 90);
        var heightGuard = new ImportedSkyLight(true, true, java.util.Map.of(4, topSky), heights).resolve(5);
        check(heightGuard.get(0, 9, 0) == 0 && heightGuard.get(0, 10, 0) == 15, "Full-sky fallback ignored surface height");
        check(new ImportedSkyLight(true, true, java.util.Map.of(), null).resolve(5).get(0, 0, 0) == 0, "Unknown lighting received invented full sky");

        byte[] bytes = {1, 2, 3};
        try (var input = new LimitedInputStream(new ByteArrayInputStream(bytes), 3)) {
            check(Arrays.equals(input.readAllBytes(), bytes) && input.read() == -1, "Exact-quota EOF is wrong");
        }
        rejects(() -> new LimitedInputStream(new ByteArrayInputStream(bytes), 2).readAllBytes(), "Decompression quota was ignored");
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compressed)) { gzip.write(new byte[1 << 20]); }
        rejects(() -> {
            try (var input = new LimitedInputStream(new GZIPInputStream(new ByteArrayInputStream(compressed.toByteArray())), 32768)) { input.readAllBytes(); }
        }, "Compression bomb exceeded quota");
        rejects(() -> RegionChunkReader.readLimited(new ByteArrayInputStream(new byte[8193]), 8192), "External chunk stream exceeded quota");

        var directory = Files.createTempDirectory("voxy-data-bounds-");
        try {
            var file = directory.resolve("r.-1.-1.mca");
            ByteBuffer region = ByteBuffer.allocate(4 * 4096);
            region.putInt(0, (2 << 8) | 1);
            region.putInt(1023 * 4, (3 << 8) | 1);
            region.putInt(2 * 4096, 5).put(2 * 4096 + 4, (byte) 3);
            region.position(2 * 4096 + 5); region.put(new byte[]{11, 22, 33, 44});
            region.putInt(3 * 4096, 1).put(3 * 4096 + 4, (byte) 131);
            Files.write(file, region.array());
            Files.write(directory.resolve("c.-1.-1.mcc"), new byte[]{55, 66});
            try (var reader = new RegionChunkReader(file, -1, -1, 1024)) {
                check(reader.hasChunk(0) && !reader.hasChunk(1), "Chunk header lookup failed");
                check(reader.readChunk(1) == null, "Empty chunk returned data");
                check(Arrays.equals(reader.readChunk(0).bytes(), new byte[]{11, 22, 33, 44}), "Internal chunk read failed");
                check(Arrays.equals(reader.readChunk(1023).bytes(), new byte[]{55, 66}), "External negative-coordinate read failed");
            }
            Files.write(file, Arrays.copyOf(region.array(), 2 * 4096 + 9));
            try (var reader = new RegionChunkReader(file, -1, -1, 8192)) {
                check(Arrays.equals(reader.readChunk(0).bytes(), new byte[]{11, 22, 33, 44}), "Partially padded final sector rejected");
            }
            region.putInt(2 * 4096, 5000);
            Files.write(file, region.array());
            rejects(() -> { try (var reader = new RegionChunkReader(file, -1, -1, 8192)) { reader.readChunk(0); } }, "Truncated sector payload accepted");
            region.putInt(0, (8 << 8) | 1);
            Files.write(file, region.array());
            rejects(() -> { try (var reader = new RegionChunkReader(file, -1, -1, 8192)) { reader.readChunk(0); } }, "Outside-file sector accepted");
            Files.write(file, new byte[8191]);
            rejects(() -> { new RegionChunkReader(file, -1, -1, 8192).close(); }, "Truncated header accepted");
        } finally {
            try (var entries = Files.list(directory)) { for (var entry : entries.toList()) Files.delete(entry); }
            Files.delete(directory);
        }
        System.out.println("PASS " + cases + " data bounds scenarios; 160000 concurrent reservation attempts and 10000 live protection checks");
    }
}
