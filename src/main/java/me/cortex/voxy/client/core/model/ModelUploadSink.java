package me.cortex.voxy.client.core.model;
import me.cortex.voxy.common.util.MemoryBuffer;
/** GPU-independent bakery output, borrowed only for the duration of each call. */
public interface ModelUploadSink {
    default boolean canUploadModel(int id){return true;}
    void uploadModel(int modelId,MemoryBuffer model,MemoryBuffer texture,boolean mips,int biomeIndex,MemoryBuffer biome);
    void uploadBiomes(MemoryBuffer colours,MemoryBuffer modelIndexPairs);
}
