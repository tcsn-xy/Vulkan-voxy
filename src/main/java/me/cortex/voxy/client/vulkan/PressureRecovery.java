package me.cortex.voxy.client.vulkan;
final class PressureRecovery {
    static boolean canResume(long geometryAvailable,long remaining,long limit){long needed=Math.min(VulkanGeometry.PAGE_BYTES*3L/2,limit/4),headroom=Math.min(VulkanGeometry.PAGE_BYTES/2,limit/32);return geometryAvailable>=needed&&remaining>=headroom;}
}
