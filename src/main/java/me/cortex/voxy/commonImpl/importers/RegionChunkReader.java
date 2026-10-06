package me.cortex.voxy.commonImpl.importers;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Positional streaming reads: 8 KiB header plus one bounded compressed chunk. */
public final class RegionChunkReader implements AutoCloseable {
    public record Chunk(int compression, byte[] bytes) {}
    public static final class IncompleteChunkException extends IOException {
        public IncompleteChunkException(String message) { super(message); }
    }
    @FunctionalInterface public interface ExternalReader { byte[] read(int x, int z, int maxBytes) throws IOException; }
    private final FileChannel channel;
    private final ByteBuffer header = ByteBuffer.allocate(8192);
    private final int regionX, regionZ, maxChunkBytes;
    private final ExternalReader external;

    public RegionChunkReader(Path path, int regionX, int regionZ, int maxChunkBytes) throws IOException {
        this(path, regionX, regionZ, maxChunkBytes, (x, z, max) -> readLimited(Files.newInputStream(path.resolveSibling("c." + x + "." + z + ".mcc")), max));
    }
    public RegionChunkReader(Path path, int regionX, int regionZ, int maxChunkBytes, ExternalReader external) throws IOException {
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.maxChunkBytes = maxChunkBytes;
        this.external = external;
        try { readFully(this.channel, this.header, 0); }
        catch (EOFException e) { this.channel.close(); throw new IncompleteChunkException("Region header is still incomplete"); }
        catch (IOException e) { this.channel.close(); throw e; }
    }
    public boolean hasChunk(int index) {
        checkIndex(index);
        return this.header.getInt(index * 4) != 0;
    }
    private static void checkIndex(int index) { if (index < 0 || index >= 1024) throw new IndexOutOfBoundsException(index); }
    public Chunk readChunk(int index) throws IOException {
        try { return this.readChunk0(index); }
        catch (EOFException e) { throw new IncompleteChunkException("Region file changed during read"); }
    }
    private Chunk readChunk0(int index) throws IOException {
        checkIndex(index);
        int location = this.readLocation(index), sector = location >>> 8, count = location & 255;
        if (location == 0) {
            if (this.header.getInt(index * 4) != 0) throw new IncompleteChunkException("Chunk location was cleared during import");
            return null;
        }
        long start = (long) sector * 4096;
        if (sector < 2 || count == 0) throw new IOException("Invalid chunk sector address/count");
        // Minecraft need not pad the final allocated sector. Validate only bytes actually consumed.
        if (start + 5 > this.channel.size()) throw new IncompleteChunkException("Chunk prefix has not reached disk yet");
        ByteBuffer prefix = ByteBuffer.allocate(5);
        readFully(this.channel, prefix, start);
        int size = prefix.getInt(0), flags = Byte.toUnsignedInt(prefix.get(4));
        if ((flags & 128) != 0) {
            if (size != 1) throw new IOException("Chunk has both internal and external streams");
            byte[] bytes = this.external.read((this.regionX << 5) + (index & 31), (this.regionZ << 5) + (index >>> 5), this.maxChunkBytes);
            if (location != this.readLocation(index)) throw new IncompleteChunkException("External chunk location changed during read");
            return new Chunk(flags & 127, bytes);
        }
        int payload = size - 1;
        if (size < 1 || payload > count * 4096 - 5 || payload > this.maxChunkBytes) throw new IOException("Invalid or oversized chunk stream: " + payload);
        if (start + 5L + payload > this.channel.size()) throw new IncompleteChunkException("Chunk payload has not reached disk yet");
        byte[] bytes = new byte[payload];
        readFully(this.channel, ByteBuffer.wrap(bytes), start + 5);
        if (location != this.readLocation(index)) throw new IncompleteChunkException("Chunk location changed during read");
        return new Chunk(flags, bytes);
    }
    private int readLocation(int index) throws IOException {
        ByteBuffer location = ByteBuffer.allocate(4);
        readFully(this.channel, location, (long) index * 4);
        return location.getInt(0);
    }
    private static void readFully(FileChannel channel, ByteBuffer bytes, long offset) throws IOException {
        while (bytes.hasRemaining()) {
            int count = channel.read(bytes, offset + bytes.position());
            if (count < 0) throw new EOFException("Truncated region file");
            if (count == 0) Thread.onSpinWait();
        }
    }
    public static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        try (input; var output = new java.io.ByteArrayOutputStream(Math.min(maxBytes, 8192))) {
            byte[] scratch = new byte[8192];
            int count, size = 0;
            while ((count = input.read(scratch)) != -1) {
                size = Math.addExact(size, count);
                if (size > maxBytes) throw new IOException("External chunk exceeds compressed byte limit");
                output.write(scratch, 0, count);
            }
            return output.toByteArray();
        }
    }
    @Override public void close() throws IOException { this.channel.close(); }
}
