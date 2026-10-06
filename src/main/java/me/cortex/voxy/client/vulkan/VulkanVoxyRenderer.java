package me.cortex.voxy.client.vulkan;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.*;
import com.mojang.renderpearl.backend.vulkan.*;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.model.*;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import java.nio.ByteBuffer;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRPushDescriptor.*;
public final class VulkanVoxyRenderer implements AutoCloseable {
    public static volatile VulkanVoxyRenderer current;
    private final WorldEngine world;
    public final GpuBudget budget;
    public final VulkanModels models;
    public final VulkanMeshService meshService;
    private final ModelBakerySubsystem bakery;
    private final VulkanGeometry geometry;
    private VkPipelines pipelines;
    private boolean announcedPackTarget;
    private com.mojang.renderpearl.api.textures.GpuTextureView packDepth;
    private VulkanHiZ hiz;
    private int renderWidth,renderHeight;
    private long hizRetryAfter;
    private final VulkanBuffer[] metadata=new VulkanBuffer[2],commands=new VulkanBuffer[2],coverage=new VulkanBuffer[2];
    private final VulkanGpuSampler sampler;
    private final VulkanFrameTiming timing,opaqueTiming,transparentTiming;
    private VulkanBuffer quadIndices;
    private int quadIndexCapacity;
    private boolean directFallback;
    private final VulkanBuffer cullDiagnostics;
    private long probeStarted,indexPressureUntil;
    private boolean probeSent;
    private int activeSlot;
    private RenderTarget activeTarget;
    private boolean prepared,dynamicOccluders,hizRanThisFrame;
    private final boolean reference="reference".equals(System.getProperty("voxy.qa.mode"));
    private net.minecraft.client.renderer.state.level.LevelRenderState occluderState;
    private final Matrix4f stableProjection=new Matrix4f();
    private double stableX,stableY,stableZ;
    private int stableFrames;
    public int maskedOccluderCells;
    public boolean hizUsed;
    public void beginFrame(net.minecraft.client.renderer.state.level.LevelRenderState state){
        occluderState=state;dynamicOccluders=false;
        for(var particle:state.particlesRenderState.particles)if(!(particle instanceof net.minecraft.client.renderer.state.level.QuadParticleRenderState quad)||!quad.isEmpty())dynamicOccluders=true;
        for(var entity:state.entityRenderStates)if(entity.entityType==null||!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.entityType).getNamespace().equals("minecraft")||(entity.leashStates!=null&&!entity.leashStates.isEmpty()))dynamicOccluders=true;
        for(var entity:state.blockEntityRenderStates)if(entity.blockEntityType==null||!net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.blockEntityType).getNamespace().equals("minecraft"))dynamicOccluders=true;
    }
    private OccluderMask occluderMask(CameraRenderState camera){
        var mask=new OccluderMask(new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix));var mc=Minecraft.getInstance();
        for(var entity:mc.level.entitiesForRendering()){
            if(entity==mc.getCameraEntity()&&mc.options.getCameraType().isFirstPerson())continue;
            if(!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("minecraft")){mask.fill();break;}
            var renderer=mc.getEntityRenderDispatcher().getRenderer(entity);
            var bounds=((me.cortex.voxy.client.mixin.vulkan.EntityCullingBoundsAccessor)renderer).voxy$cullingBounds(entity,occluderState.worldPartialTicks).inflate(2);
            mask.cover(bounds.minX-camera.pos.x(),bounds.minY-camera.pos.y(),bounds.minZ-camera.pos.z(),bounds.maxX-camera.pos.x(),bounds.maxY-camera.pos.y(),bounds.maxZ-camera.pos.z());
        }
        for(var entity:occluderState.blockEntityRenderStates){
            if(entity.blockPos==null){mask.fill();break;}var pos=entity.blockPos;
            mask.cover(pos.getX()-4-camera.pos.x(),pos.getY()-4-camera.pos.y(),pos.getZ()-4-camera.pos.z(),pos.getX()+5-camera.pos.x(),pos.getY()+5-camera.pos.y(),pos.getZ()+5-camera.pos.z());
        }
        maskedOccluderCells=mask.cells();return mask;
    }
    void deferRefinements(){geometryPressure=true;pressureUntil=System.nanoTime()+2_000_000_000L;meshService.setGeometryPressure(true);me.cortex.voxy.common.util.DataMemoryBudget.setGpuPressure(true);}
    boolean geometryPressure(){return geometryPressure;}
    private final ByteBuffer push=MemoryUtil.memAlloc(128);
    static final class DrawEntry {
        boolean lateOpaque;int transparentIndex;double distanceSquared;
        final long key,builtVersion;final VulkanGeometry.Mesh mesh;final MeshClusters.Cluster cluster;
        DrawEntry(VulkanMeshService.Node node,MeshClusters.Cluster cluster){key=node.key;builtVersion=node.builtVersion;mesh=node.mesh;this.cluster=cluster;}
    }
    private final ArrayList<DrawEntry> drawNodes=new ArrayList<>();
    private static final class PageGroup {
        double waterDistance;
        final VulkanGeometry.Page page;final int start,end,rank;final int[] indices=new int[3],firstIndex=new int[3];
        PageGroup(VulkanGeometry.Page page,int start,int end,int rank){this.page=page;this.start=start;this.end=end;this.rank=rank;}
        int required(){return indices[0]+indices[1]+indices[2];}
    }
    private final ArrayList<PageGroup> pageGroups=new ArrayList<>(),waterGroups=new ArrayList<>();
    private boolean compactIndices;
    private final int maxMeshes;
    private long geometryRevision,cachedGeometryRevision=-1,cachedReclamation=-1,cachedCoverage=Long.MIN_VALUE,geometryDrawSignature;
    private long[] cachedSelection;
    private final Matrix4f cachedDrawMatrix=new Matrix4f();
    private double cachedDrawX,cachedDrawY,cachedDrawZ;
    private final long[] metadataSignature={Long.MIN_VALUE,Long.MIN_VALUE};
    private final int[] metadataX=new int[2],metadataY=new int[2],metadataZ=new int[2];
    private long atlasVersion,lastEviction,lastHizWorldVersion,lastHizSignature,drawSignature,pressureUntil;
    private boolean geometryPressure;
    private boolean closed;
    public double cpuMillis;
    public int drawnSections;
    public final int[] lodSections=new int[5];
    public long drawnQuads;
    public int drawPackets;
    public long renderedFrames;
    public VulkanVoxyRenderer(WorldEngine world){
        this.world=world;world.acquireRef();
        var conf=VoxyConfig.CONFIG;int refinementLimit=Math.clamp(conf.maxResidentMeshes,256,16384);
        var level=Minecraft.getInstance().level;
        int rootSize=32<<WorldEngine.MAX_LOD_LAYER;
        int verticalRoots=Math.floorDiv(level.getMaxY(),rootSize)-Math.floorDiv(level.getMinY(),rootSize)+1;
        int rootDiameter=2*(32*512/rootSize)+1;
        // Small per-root metadata reserves cover the maximum configurable distance even at a low fine limit.
        int nodeCapacity=Math.max(refinementLimit,Math.multiplyExact(rootDiameter*rootDiameter,verticalRoots));
        maxMeshes=nodeCapacity*9;
        budget=VulkanInterop.budget(conf.gpuBudgetMiB);
        models=new VulkanModels(budget);
        bakery=new ModelBakerySubsystem(world.getMapper(),new ModelStore(models));
        for(var b:world.getMapper().getBiomeEntries())bakery.addBiome(b);
        world.getMapper().setBiomeCallback(bakery::addBiome);
        meshService=new VulkanMeshService(world,bakery,world.instanceIn.getServiceManager(),refinementLimit,nodeCapacity);
        geometry=new VulkanGeometry(budget);
        // Shared Uint32 indices reuse four vertex shader results per quad, including large sections.
        cullDiagnostics=new VulkanBuffer(budget,32,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,true);
        for(int i=0;i<2;i++){
            metadata[i]=new VulkanBuffer(budget,maxMeshes*64L,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,true);
            commands[i]=new VulkanBuffer(budget,maxMeshes*2L*20,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT|VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT|VK_BUFFER_USAGE_TRANSFER_DST_BIT,false);
            coverage[i]=new VulkanBuffer(budget,8208,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,true);
        }
        sampler=(VulkanGpuSampler)RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE,AddressMode.CLAMP_TO_EDGE,FilterMode.NEAREST,FilterMode.NEAREST,1,OptionalDouble.of(3));
        timing=new VulkanFrameTiming();opaqueTiming=new VulkanFrameTiming();transparentTiming=new VulkanFrameTiming();atlasVersion=CpuBlockAtlas.snapshot().version();current=this;
        Logger.info("Voxy Vulkan renderer ready, heap="+Runtime.getRuntime().maxMemory()+" GPU budget="+budget.limit());
    }
    private boolean ensureQuadIndices(int required){
        if(quadIndices!=null&&quadIndexCapacity>=required)return true;
        int capacity=Math.min(1<<19,Math.max(required,Math.max(1024,quadIndexCapacity+quadIndexCapacity/2)));
        long bytes=capacity*24L;if(budget.remaining()<bytes)return false;
        var next=new VulkanBuffer(budget,bytes,VK_BUFFER_USAGE_INDEX_BUFFER_BIT|VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,true);
        var indices=next.mapped();for(int q=0;q<capacity;q++){int at=q*24,base=q*4;indices.putInt(at,base).putInt(at+4,base+1).putInt(at+8,base+2).putInt(at+12,base+2).putInt(at+16,base+1).putInt(at+20,base+3);}
        if(quadIndices!=null)quadIndices.close();quadIndices=next;quadIndexCapacity=capacity;return true;
    }
    public void render(LevelRenderer levelRenderer,CameraRenderState camera,RenderTarget target){
        prepared=false;hizRanThisFrame=false;
        if(closed||!VoxyConfig.CONFIG.enableRendering)return;
        VulkanInterop.prepareExternalCommands();
        long start=System.nanoTime();
        if(meshService.failure!=null)throw new IllegalStateException("Voxy mesh worker failed",meshService.failure);
        if(pipelines==null){pipelines=new VkPipelines(target.getColorTexture().getFormat(),target.getDepthTexture().getFormat());Logger.info("Voxy camera projection: "+camera.projectionMatrix);}
        if(renderWidth!=target.width||renderHeight!=target.height){
            if(hiz!=null){hiz.close();hiz=null;}
            renderWidth=target.width;renderHeight=target.height;hizRetryAfter=0;
        }
        if(hiz==null&&start>=hizRetryAfter){
            try{hiz=new VulkanHiZ(budget,target.width,target.height);}
            catch(GpuBudget.BudgetExceeded exhausted){
                if(hizRetryAfter==0)Logger.info("Deferring HiZ allocation under GPU budget pressure; distance rendering continues");
                hizRetryAfter=start+1_000_000_000L;
                deferRefinements();
            }
        }
        bakery.tick(500_000);
        if(geometryPressure){
            meshService.reclaimRefinements(geometry);
            if(start>=pressureUntil&&PressureRecovery.canResume(geometry.availableBytes(),budget.remaining(),budget.limit())){geometryPressure=false;meshService.setGeometryPressure(false);me.cortex.voxy.common.util.DataMemoryBudget.setGpuPressure(false);}
        }
        if(!geometryPressure&&budget.remaining()>budget.limit()/8)meshService.easePressure();
        long uploadBudget=Math.max(1,VoxyConfig.CONFIG.uploadBudgetMiB)*1024L*1024;
        VulkanMeshService.Result result;
        while(uploadBudget>0&&(result=meshService.completed.poll())!=null){
            var n=result.node();
            if(meshService.node(n.key)!=n||result.version()!=n.version.get()){meshService.consumed(result);continue;}
            if(geometryPressure&&!meshService.protectedNode(n)){n.retryAfter=start+1_000_000_000;meshService.consumed(result);continue;}
            var built=result.section();
            if(built.isEmpty()){geometryRevision++;
                if(n.mesh!=null)geometry.retire(n.mesh);n.mesh=null;n.children=Byte.toUnsignedInt(built.childExistence);n.ready=true;n.builtVersion=result.version();meshService.consumed(result);continue;
            }
            // Do not draw a model until its texture upload is in the same ordered engine submission.
            int maxModel=0;for(long p=built.geometryBuffer.address;p<built.geometryBuffer.address+built.geometryBuffer.size;p+=8)maxModel=Math.max(maxModel,(int)((MemoryUtil.memGetLong(p)>>>26)&65535));
            if(maxModel>=models.uploadedModels){meshService.completed.add(result);break;}
            var uploaded=geometry.upload(built,result.clusters());
            if(uploaded==null){
                if(geometry.releaseIndexCaches()){indexPressureUntil=start+1_000_000_000L;me.cortex.voxy.common.util.DataMemoryBudget.setGpuPressure(true);}
                else if(start>=indexPressureUntil)deferRefinements();
                meshService.completed.add(result);break;
            }
            geometryRevision++;if(n.mesh!=null)geometry.retire(n.mesh);n.mesh=uploaded;n.children=Byte.toUnsignedInt(built.childExistence);n.ready=true;n.builtVersion=result.version();uploadBudget-=result.bytes();meshService.consumed(result);
        }
        geometry.finishUploads();
        int radius=(int)Math.ceil(VoxyConfig.CONFIG.sectionRenderDistance*512);
        var mc=Minecraft.getInstance();
        var sodiumGrid=SodiumNearCoverage.forLevel(mc.level);
        var drawMatrix=new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        var frustum=new DistantFrustum(drawMatrix);
        meshService.update(new VulkanMeshService.View(camera.pos.x(),camera.pos.y(),camera.pos.z(),radius,VoxyConfig.CONFIG.subDivisionSize,target.height,mc.level.getMinY(),mc.level.getMaxY()+1));
        if(start-lastEviction>1_000_000_000){meshService.evictUnvisited(geometry);geometry.trim();lastEviction=start;}
        var selection=meshService.selected;long reclaimed=meshService.reclamationRevision.get(),nearSignature=sodiumGrid==null?Long.MIN_VALUE:sodiumGrid.signature();
        boolean refresh=geometryRevision!=cachedGeometryRevision||reclaimed!=cachedReclamation||selection!=cachedSelection||nearSignature!=cachedCoverage||camera.pos.x()!=cachedDrawX||camera.pos.y()!=cachedDrawY||camera.pos.z()!=cachedDrawZ||!cachedDrawMatrix.equals(drawMatrix);
        if(refresh){
        drawNodes.clear();
        for(long key:selection){var n=meshService.node(key);if(n!=null&&n.mesh!=null){
            int size=32<<WorldEngine.getLevel(key);
            if(n.drawEntries==null||n.drawEntries[0].mesh!=n.mesh){var clusters=n.mesh.clusters();n.drawEntries=new DrawEntry[clusters.length];for(int i=0;i<clusters.length;i++)n.drawEntries[i]=new DrawEntry(n,clusters[i]);}
            for(var entry:n.drawEntries){var cluster=entry.cluster;
                int box=cluster.aabb();double scale=1<<WorldEngine.getLevel(key);
                double x=WorldEngine.getX(key)*(double)size+((box>>>0)&31)*scale-camera.pos.x(),y=WorldEngine.getY(key)*(double)size+((box>>>5)&31)*scale-camera.pos.y(),z=WorldEngine.getZ(key)*(double)size+((box>>>10)&31)*scale-camera.pos.z();
                double pad=.002*scale;
                if(frustum.intersects(x-pad,y-pad,z-pad,(((box>>>15)&31)+1)*scale+2*pad,(((box>>>20)&31)+1)*scale+2*pad,(((box>>>25)&31)+1)*scale+2*pad)){
                    entry.lateOpaque=cluster.cutout()||sodiumGrid==null||sodiumGrid.intersectsBounds(x+camera.pos.x()-pad,y+camera.pos.y()-pad,z+camera.pos.z()-pad,x+camera.pos.x()+(((box>>>15)&31)+1)*scale+pad,y+camera.pos.y()+(((box>>>20)&31)+1)*scale+pad,z+camera.pos.z()+(((box>>>25)&31)+1)*scale+pad);
                    entry.distanceSquared=x*x+y*y+z*z;drawNodes.add(entry);
                }
            }
        }}
        if(drawNodes.size()>maxMeshes)throw new IllegalStateException("LOD selection exceeded reserved command capacity");
        drawNodes.sort(Comparator.<DrawEntry>comparingInt(n->n.mesh.page().id).thenComparing(n->n.lateOpaque).thenComparing(Comparator.comparingDouble((DrawEntry n)->n.distanceSquared).reversed()));
        drawnSections=drawNodes.size();drawPackets=drawnSections*2;Arrays.fill(lodSections,0);drawnQuads=0;
        for(var n:drawNodes){lodSections[WorldEngine.getLevel(n.key)]++;drawnQuads+=n.cluster.count();}
        drawSignature=drawnSections;for(var n:drawNodes)drawSignature=(drawSignature*0x9e3779b97f4a7c15L)^n.key^n.builtVersion^n.cluster.aabb()^n.cluster.offsets()[0]^n.mesh.offset()^((long)n.mesh.page().id<<32)^(n.lateOpaque?0x5a5a5a5aL:0);
        pageGroups.clear();int groupStart=0;
        while(groupStart<drawnSections){
            var page=drawNodes.get(groupStart).mesh.page();int end=groupStart+1;while(end<drawnSections&&drawNodes.get(end).mesh.page()==page)end++;
            var group=new PageGroup(page,groupStart,end,pageGroups.size());
            for(int i=groupStart;i<end;i++){var entry=drawNodes.get(i);int[] offsets=entry.cluster.offsets();group.indices[entry.lateOpaque?1:0]+=(entry.cluster.end()-offsets[1]);group.indices[2]+=(offsets[1]-offsets[0]);}
            for(int i=groupStart;i<end;i++)if(drawNodes.get(i).cluster.offsets()[1]>drawNodes.get(i).cluster.offsets()[0])group.waterDistance=Math.max(group.waterDistance,drawNodes.get(i).distanceSquared);
            group.firstIndex[1]=group.indices[0];group.firstIndex[2]=group.indices[0]+group.indices[1];
            int water=group.firstIndex[2];for(int i=groupStart;i<end;i++){var entry=drawNodes.get(i);entry.transparentIndex=water;water+=(entry.cluster.offsets()[1]-entry.cluster.offsets()[0]);}
            pageGroups.add(group);groupStart=end;
        }
        waterGroups.clear();waterGroups.addAll(pageGroups);waterGroups.sort(Comparator.comparingDouble((PageGroup g)->g.waterDistance).reversed());
        geometryDrawSignature=drawSignature;cachedGeometryRevision=geometryRevision;cachedReclamation=reclaimed;cachedSelection=selection;cachedCoverage=nearSignature;cachedDrawMatrix.set(drawMatrix);cachedDrawX=camera.pos.x();cachedDrawY=camera.pos.y();cachedDrawZ=camera.pos.z();
        }
        drawSignature=geometryDrawSignature;
        if(drawnSections==0){cpuMillis=(System.nanoTime()-start)/1e6;return;}
        int slot=(int)(VulkanInterop.submitIndex()&1),ox=(int)Math.floor(camera.pos.x()/32)*32,oy=(int)Math.floor(camera.pos.y()/32)*32,oz=(int)Math.floor(camera.pos.z()/32)*32;
        boolean probe=false;
        if(Boolean.getBoolean("voxy.qa.cullProbe")&&!probeSent){if(probeStarted==0)probeStarted=start;probe=start-probeStarted>60_000_000_000L;if(probe){probeSent=true;MemoryUtil.memSet(cullDiagnostics.address,0,32);}}
        var meta=metadata[slot].mapped();
        if(indexPressureUntil!=0&&start>=indexPressureUntil){indexPressureUntil=0;if(!geometryPressure)me.cortex.voxy.common.util.DataMemoryBudget.setGpuPressure(false);}
        compactIndices=start>=indexPressureUntil&&!Boolean.getBoolean("voxy.qa.fixedCommands");
        if(compactIndices)for(var group:pageGroups)if(!group.page.ensureIndices(budget,group.required()))compactIndices=false;
        directFallback=reference;
        if(!compactIndices&&!reference){int required=0;for(var entry:drawNodes)required=Math.max(required,entry.cluster.count());directFallback=!ensureQuadIndices(required);}
        drawPackets=compactIndices?pageGroups.size()*3:drawnSections*2;
        long signature=drawSignature^(probe?Long.MIN_VALUE:0);
        if(metadataSignature[slot]!=signature||metadataX[slot]!=ox||metadataY[slot]!=oy||metadataZ[slot]!=oz){
        for(int i=0;i<drawnSections;i++){
            var n=drawNodes.get(i);var m=n.mesh;int lod=WorldEngine.getLevel(n.key),size=32<<lod,off=i*64;
            meta.putInt(off,WorldEngine.getX(n.key)*size-ox).putInt(off+4,WorldEngine.getY(n.key)*size-oy).putInt(off+8,WorldEngine.getZ(n.key)*size-oz).putInt(off+12,lod);
            int base=m.offset()/8;for(int j=0;j<8;j++)meta.putInt(off+16+j*4,base+n.cluster.offsets()[j]);
            meta.putInt(off+48,base+n.cluster.end()).putInt(off+52,n.cluster.aabb()).putInt(off+56,(n.transparentIndex<<1)|(probe?1:0)).putInt(off+60,n.lateOpaque?1:0);
        }
        metadataSignature[slot]=signature;metadataX[slot]=ox;metadataY[slot]=oy;metadataZ[slot]=oz;
        }
        var wholeNear=coverage[slot].mapped();MemoryUtil.memSet(MemoryUtil.memAddress(wholeNear)+16,0,8192);
        var near=wholeNear.duplicate().position(16).slice().order(wholeNear.order());
        int nx=ox/16-16,ny=NearCoverageGrid.verticalBase(oy/16,Math.floorDiv(mc.level.getMinY(),16),Math.floorDiv(mc.level.getMaxY(),16)),nz=oz/16-16;
        var sodiumCoverage=Boolean.getBoolean("voxy.qa.legacyCoverage")?null:SodiumNearCoverage.forLevel(mc.level);
        if(sodiumCoverage!=null){sodiumCoverage.writeTo(near,nx,ny,nz);drawSignature^=sodiumCoverage.signature();}
        else for(var section:levelRenderer.visibleSections()){
            var mesh=section.getSectionMesh();if(mesh==null||mesh==net.minecraft.client.renderer.chunk.CompiledSectionMesh.UNCOMPILED)continue;
            var pos=section.getRenderOrigin();int x=(pos.getX()>>4)-nx,y=(pos.getY()>>4)-ny,z=(pos.getZ()>>4)-nz;
            if(x<0||x>=32||y<0||y>=64||z<0||z>=32)continue;
            int index=x+z*32+y*1024,word=(index>>>5)*4;near.putInt(word,near.getInt(word)|(1<<(index&31)));
        }
        Matrix4f mvp=new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        mvp.get(0,push);push.putFloat(64,(float)(camera.pos.x()-ox)).putFloat(68,(float)(camera.pos.y()-oy)).putFloat(72,(float)(camera.pos.z()-oz)).putFloat(76,Boolean.getBoolean("voxy.qa.legacyCoverage")?Math.max(0,camera.fogData.renderDistanceStart):Float.MAX_VALUE);
        boolean transparentInView=false;for(var group:pageGroups)transparentInView|=group.indices[2]>0;
        hizUsed=hiz!=null&&VoxyConfig.CONFIG.hizEnabled&&!transparentInView&&!dynamicOccluders&&lastHizWorldVersion==meshService.worldChanges.get()&&lastHizSignature==drawSignature&&hiz.canUse(camera);
        push.putFloat(80,radius).putInt(84,(hizUsed?1:0)|(directFallback?2:0)|(compactIndices?0:4)|(Boolean.getBoolean("voxy.qa.noRasterCull")?0:8)|(drawnSections<<8));
        push.putFloat(96,camera.fogData.color.x).putFloat(100,camera.fogData.color.y).putFloat(104,camera.fogData.color.z).putFloat(108,camera.fogData.color.w);
        wholeNear.putInt(0,nx-ox/16).putInt(4,ny-oy/16).putInt(8,nz-oz/16).putInt(12,(target.width&65535)|(target.height<<16));
        push.putInt(112,ox).putInt(116,oy).putInt(120,oz).putInt(124,VulkanFog.packEnvironment(camera.fogData.environmentalStart,camera.fogData.environmentalEnd));push.position(0).limit(128);
        var cb=VulkanInterop.encoder().allocateAndBeginTransientCommandBuffer();timing.begin(cb,slot);opaqueTiming.reset(cb,slot);transparentTiming.reset(cb,slot);
        VkPipelines.barrier(cb,VK_PIPELINE_STAGE_HOST_BIT|VK_PIPELINE_STAGE_TRANSFER_BIT,VK_ACCESS_HOST_WRITE_BIT|VK_ACCESS_TRANSFER_WRITE_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT|VK_PIPELINE_STAGE_VERTEX_SHADER_BIT|VK_PIPELINE_STAGE_VERTEX_INPUT_BIT,VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_INDEX_READ_BIT);
        vkCmdBindPipeline(cb,VK_PIPELINE_BIND_POINT_COMPUTE,pipelines.cull);
        if(compactIndices){
            VkPipelines.barrier(cb,VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT|VK_PIPELINE_STAGE_VERTEX_INPUT_BIT|VK_PIPELINE_STAGE_VERTEX_SHADER_BIT,VK_ACCESS_INDIRECT_COMMAND_READ_BIT|VK_ACCESS_INDEX_READ_BIT|VK_ACCESS_SHADER_READ_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_ACCESS_TRANSFER_WRITE_BIT|VK_ACCESS_SHADER_WRITE_BIT);
            try(var stack=MemoryStack.stackPush()){
                for(var group:pageGroups){
                    var template=stack.calloc(64);for(int bucket=0;bucket<3;bucket++)template.putInt(bucket*20,reference?6:4).putInt(bucket*20+4,bucket==2?group.indices[2]:0).putInt(bucket*20+8,0).putInt(bucket*20+12,group.firstIndex[bucket]);
                    vkCmdUpdateBuffer(cb,commands[slot].handle,group.rank*64L,template);
                }
            }
            VkPipelines.barrier(cb,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_ACCESS_TRANSFER_WRITE_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_SHADER_WRITE_BIT);
        }
        for(var group:pageGroups){
            push.putInt(88,group.start).putInt(92,group.end-group.start);
            bind(cb,VK_PIPELINE_BIND_POINT_COMPUTE,slot,group.page,target,false,compactIndices?group.rank*64L:0,compactIndices?64:commands[slot].size);
            vkCmdDispatch(cb,group.end-group.start,1,1);
        }
        VkPipelines.barrier(cb,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_ACCESS_SHADER_WRITE_BIT,VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT|VK_PIPELINE_STAGE_VERTEX_INPUT_BIT|VK_PIPELINE_STAGE_VERTEX_SHADER_BIT,VK_ACCESS_INDIRECT_COMMAND_READ_BIT|VK_ACCESS_INDEX_READ_BIT|VK_ACCESS_SHADER_READ_BIT);
        if(probe){VkPipelines.barrier(cb,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_ACCESS_SHADER_WRITE_BIT,VK_PIPELINE_STAGE_HOST_BIT,VK_ACCESS_HOST_READ_BIT);VulkanInterop.encoder().queueForDestroy(()->{var values=new int[8];for(int i=0;i<8;i++)values[i]=cullDiagnostics.mapped().getInt(i*4);Logger.info("VULKAN_QA_CULL_DIAGNOSTICS total,visible,hizEnabled,behind,eligible,depthNonzero,occluded,quads="+Arrays.toString(values));});}
        timing.end(cb,slot);VulkanInterop.endAndExecute(cb);
        activeSlot=slot;activeTarget=target;prepared=true;
        cpuMillis=(System.nanoTime()-start)/1e6;
    }
    public void drawOpaque(RenderPass pass){
        if(!prepared)return;if(VitrailCompat.shaderActive()){packDepth=VitrailCompat.sceneDepth(activeTarget);return;}long start=System.nanoTime();
        var cb=VulkanInterop.commands(pass);
        // Reset is recorded outside the active render pass in prepare; begin/end are legal here.
        opaqueTiming.markerBegin(cb,activeSlot);draw(cb,activeSlot,activeTarget,false,pipelines);opaqueTiming.end(cb,activeSlot);
        VulkanInterop.invalidate(pass);
        cpuMillis+=(System.nanoTime()-start)/1e6;renderedFrames++;
    }
    public void drawTransparent(RenderPass pass){
        if(!prepared)return;if(VitrailCompat.shaderActive())return;long start=System.nanoTime();
        var cb=VulkanInterop.commands(pass);
        transparentTiming.markerBegin(cb,activeSlot);draw(cb,activeSlot,activeTarget,true,pipelines);transparentTiming.end(cb,activeSlot);
        VulkanInterop.invalidate(pass);
        cpuMillis+=(System.nanoTime()-start)/1e6;
    }
    public void drawAfterPack(){
        if(!prepared||closed)return;long start=System.nanoTime();VulkanInterop.prepareExternalCommands();
        var depth=activeTarget.getDepthTexture();
        if(packDepth!=null&&packDepth.texture()!=depth&&packDepth.texture().getFormat()==depth.getFormat())RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(packDepth.texture(),depth,0,0,0,0,0,Math.min(packDepth.getWidth(0),depth.getWidth(0)),Math.min(packDepth.getHeight(0),depth.getHeight(0)));
        try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"Voxy after Vitrail composition",activeTarget.getColorTextureView(),Optional.empty(),activeTarget.getDepthTextureView(),OptionalDouble.empty())){
            var cb=VulkanInterop.commands(pass);opaqueTiming.markerBegin(cb,activeSlot);draw(cb,activeSlot,activeTarget,false,pipelines);opaqueTiming.end(cb,activeSlot);transparentTiming.markerBegin(cb,activeSlot);draw(cb,activeSlot,activeTarget,true,pipelines);transparentTiming.end(cb,activeSlot);VulkanInterop.invalidate(pass);renderedFrames++;
        }
        cpuMillis+=(System.nanoTime()-start)/1e6;
        if(!announcedPackTarget){Logger.info("Voxy renders distant material after Vitrail composition with preserved near depth");announcedPackTarget=true;}
    }
    public void drawTransparent(){
        if(!prepared)return;
        try(RenderPass pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"Voxy distant transparency",activeTarget.getColorTextureView(),Optional.empty(),activeTarget.getDepthTextureView(),OptionalDouble.empty())){drawTransparent(pass);}
    }

    private void draw(VkCommandBuffer cb,int slot,RenderTarget target,boolean transparent,VkPipelines graphics){
        for(var group:transparent?waterGroups:pageGroups){
            if(!compactIndices&&!directFallback)vkCmdBindIndexBuffer(cb,quadIndices.handle,0,VK_INDEX_TYPE_UINT32);
            bind(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,slot,group.page,target,false,compactIndices?group.rank*64L:0,compactIndices?64:commands[slot].size);
            if(compactIndices){
                int first=transparent?2:0,last=transparent?3:2;
                for(int bucket=first;bucket<last;bucket++)if(group.indices[bucket]>0){
                    vkCmdBindPipeline(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,bucket==2?graphics.transparent:(bucket==0?graphics.solid:graphics.opaque));
                    vkCmdDrawIndirect(cb,commands[slot].handle,group.rank*64L+bucket*20L,1,20);
                }
            }else{
                int start=group.start;
                while(start<group.end){int end=start+1;boolean late=drawNodes.get(start).lateOpaque;while(end<group.end&&(transparent||drawNodes.get(end).lateOpaque==late))end++;
                    vkCmdBindPipeline(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,transparent?graphics.transparentFallback:(late?graphics.opaqueFallback:graphics.solidFallback));
                    long offset=(transparent?drawnSections+(long)start:start)*20;int count=end-start,max=Math.max(1,VulkanInterop.device().getDeviceInfo().limits().maxDrawIndirectDrawCount());
                    for(int i=0;i<count;i+=max){if(directFallback)vkCmdDrawIndirect(cb,commands[slot].handle,offset+i*20L,Math.min(max,count-i),20);else vkCmdDrawIndexedIndirect(cb,commands[slot].handle,offset+i*20L,Math.min(max,count-i),20);}start=end;
                }
            }
        }
    }
    private void bind(VkCommandBuffer cb,int point,int slot,VulkanGeometry.Page page,RenderTarget target,boolean history,long commandOffset,long commandRange){
        try(var s=MemoryStack.stackPush()){
            var writes=VkWriteDescriptorSet.calloc(14,s);
            VulkanBuffer[] buffers={page.buffer,models.models,models.colours,metadata[slot],commands[slot]};
            for(int i=0;i<5;i++)writes.get(i).sType$Default().dstBinding(i).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).pBufferInfo(VkDescriptorBufferInfo.calloc(1,s).buffer(buffers[i].handle).offset(0).range(i==4?commandRange:buffers[i].size));
            writes.get(4).pBufferInfo().get(0).offset(commandOffset);
            var lm=Minecraft.getInstance().gameRenderer.levelLightmap();
            long[] views={models.view.vkImageView(),((VulkanGpuTextureView)lm).vkImageView(),hiz==null?models.view.vkImageView():hiz.view};
            for(int i=5;i<8;i++)writes.get(i).sType$Default().dstBinding(i).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1).pImageInfo(VkDescriptorImageInfo.calloc(1,s).sampler(sampler.vkSampler()).imageView(views[i-5]).imageLayout(VK_IMAGE_LAYOUT_GENERAL));
            writes.get(8).sType$Default().dstBinding(8).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).pBufferInfo(VkDescriptorBufferInfo.calloc(1,s).buffer(coverage[slot].handle).offset(0).range(coverage[slot].size));
            writes.get(9).sType$Default().dstBinding(11).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).pBufferInfo(VkDescriptorBufferInfo.calloc(1,s).buffer(cullDiagnostics.handle).offset(0).range(32));
            VulkanBuffer[] extra={page.originIds,geometry.origins,page.indices==null?(quadIndices==null?cullDiagnostics:quadIndices):page.indices};
            for(int i=0;i<3;i++)writes.get(10+i).sType$Default().dstBinding(12+i).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).pBufferInfo(VkDescriptorBufferInfo.calloc(1,s).buffer(extra[i].handle).offset(0).range(extra[i].size));
            var blockAtlas=Minecraft.getInstance().getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
            writes.get(13).sType$Default().dstBinding(15).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1).pImageInfo(VkDescriptorImageInfo.calloc(1,s).sampler(((VulkanGpuSampler)blockAtlas.getSampler()).vkSampler()).imageView(((VulkanGpuTextureView)blockAtlas.getTextureView()).vkImageView()).imageLayout(VK_IMAGE_LAYOUT_GENERAL));
            vkCmdPushDescriptorSetKHR(cb,point,pipelines.layout,0,writes);vkCmdPushConstants(cb,pipelines.layout,VK_SHADER_STAGE_ALL,0,push);
        }
    }
    public void finishFrame(CameraRenderState camera,RenderTarget target){
        if(closed||pipelines==null||hiz==null||drawnSections==0||!VoxyConfig.CONFIG.hizEnabled||dynamicOccluders)return;
        for(var group:pageGroups)if(group.indices[2]>0){hiz.invalidate();return;}
        if(!VulkanInterop.canPrepareExternalCommands()){hiz.invalidate();return;}
        var matrix=new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        boolean stable=Math.abs(camera.pos.x()-stableX)<.00001&&Math.abs(camera.pos.y()-stableY)<.00001&&Math.abs(camera.pos.z()-stableZ)<.00001&&stableProjection.equals(matrix,.000001f);
        if(!stable){stableFrames=0;stableProjection.set(matrix);stableX=camera.pos.x();stableY=camera.pos.y();stableZ=camera.pos.z();return;}
        if(++stableFrames<3)return;
        // An unchanged terrain image gives the same conservative pyramid; do not recompute it each frame.
        long worldVersion=meshService.worldChanges.get();
        if(lastHizWorldVersion==worldVersion&&lastHizSignature==drawSignature&&hiz.canUse(camera))return;
        hiz.reduce(pipelines,target,sampler.vkSampler(),camera,occluderMask(camera));lastHizWorldVersion=worldVersion;lastHizSignature=drawSignature;hizRanThisFrame=true;
    }
    public double gpuMillis(){return timing.gpuMillis+opaqueTiming.gpuMillis+transparentTiming.gpuMillis+(hizRanThisFrame&&hiz!=null&&Double.isFinite(hiz.gpuMillis())?hiz.gpuMillis():0);}
    public void addDebug(List<String> out){out.add("Vulkan Voxy clusters="+drawnSections+" tasks="+meshService.pending()+" models="+models.uploadedModels+(geometryPressure?" (coarse coverage: GPU pressure)":""));out.add(String.format(Locale.ROOT,"LOD CPU %.3f ms / GPU %.3f ms; GPU %.1f / %.1f MiB",cpuMillis,gpuMillis(),budget.used()/1048576.0,budget.limit()/1048576.0));}
    @Override public void close(){if(closed)return;closed=true;if(current==this){current=null;me.cortex.voxy.common.util.DataMemoryBudget.setGpuPressure(false);}
        meshService.close();bakery.shutdown();world.getMapper().setBiomeCallback(null);geometry.close();models.close();if(quadIndices!=null)quadIndices.close();cullDiagnostics.close();
        for(int i=0;i<2;i++){metadata[i].close();commands[i].close();coverage[i].close();}sampler.close();timing.close();opaqueTiming.close();transparentTiming.close();if(pipelines!=null)pipelines.close();if(hiz!=null)hiz.close();MemoryUtil.memFree(push);world.releaseRef();
    }
}
