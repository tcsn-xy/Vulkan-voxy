package me.cortex.voxy.commonImpl.importers;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Bound decompressed bytes independently from the NBT allocation/depth quota. */
public final class LimitedInputStream extends FilterInputStream {
    private long remaining;
    public LimitedInputStream(InputStream input, long limit) {
        super(input);
        if (limit < 0) throw new IllegalArgumentException("Negative stream quota");
        this.remaining = limit;
    }
    @Override public int read() throws IOException {
        if (this.remaining == 0) {
            if (super.read() == -1) return -1;
            throw new IOException("Decompressed chunk exceeds byte quota");
        }
        int value = super.read();
        if (value != -1) this.remaining--;
        return value;
    }
    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
        if (length == 0) return 0;
        if (this.remaining == 0) return this.read() == -1 ? -1 : 0;
        int count = this.in.read(bytes, offset, (int) Math.min(length, this.remaining));
        if (count > 0) this.remaining -= count;
        return count;
    }
    @Override public long skip(long count) throws IOException {
        long skipped = super.skip(Math.min(count, this.remaining));
        this.remaining -= skipped;
        return skipped;
    }
}
