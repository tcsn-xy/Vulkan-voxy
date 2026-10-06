package me.cortex.voxy.client.vulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.*;
import org.lwjgl.vulkan.*;
import java.nio.ByteBuffer;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.util.vma.Vma.*;
/** Uses the game's allocator; persistent mapping exploits Apple unified memory. */
public final class VulkanBuffer implements AutoCloseable {
    public final long handle,allocation,size,address;
    private final long allocationBytes;
    private final GpuBudget budget;
    private boolean closed;
    public VulkanBuffer(GpuBudget budget,long bytes,int usage,boolean mapped) {
        this.budget=budget;this.size=bytes;
        long charged=bytes,createdBuffer=0,createdAllocation=0;budget.reserve(charged);
        try(MemoryStack s=MemoryStack.stackPush()){
            var ci=VkBufferCreateInfo.calloc(s).sType$Default().size(bytes).usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            var ai=VmaAllocationCreateInfo.calloc(s).usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            if(mapped)ai.flags(VMA_ALLOCATION_CREATE_MAPPED_BIT|VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT).requiredFlags(VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            var h=s.mallocLong(1);var a=s.mallocPointer(1);var info=VmaAllocationInfo.calloc(s);
            VulkanInterop.check(vmaCreateBuffer(VulkanInterop.device().vma(),ci,ai,h,a,info),"allocate buffer");
            createdBuffer=h.get(0);createdAllocation=a.get(0);
            if(info.size()>charged)budget.reserve(info.size()-charged);else budget.release(charged-info.size());
            charged=info.size();
            handle=createdBuffer;allocation=createdAllocation;address=info.pMappedData();
            if(mapped&&address==0)throw new IllegalStateException("Mapped buffer has no pointer");
        }catch(Throwable e){if(createdBuffer!=0)vmaDestroyBuffer(VulkanInterop.device().vma(),createdBuffer,createdAllocation);budget.release(charged);throw e;}
        allocationBytes=charged;
    }
    public ByteBuffer mapped(){return MemoryUtil.memByteBuffer(address,Math.toIntExact(size));}
    public void update(VkCommandBuffer cb,long offset,ByteBuffer bytes){
        if((offset&3)!=0||(bytes.remaining()&3)!=0||offset<0||offset+bytes.remaining()>size)throw new IllegalArgumentException("unaligned/out of bounds buffer update");
        int base=bytes.position();int remaining=bytes.remaining();
        for(int i=0;i<remaining;i+=65536){int n=Math.min(65536,remaining-i);vkCmdUpdateBuffer(cb,handle,offset+i,bytes.slice(base+i,n));}
    }
    @Override public void close(){if(closed)return;closed=true;VulkanInterop.encoder().queueForDestroy(()->{vmaDestroyBuffer(VulkanInterop.device().vma(),handle,allocation);budget.release(allocationBytes);});}
}
