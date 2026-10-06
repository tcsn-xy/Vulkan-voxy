package me.cortex.voxy.client.vulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import static org.lwjgl.vulkan.VK10.*;
/** Availability-only queries: the CPU never waits for a GPU timing result. */
public final class VulkanFrameTiming implements AutoCloseable {
    private final long pool;
    private final double period;
    public double gpuMillis=Double.NaN;
    public VulkanFrameTiming(){try(var s=MemoryStack.stackPush()){
        var props=VkPhysicalDeviceProperties.calloc(s);vkGetPhysicalDeviceProperties(VulkanInterop.device().vkDevice().getPhysicalDevice(),props);period=props.limits().timestampPeriod();
        var ci=VkQueryPoolCreateInfo.calloc(s).sType$Default().queryType(VK_QUERY_TYPE_TIMESTAMP).queryCount(4);var out=s.mallocLong(1);
        VulkanInterop.check(vkCreateQueryPool(VulkanInterop.device().vkDevice(),ci,null,out),"timestamp query pool");pool=out.get(0);
    }}
    public void reset(VkCommandBuffer cb,int slot){try(var s=MemoryStack.stackPush()){
        var out=s.callocLong(4);int result=vkGetQueryPoolResults(VulkanInterop.device().vkDevice(),pool,slot*2,2,out,16,VK_QUERY_RESULT_64_BIT|VK_QUERY_RESULT_WITH_AVAILABILITY_BIT);
        if(result==VK_SUCCESS&&out.get(1)!=0&&out.get(3)!=0)gpuMillis=(out.get(2)-out.get(0))*period/1e6;
        vkCmdResetQueryPool(cb,pool,slot*2,2);
    }}
    public void markerBegin(VkCommandBuffer cb,int slot){vkCmdWriteTimestamp(cb,VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,pool,slot*2);}
    public void begin(VkCommandBuffer cb,int slot){reset(cb,slot);markerBegin(cb,slot);}
    public void end(VkCommandBuffer cb,int slot){vkCmdWriteTimestamp(cb,VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,pool,slot*2+1);}
    @Override public void close(){VulkanInterop.encoder().queueForDestroy(()->vkDestroyQueryPool(VulkanInterop.device().vkDevice(),pool,null));}
}
