package me.cortex.voxy.client.vulkan;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.LinkedHashMap;
/** Retries pressure-rejected chunks on the client tick rather than blocking render or packet handling. */
public final class VulkanIngest {
    private record Pending(LevelChunk chunk,boolean unloaded){}
    private record ChunkKey(net.minecraft.world.level.Level level,long position){}
    private static final LinkedHashMap<ChunkKey,Pending> pending=new LinkedHashMap<>();
    public static void initialize(){
        ClientChunkEvents.CHUNK_LOAD.register((world,chunk)->request(chunk));
        ClientChunkEvents.CHUNK_UNLOAD.register((world,chunk)->request(chunk,true));
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(!VulkanInterop.active()||!VoxyConfig.CONFIG.enabled||!VoxyConfig.CONFIG.ingestEnabled){pending.clear();return;}
            int budget=16;
            var retry=new java.util.ArrayList<java.util.Map.Entry<ChunkKey,Pending>>(16);
            var it=pending.entrySet().iterator();
            while(it.hasNext()&&budget-->0){var e=it.next();it.remove();if(!VoxelIngestService.tryAutoIngestChunk(e.getValue().chunk(),e.getValue().unloaded()))retry.add(java.util.Map.entry(e.getKey(),e.getValue()));}
            for(var e:retry)pending.putIfAbsent(e.getKey(),e.getValue());
        });
    }
    public static void request(LevelChunk chunk){request(chunk,false);}
    public static void request(LevelChunk chunk,boolean unloaded){
        if(!VulkanInterop.active()||!VoxyConfig.CONFIG.ingestEnabled)return;
        var renderer=VulkanVoxyRenderer.current;if(renderer!=null)renderer.meshService.worldChanges.incrementAndGet();
        long key=Integer.toUnsignedLong(chunk.getPos().x())|((long)chunk.getPos().z()<<32);
        var chunkKey=new ChunkKey(chunk.getLevel(),key);var old=pending.get(chunkKey);pending.put(chunkKey,new Pending(chunk,unloaded||(old!=null&&old.chunk()==chunk&&old.unloaded())));
    }
    public static void clear(){pending.clear();}
    public static int pending(){return pending.size();}
}
