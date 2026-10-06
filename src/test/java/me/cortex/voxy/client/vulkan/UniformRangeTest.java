package me.cortex.voxy.client.vulkan;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UniformRangeTest {
    @Test void includesStd140Vec3Tail(){assertEquals(16,UniformRange.padded(12,512));}
    @Test void neverExceedsAllocation(){assertEquals(12,UniformRange.padded(12,12));}
    @Test void leavesAlignedBlocksAlone(){assertEquals(64,UniformRange.padded(64,1024));}
    @Test void rejectsArithmeticOverflow(){assertEquals(Long.MAX_VALUE,UniformRange.padded(Long.MAX_VALUE,Long.MAX_VALUE));}
}
