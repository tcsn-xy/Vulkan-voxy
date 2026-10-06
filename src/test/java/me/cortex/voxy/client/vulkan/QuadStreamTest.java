package me.cortex.voxy.client.vulkan;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class QuadStreamTest {
    @Test void fourMillionVisibleQuadsNeedSixteenMiB(){assertEquals(16L*1024*1024,QuadStream.bytes(4*1024*1024));assertThrows(IllegalArgumentException.class,()->QuadStream.bytes(-1));}
}
