package me.cortex.voxy.client.vulkan;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import java.util.*;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.vulkan.VK10.*;
/** 32 MiB pages replace upstream's preallocated multi-gigabyte geometry heap. */
public final class VulkanGeometry implements AutoCloseable {
    public static final int PAGE_BYTES=32*1024*1024;
    public static final class Page {
        public final VulkanBuffer buffer,originIds;
        VulkanBuffer indices;
        int indexCapacity;
        public final int id;
        public final RangeAllocator ranges=new RangeAllocator(PAGE_BYTES);
        public int allocations;
        Page(GpuBudget budget,int id){this.id=id;buffer=new VulkanBuffer(budget,PAGE_BYTES,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,true);originIds=new VulkanBuffer(budget,PAGE_BYTES/2,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT|VK_BUFFER_USAGE_TRANSFER_DST_BIT,false);}
        boolean ensureIndices(GpuBudget budget,int required){
            if(required<=indexCapacity)return true;
            int capacity=Math.min(PAGE_BYTES/8,Math.max(required,Math.max(4096,indexCapacity+indexCapacity/2)));
            long bytes=QuadStream.bytes(capacity);
            if(budget.remaining()<bytes)return false;
            var next=new VulkanBuffer(budget,bytes,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT|VK_BUFFER_USAGE_INDEX_BUFFER_BIT,false);
            if(indices!=null)indices.close();indices=next;indexCapacity=capacity;return true;
        }
        void close(){buffer.close();originIds.close();if(indices!=null){indices.close();indices=null;indexCapacity=0;}}
    }
    public record Mesh(Page page,int offset,int bytes,int[] ranges,int aabb,byte children,int maxModel,MeshClusters.Cluster[] clusters,int originId,long metadataBytes) {}
    private final GpuBudget budget;
    private final me.cortex.voxy.common.util.ByteBudget metadataBudget=new me.cortex.voxy.common.util.ByteBudget(me.cortex.voxy.client.config.VoxyConfig.CONFIG.heapBudgetMiB*1024L*1024/6);
    private int nextPageId,nextOriginId;
    private final it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue freeOrigins=new it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue();
    public final VulkanBuffer origins;
    private org.lwjgl.vulkan.VkCommandBuffer uploadCommands;
    public final List<Page> pages=new ArrayList<>();
    public VulkanGeometry(GpuBudget budget){this.budget=budget;origins=new VulkanBuffer(budget,65536L*16,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,true);}
    public long availableBytes(){long bytes=budget.remaining();for(var p:pages)bytes+=p.ranges.freeBytes()*3/2;return bytes;}
    public Mesh upload(BuiltSection section,MeshClusters.Cluster[] clusters){
        int bytes=Math.toIntExact(section.geometryBuffer.size),offset=-1;Page page=null;
        long metadataBytes=256L+144L*clusters.length;
        if(bytes>PAGE_BYTES)throw new IllegalStateException("Section exceeds geometry page size");
        for(var p:pages)if((offset=p.ranges.allocate(bytes))>=0){page=p;break;}
        if(page==null){if(budget.remaining()<PAGE_BYTES*3L/2)return null;page=new Page(budget,nextPageId++);pages.add(page);offset=page.ranges.allocate(bytes);}
        MemoryUtil.memCopy(section.geometryBuffer.address,page.buffer.address+offset,bytes);
        int maxModel=0;
        for(long p=section.geometryBuffer.address;p<section.geometryBuffer.address+bytes;p+=8)maxModel=Math.max(maxModel,(int)((MemoryUtil.memGetLong(p)>>>26)&65535));
        if(!metadataBudget.tryReserve(metadataBytes)){page.ranges.release(offset,bytes);return null;}
        int originId;
        if(!freeOrigins.isEmpty())originId=freeOrigins.dequeueInt();else if(nextOriginId<65536)originId=nextOriginId++;else{page.ranges.release(offset,bytes);metadataBudget.release(metadataBytes);return null;}
        int lod=me.cortex.voxy.common.world.WorldEngine.getLevel(section.position),scale=32<<lod;
        var origin=origins.mapped();int row=originId*16;
        origin.putInt(row,me.cortex.voxy.common.world.WorldEngine.getX(section.position)*scale).putInt(row+4,me.cortex.voxy.common.world.WorldEngine.getY(section.position)*scale).putInt(row+8,me.cortex.voxy.common.world.WorldEngine.getZ(section.position)*scale).putInt(row+12,lod);
        if(uploadCommands==null)uploadCommands=VulkanInterop.encoder().allocateAndBeginTransientCommandBuffer();
        vkCmdFillBuffer(uploadCommands,page.originIds.handle,offset/2,bytes/2,originId);
        page.allocations++;
        return new Mesh(page,offset,bytes,section.offsets.clone(),section.aabb,section.childExistence,maxModel,clusters,originId,metadataBytes);
    }
    public void retire(Mesh mesh){
        var page=mesh.page();VulkanInterop.encoder().queueForDestroy(()->{page.ranges.release(mesh.offset(),mesh.bytes());page.allocations--;freeOrigins.enqueue(mesh.originId());metadataBudget.release(mesh.metadataBytes());});
    }
    public boolean releaseIndexCaches(){boolean released=false;for(var p:pages)if(p.indices!=null){p.indices.close();p.indices=null;p.indexCapacity=0;released=true;}return released;}
    public void finishUploads(){if(uploadCommands!=null){VulkanInterop.endAndExecute(uploadCommands);uploadCommands=null;}}
    public void trim(){for(var it=pages.iterator();it.hasNext();){var p=it.next();if(p.allocations==0){p.close();it.remove();}}}
    @Override public void close(){finishUploads();for(var p:pages)p.close();pages.clear();origins.close();}
}
