package me.cortex.voxy.client.vulkan;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.commonImpl.*;
import me.cortex.voxy.commonImpl.importers.WorldImporter;
import me.cortex.voxy.common.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.io.*;
import java.lang.management.ManagementFactory;
/** Opt-in only, never installed into the user's formal instance by our scripts. */
public final class VulkanQa {
    private static long started,lastScreenshot,lastRaise;
    private static boolean importing,ended,texturesVerified;
    private static long lastWaitingLog;
    private static WorldImporter checkpointImport;
    private static int checkpointStep;
    private static int featureStep,lastRoute=-1;
    private static VulkanConfigScreen settings;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static VulkanBuffer pressureAllocation;
    private static int pressureStep;
    private static long lastNativeSample,directBytes,mappedBytes,databaseCacheBytes,databaseMemtableBytes;
    private static final java.util.List<java.lang.management.BufferPoolMXBean> bufferPools=ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean.class);
    private static VulkanFrameTiming gameTiming;
    private static long gameCpuStart;
    private static double gameCpuMillis;
    private static int gameTimingSlot;
    private static void command(Minecraft mc,String command){var server=mc.getSingleplayerServer();server.execute(()->server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),command));}
    private static void step(Minecraft mc,int index,String name,Runnable action){if(featureStep!=index)return;Logger.info("VULKAN_QA_FEATURE "+name);action.run();featureStep++;}
    private static void denseViews(Minecraft mc,double seconds){
        int phase=(int)(seconds/45);if(phase==lastRoute)return;lastRoute=phase;
        var camera=System.getProperty("voxy.qa.camera","871 166 -186 -90 25").split(" ");
        double yaw=Double.parseDouble(camera[3])+(phase%4)*90;
        command(mc,"tp VulkanVoxyQA "+camera[0]+" "+camera[1]+" "+camera[2]+" "+yaw+" "+camera[4]);
        Logger.info("VULKAN_QA_DENSE_VIEW phase="+phase+" yaw="+yaw);
    }
    private static void features(Minecraft mc,double seconds){
        if(seconds>=25)step(mc,0,"flight",()->command(mc,"tp VulkanVoxyQA 512 180 512 -135 60"));
        if(seconds>=50)step(mc,1,"turn",()->command(mc,"tp VulkanVoxyQA 512 180 512 45 35"));
        if(seconds>=65)step(mc,2,"teleport",()->command(mc,"tp VulkanVoxyQA 1024 180 1024 -90 45"));
        if(seconds>=100)step(mc,3,"window resize",()->{mc.getWindow().setFullscreen(false);mc.getWindow().setWindowed(1720,720);});
        if(seconds>=115)step(mc,4,"restore 3440x1440",()->mc.getWindow().setWindowed(3440,1440));
        if(seconds>=130)step(mc,5,"resource reload",()->{reload=mc.reloadResourcePacks();});
        if(seconds>=160&&reload!=null&&reload.isDone())step(mc,6,"nether",()->command(mc,"execute in minecraft:the_nether run tp VulkanVoxyQA 0 100 0 -135 40"));
        if(seconds>=195)step(mc,7,"overworld",()->command(mc,"execute in minecraft:overworld run tp VulkanVoxyQA 0 180 0 -135 60"));
        if(seconds>=220)step(mc,8,"end",()->command(mc,"execute in minecraft:the_end run tp VulkanVoxyQA 0 120 0 -135 45"));
        if(seconds>=255)step(mc,9,"return overworld",()->command(mc,"execute in minecraft:overworld run tp VulkanVoxyQA 0 180 0 -135 60"));
        if(seconds>=280)step(mc,10,"settings open",()->{settings=new VulkanConfigScreen(null);mc.setScreenAndShow(settings);});
        if(seconds>=285)step(mc,11,"settings save",()->settings.onClose());
        if(seconds>=300)step(mc,12,"64 chunks",()->me.cortex.voxy.client.config.VoxyConfig.CONFIG.sectionRenderDistance=2);
        if(seconds>=320)step(mc,13,"256 chunks",()->me.cortex.voxy.client.config.VoxyConfig.CONFIG.sectionRenderDistance=8);
        if(seconds>=340)step(mc,14,"128 chunks",()->me.cortex.voxy.client.config.VoxyConfig.CONFIG.sectionRenderDistance=4);
        if(seconds>=360)step(mc,15,"renderer reload",()->mc.levelExtractor.allChanged());
        if(seconds>=390)step(mc,16,"budget assertions",()->{var r=VulkanVoxyRenderer.current;if(r==null)throw new IllegalStateException("Missing renderer");if(r.budget.used()>r.budget.limit()||DataMemoryBudget.retainedPayloadBytes()>DataMemoryBudget.HEAP_LIMIT)throw new IllegalStateException("Memory budget exceeded");});
    }
    private static void checkpointTest(Minecraft mc,double seconds){
        var instance=VoxyCommon.getInstance();if(instance==null)return;var engine=WorldIdentifier.ofEngine(mc.level);
        var fixture=mc.gameDirectory.toPath().resolve("import-fixture").toFile();
        if(checkpointStep==0&&seconds>=10){
            instance.getImportManager().makeAndRunIfNone(engine,()->{checkpointImport=new WorldImporter(engine,mc.level,instance.getServiceManager(),instance.savingServiceRateLimiter);ImportCheckpoints.prepare(checkpointImport,fixture);checkpointImport.pause();return checkpointImport;});
            ImportCheckpoints.save(checkpointImport);Logger.info("VULKAN_QA_IMPORT_PAUSED cursor="+checkpointImport.getCursor());checkpointStep=1;
        }
        if(checkpointStep==1&&seconds>=12){checkpointImport.resume();checkpointStep=2;}
        if(checkpointStep==2){
            var cursor=checkpointImport.getCursor();
            if(cursor.regionIndex()>0||cursor.chunkIndex()>0){
                checkpointImport.pause();ImportCheckpoints.save(checkpointImport);instance.getImportManager().cancelImport(engine);
                instance.getImportManager().makeAndRunIfNone(engine,()->{checkpointImport=new WorldImporter(engine,mc.level,instance.getServiceManager(),instance.savingServiceRateLimiter);ImportCheckpoints.prepare(checkpointImport,fixture);return checkpointImport;});
                var restored=checkpointImport.getCursor();if(restored.regionIndex()==0&&restored.chunkIndex()==0)throw new IllegalStateException("Checkpoint cursor did not resume");Logger.info("VULKAN_QA_IMPORT_RESUMED cursor="+restored);checkpointStep=3;
            }
        }
        if(checkpointStep==3&&!checkpointImport.isRunning()){if(checkpointImport.getFailure()!=null)throw new IllegalStateException("Resumed import failed",checkpointImport.getFailure());Logger.info("VULKAN_QA_IMPORT_COMPLETE cursor="+checkpointImport.getCursor());checkpointStep=4;}
    }
    private static void route(Minecraft mc,double seconds){
        int period=(int)(seconds/10);if(period==lastRoute)return;lastRoute=period;
        int[][] positions={{0,0},{256,0},{512,0},{512,256},{512,512},{256,512},{0,512},{0,256}};
        var pos=positions[period%positions.length];command(mc,"tp VulkanVoxyQA "+pos[0]+" 180 "+pos[1]+" -135 45");
    }
    private static void pressureTest(double seconds){
        var r=VulkanVoxyRenderer.current;if(r==null)return;
        if(seconds>=30&&pressureStep==0){
            long fill=r.budget.remaining()-512*1024;
            if(fill<=0)throw new IllegalStateException("No GPU headroom for pressure fixture");
            pressureAllocation=new VulkanBuffer(r.budget,fill,org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,false);
            r.deferRefinements();pressureStep=1;Logger.info("VULKAN_QA_PRESSURE filled GPU budget, coarse coverage requested");
        }
        if(seconds>=40&&pressureStep==1){
            if(!r.geometryPressure()||!r.meshService.coarseSelection||r.drawnSections==0)throw new IllegalStateException("Coarse GPU pressure coverage failed");
            for(long key:r.meshService.selected)if(me.cortex.voxy.common.world.WorldEngine.getLevel(key)!=me.cortex.voxy.common.world.WorldEngine.MAX_LOD_LAYER)throw new IllegalStateException("Pressure selection still contains fine nodes");
            if(r.budget.used()>r.budget.limit())throw new IllegalStateException("Memory budget exceeded");
            pressureStep=2;Logger.info("VULKAN_QA_PRESSURE coarse coverage retained under GPU pressure");
        }
        if(seconds>=65&&pressureStep==2){pressureAllocation.close();pressureAllocation=null;pressureStep=3;Logger.info("VULKAN_QA_PRESSURE allocation retired");}
        if(seconds>=90&&pressureStep==3){
            if(r.geometryPressure())throw new IllegalStateException("Refinement did not resume after pressure release");
            if(r.meshService.selected.length>256)throw new IllegalStateException("Refinement command limit exceeded");
            pressureStep=4;Logger.info("VULKAN_QA_PRESSURE refinement resumed, capped at 256 without truncation");
        }
    }
    private static BufferedWriter frames;
    private static long previousFrame;
    public static void initialize(){
        int seconds=Integer.getInteger("voxy.qa.seconds",0);if(seconds<=0)return;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(ended)return;
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null){
                if(System.nanoTime()-lastWaitingLog>10_000_000_000L&&mc.gui.screen()!=null){
                    lastWaitingLog=System.nanoTime();Logger.info("VULKAN_QA_WAIT_SCREEN "+mc.gui.screen().getClass().getName()+" title="+mc.gui.screen().getTitle().getString());
                    for(var child:mc.gui.screen().children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget)Logger.info("VULKAN_QA_WAIT_BUTTON "+widget.getMessage().getString());
                    if(Boolean.getBoolean("voxy.qa.background")&&Files.exists(mc.gameDirectory.toPath().resolve(".vulkan-voxy-disposable-qa"))&&mc.gui.screen().getClass().getName().equals("net.fabricmc.fabric.impl.client.registry.sync.validate.DetailedBackupConfirmScreen")){
                        for(var child:mc.gui.screen().children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals("我知道我在做什么！")){Logger.info("VULKAN_QA_COPY_ONLY missing-content confirmation accepted");button.onPress(null);break;}
                    }
                }
                return;
            }
            long now=System.nanoTime();
            if(Boolean.getBoolean("voxy.qa.background")){
                long flags=org.lwjgl.sdl.SDLVideo.SDL_GetWindowFlags(mc.getWindow().handle());
                if((flags&org.lwjgl.sdl.SDLVideo.SDL_WINDOW_HIDDEN)==0){org.lwjgl.sdl.SDLVideo.SDL_HideWindow(mc.getWindow().handle());throw new IllegalStateException("QA window unexpectedly became visible");}
                if(started==0)Logger.info("VULKAN_QA_BACKGROUND native_hidden=true native_focus="+((flags&org.lwjgl.sdl.SDLVideo.SDL_WINDOW_INPUT_FOCUS)!=0));
            }
            if(started==0){
                started=now;Logger.info("VULKAN_QA_START mode="+System.getProperty("voxy.qa.mode")+" width="+mc.gameRenderer.mainRenderTarget().width+" height="+mc.gameRenderer.mainRenderTarget().height+" heap="+Runtime.getRuntime().maxMemory());
                var server=mc.getSingleplayerServer();server.execute(()->{
                    var source=server.createCommandSourceStack().withSuppressedOutput();
                    server.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME,false,server);
                    server.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER,false,server);
                    for(String cmd:new String[]{"gamemode creative VulkanVoxyQA","time set noon","weather clear","tp VulkanVoxyQA "+System.getProperty("voxy.qa.camera","320 105 -40 -60 25")})server.getCommands().performPrefixedCommand(source,cmd);
                });
                try{frames=Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("frames-"+System.getProperty("voxy.qa.mode","render")+".csv"));frames.write("seconds,frame_ms,lod_cpu_ms,lod_gpu_ms,sections,models,gpu_bytes,heap_used,heap_committed,gc_count,gc_ms,data_payload_bytes,geometry_pending_bytes,focused,dimension,ingest_pending,direct_buffer_bytes,mapped_buffer_bytes,database_cache_bytes,database_memtable_bytes,coarse_pressure,game_render_cpu_ms,game_render_gpu_ms,lod0,lod1,lod2,lod3,lod4,quads,mesh_builds,missing_builds,selection_ms,draw_packets,hiz_used,masked_occluder_cells\n");}catch(IOException e){throw new UncheckedIOException(e);}
            }
            if(mc.player.getAbilities().mayfly&&!mc.player.getAbilities().flying){mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();}
            double elapsed=(now-started)/1e9;
            if(elapsed>=10&&!texturesVerified){var camera=mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;Logger.info("VULKAN_QA_CAMERA pos="+camera.pos+" pitch="+camera.xRot+" yaw="+camera.yRot+" fog="+camera.fogType+" nearFogStart="+camera.fogData.renderDistanceStart+" focus="+mc.getWindow().isFocused());verifyTextures();texturesVerified=true;}
            String scenario=System.getProperty("voxy.qa.scenario","static");if(scenario.equals("dense"))denseViews(mc,elapsed);if(scenario.equals("features"))features(mc,elapsed);if(scenario.equals("stability"))route(mc,elapsed);if(scenario.equals("imports"))checkpointTest(mc,elapsed);if(scenario.equals("budget"))pressureTest(elapsed);
            if(scenario.equals("settings")&&elapsed>=45&&featureStep==1){
                var renderer=VulkanVoxyRenderer.current;
                if(renderer==null||renderer.budget.used()>renderer.budget.limit())throw new IllegalStateException("Settings restart did not recover a valid Vulkan renderer");
                Logger.info("VULKAN_QA_SETTINGS_RECOVERED budget="+renderer.budget.used()+" limit="+renderer.budget.limit());featureStep=2;
            }
            if(scenario.equals("settings")&&elapsed>=20&&featureStep==0){
                var cfg=me.cortex.voxy.client.config.VoxyConfig.CONFIG;
                cfg.enableRendering=false;me.cortex.voxy.client.config.VoxyConfig.applyVulkanSettings();
                cfg.enableRendering=true;me.cortex.voxy.client.config.VoxyConfig.applyVulkanSettings();
                me.cortex.voxy.client.core.util.IrisUtil.reload();
                if(!net.minecraft.network.chat.Component.translatable("voxy.config.general.enabled").getString().equals("启用 Voxy"))throw new IllegalStateException("Chinese settings translation missing");
                Logger.info("VULKAN_QA_SETTINGS_SAVED irisInstalled="+me.cortex.voxy.client.core.util.IrisUtil.IRIS_INSTALLED+" chinese=true");featureStep=1;
            }

            if(!Boolean.getBoolean("voxy.qa.noImport")&&!scenario.equals("imports")&&!importing&&elapsed>3&&VoxyCommon.getInstance()!=null){
                var instance=VoxyCommon.getInstance();var engine=WorldIdentifier.ofEngine(mc.level);
                var region=DimensionType.getStorageFolder(mc.level.dimension(),mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT)).resolve("region");
                instance.getImportManager().makeAndRunIfNone(engine,()->{var importer=new WorldImporter(engine,mc.level,instance.getServiceManager(),instance.savingServiceRateLimiter);importer.importRegionDirectoryAsync(region.toFile());return importer;});importing=true;
            }
            if(elapsed>20&&now-lastScreenshot>30_000_000_000L){Screenshot.grab(mc.gameDirectory,"qa-"+(int)elapsed+".png",mc.gameRenderer.mainRenderTarget(),1,c->Logger.info(c.getString()));lastScreenshot=now;}
            if(elapsed>=seconds){if(scenario.equals("settings")&&featureStep!=2)throw new IllegalStateException("Settings recovery QA incomplete");if(scenario.equals("imports")&&checkpointStep!=4)throw new IllegalStateException("Import checkpoint QA incomplete");if(scenario.equals("features")&&featureStep!=17)throw new IllegalStateException("Feature QA incomplete");if(scenario.equals("budget")&&pressureStep!=4)throw new IllegalStateException("Budget QA incomplete");if(System.getProperty("voxy.qa.backend","vulkan").equals("opengl")&&VulkanVoxyRenderer.current!=null)throw new IllegalStateException("Vulkan Voxy did not disable itself on OpenGL");ended=true;
                try{if(frames!=null){frames.close();frames=null;}}catch(IOException e){throw new UncheckedIOException(e);}
                if(gameTiming!=null){gameTiming.close();gameTiming=null;}
                Logger.info("VULKAN_QA_COMPLETE seconds="+elapsed);mc.stop();
            }
        });
    }
    private static void verifyTextures(){
        var mc=Minecraft.getInstance();
        var parts=new java.util.ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();
        mc.getModelManager().getBlockStateModelSet().get(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()).collectParts(new net.minecraft.world.level.levelgen.SingleThreadedRandomSource(42),parts);
        var sprite=parts.getFirst().getQuads(net.minecraft.core.Direction.UP).getFirst().materialInfo().sprite();
        var atlas=CpuBlockAtlas.snapshot();var expected=new java.util.HashSet<Integer>();
        int sx=Math.round(sprite.getU0()*atlas.width()),sy=Math.round(sprite.getV0()*atlas.height());
        for(int y=0;y<sprite.contents().height();y++)for(int x=0;x<sprite.contents().width();x++)expected.add(atlas.pixels()[(sy+y)*atlas.width()+sx+x]);
        var bake=new me.cortex.voxy.client.core.model.bakery.SoftwareModelTextureBakery();
        var output=new MemoryBuffer(16*16*8*6);
        try{
            bake.setupTexture();bake.renderToOutput(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),output.address);
            var colours=new java.util.HashSet<Integer>();
            for(int i=0;i<256;i++)colours.add(org.lwjgl.system.MemoryUtil.memGetInt(output.address+16*16*8+i*8));
            if(!colours.equals(expected))throw new IllegalStateException("Real stone texture palette differs: baked="+colours.size()+" source="+expected.size());
            Logger.info("VULKAN_QA_REAL_TEXTURE stone top distinct colours="+colours.size()+" source="+expected.size());
        }finally{output.free();bake.free();}
    }
    public static void beginGameFrame(){
        if(frames==null)return;gameCpuStart=System.nanoTime();
        if(VulkanInterop.active()){
            if(gameTiming==null)gameTiming=new VulkanFrameTiming();
            gameTimingSlot=(int)(VulkanInterop.submitIndex()&1);
            var cb=VulkanInterop.encoder().allocateAndBeginTransientCommandBuffer();gameTiming.begin(cb,gameTimingSlot);VulkanInterop.endAndExecute(cb);
        }
    }
    public static void endGameFrame(){
        if(frames==null||gameCpuStart==0)return;
        if(gameTiming!=null){var cb=VulkanInterop.encoder().allocateAndBeginTransientCommandBuffer();gameTiming.end(cb,gameTimingSlot);VulkanInterop.endAndExecute(cb);}
        gameCpuMillis=(System.nanoTime()-gameCpuStart)/1e6;frame();
    }
    public static void frame(){
        if(frames==null)return;long now=System.nanoTime();var r=VulkanVoxyRenderer.current;
        long gcCount=0,gcTime=0;for(var b:ManagementFactory.getGarbageCollectorMXBeans()){gcCount+=Math.max(0,b.getCollectionCount());gcTime+=Math.max(0,b.getCollectionTime());}
        var heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        if(now-lastNativeSample>=1_000_000_000){
            directBytes=0;mappedBytes=0;for(var pool:bufferPools){if(pool.getName().equals("direct"))directBytes+=pool.getMemoryUsed();if(pool.getName().startsWith("mapped"))mappedBytes+=pool.getMemoryUsed();}
            databaseCacheBytes=me.cortex.voxy.common.config.storage.rocksdb.RocksDBStorageBackend.getSharedBlockCacheUsage();databaseMemtableBytes=me.cortex.voxy.common.config.storage.rocksdb.RocksDBStorageBackend.getSharedMemtableUsage();lastNativeSample=now;
        }
        try{frames.write((now-started)/1e9+","+(previousFrame==0?0:(now-previousFrame)/1e6)+","+(r==null?0:r.cpuMillis)+","+(r==null?0:r.gpuMillis())+","+(r==null?0:r.drawnSections)+","+(r==null?0:r.models.uploadedModels)+","+(r==null?0:r.budget.used())+","+heap.getUsed()+","+heap.getCommitted()+","+gcCount+","+gcTime+","+DataMemoryBudget.retainedPayloadBytes()+","+(r==null?0:r.meshService.pendingBytes())+","+Minecraft.getInstance().getWindow().isFocused()+","+Minecraft.getInstance().level.dimension().identifier()+","+VulkanIngest.pending()+","+directBytes+","+mappedBytes+","+databaseCacheBytes+","+databaseMemtableBytes+","+(r!=null&&r.geometryPressure())+","+gameCpuMillis+","+(gameTiming==null?Double.NaN:gameTiming.gpuMillis)+","+(r==null?"0,0,0,0,0":r.lodSections[0]+","+r.lodSections[1]+","+r.lodSections[2]+","+r.lodSections[3]+","+r.lodSections[4])+","+(r==null?0:r.drawnQuads)+","+(r==null?0:r.meshService.meshBuilds.get())+","+(r==null?0:r.meshService.missingBuilds.get())+","+(r==null?0:r.meshService.selectionMillis)+","+(r==null?0:r.drawPackets)+","+(r!=null&&r.hizUsed)+","+(r==null?0:r.maskedOccluderCells)+"\n");}catch(IOException e){throw new UncheckedIOException(e);}previousFrame=now;
    }
}
