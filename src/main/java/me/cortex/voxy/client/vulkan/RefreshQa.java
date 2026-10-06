package me.cortex.voxy.client.vulkan;
import me.cortex.voxy.common.world.*;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.common.voxelization.*;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import me.cortex.voxy.commonImpl.importers.WorldImporter;
import me.cortex.voxy.common.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
/** Disposable-instance regression against the real updater/importer/storage, never formal data. */
final class RefreshQa {
    private static int step;
    private static WorldImporter importer;
    private static int grass;
    private static VoxelizedSection cube(WorldEngine world,int x,boolean empty){
        var section=VoxelizedSection.createEmpty().setPosition(x,16,-25);
        if(!empty){java.util.Arrays.fill(section.section,0,4096,((long)grass<<27)|(255L<<56));section.lvl0NonAirCount=4096;}
        WorldVoxilizedSectionMipper.mipSection(section,world.getMapper());return section;
    }
    private static int block(WorldEngine world,int level,int x,int y,int z){
        int scale=1<<level;var section=world.acquireIfExists(level,Math.floorDiv(x,32*scale),Math.floorDiv(y,32*scale),Math.floorDiv(z,32*scale));
        if(section==null)return 0;
        try{synchronized(section){return Mapper.getBlockId(section._unsafeGetRawDataArray()[((Math.floorDiv(x,scale)&31))|((Math.floorDiv(z,scale)&31)<<5)|((Math.floorDiv(y,scale)&31)<<10)]);}}
        finally{section.release();}
    }
    static void tick(Minecraft mc,double seconds){
        var instance=VoxyCommon.getInstance();if(instance==null)return;var world=WorldIdentifier.ofEngine(mc.level);
        if(step==0&&seconds>=10){
            me.cortex.voxy.client.config.VoxyConfig.CONFIG.ingestEnabled=false;
            grass=world.getMapper().getIdForBlockState(Blocks.GRASS_BLOCK.defaultBlockState());
            try{
                // Restore a prior-session observation without marking it as a new live update.
                var restore=WorldUpdater.class.getDeclaredMethod("insertUpdate0",WorldEngine.class,VoxelizedSection.class,WorldSection.class,boolean.class,boolean.class);restore.setAccessible(true);
                restore.invoke(null,world,cube(world,60,false),null,true,false);
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            WorldUpdater.insertUpdate(world,cube(world,61,false));
            if(block(world,0,960,256,-400)!=grass)throw new IllegalStateException("Old cache fixture missing");
            var region=DimensionType.getStorageFolder(mc.level.dimension(),mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT)).resolve("region");
            instance.getImportManager().makeAndRunIfNone(world,()->{
                importer=WorldImporter.currentWorldRefresh(world,mc.level,instance.getServiceManager(),instance.savingServiceRateLimiter,region,ImportCheckpoints.refreshBackupPath(world));
                ImportCheckpoints.prepare(importer,region.toFile());importer.pause();return importer;
            });
            step=1;Logger.info("VULKAN_QA_REFRESH_PAUSED old_protected=true session_live=true");
        }
        if(step==1&&seconds>=14){importer.resume();step=2;}
        if(step==2&&importer.getCursor().regionIndex()>0){
            importer.pause();ImportCheckpoints.save(importer);var region=DimensionType.getStorageFolder(mc.level.dimension(),mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT)).resolve("region");
            instance.getImportManager().cancelImport(world);
            instance.getImportManager().makeAndRunIfNone(world,()->{importer=WorldImporter.currentWorldRefresh(world,mc.level,instance.getServiceManager(),instance.savingServiceRateLimiter,region,ImportCheckpoints.refreshBackupPath(world));ImportCheckpoints.prepare(importer,region.toFile());return importer;});
            if(importer.getCursor().regionIndex()==0)throw new IllegalStateException("Refresh checkpoint did not resume");step=3;
            Logger.info("VULKAN_QA_REFRESH_RESUMED policy=current_world");
        }
        if(step==3&&!importer.isRunning()){
            if(importer.getFailure()!=null)throw new IllegalStateException("Refresh failed",importer.getFailure());
            if(!java.nio.file.Files.isRegularFile(importer.getRefreshBackup().resolve("CURRENT")))throw new IllegalStateException("Consistent refresh backup missing");
            for(int level=0;level<=4;level++)if(block(world,level,960,256,-400)!=0)throw new IllegalStateException("Refresh did not repair old cache/parent at level "+level);
            if(block(world,0,976,256,-400)!=grass)throw new IllegalStateException("Refresh overwrote current-session live update");
            WorldUpdater.insertUpdate(world,cube(world,61,true));step=4;
            Logger.info("VULKAN_QA_REFRESH_VERIFIED old_and_parents_repaired=true live_preserved=true backup=true");
        }
    }
    static boolean complete(){return step==4;}
}
