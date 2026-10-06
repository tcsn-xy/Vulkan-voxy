package me.cortex.voxy.common.world;
import java.util.ArrayList;
/** Keep at most four MiB of hot parents per import worker, avoiding repeated cold compression. */
public final class ImportedColumnPins implements AutoCloseable {
    private final ArrayList<WorldSection> sections=new ArrayList<>(16);
    public ImportedColumnPins(WorldEngine world,int chunkX,int chunkZ,int minY,int height){
        try{
            for(int level=WorldEngine.MAX_LOD_LAYER;level>=1;level--){
                int min=Math.floorDiv(minY,32<<level),max=Math.floorDiv(minY+height-1,32<<level);
                for(int y=min;y<=max&&sections.size()<16;y++)sections.add(world.acquire(level,chunkX>>(level+1),y,chunkZ>>(level+1)));
            }
        }catch(Throwable e){close();throw e;}
    }
    @Override public void close(){for(var section:sections)section.release(WorldSection.RELEASE_HINT_POSSIBLE_REUSE);sections.clear();}
}
