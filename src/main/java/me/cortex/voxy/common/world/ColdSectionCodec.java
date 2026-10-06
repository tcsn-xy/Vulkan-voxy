package me.cortex.voxy.common.world;

import net.jpountz.lz4.LZ4Factory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Fast Java LZ4, with a raw fallback for incompressible section data. */
final class ColdSectionCodec {
    private static final int RAW_BYTES = 32 * 32 * 32 * Long.BYTES;
    private static final LZ4Factory LZ4 = LZ4Factory.fastestJavaInstance();
    private static final ThreadLocal<byte[]> RAW = ThreadLocal.withInitial(() -> new byte[RAW_BYTES]);
    private static final ThreadLocal<byte[]> ENCODED = ThreadLocal.withInitial(() -> new byte[LZ4.fastCompressor().maxCompressedLength(RAW_BYTES)]);

    static byte[] encode(long[] data) {
        byte[] raw = RAW.get();
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().put(data);
        byte[] encoded = ENCODED.get();
        int size = LZ4.fastCompressor().compress(raw, 0, raw.length, encoded, 0, encoded.length);
        return size < raw.length ? Arrays.copyOf(encoded, size) : Arrays.copyOf(raw, raw.length);
    }

    static void decode(byte[] encoded, long[] data) {
        byte[] raw;
        if (encoded.length == RAW_BYTES) {
            raw = encoded;
        } else {
            raw = RAW.get();
            LZ4.fastDecompressor().decompress(encoded, 0, raw, 0, RAW_BYTES);
        }
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get(data);
    }
}
