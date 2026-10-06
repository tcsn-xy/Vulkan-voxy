package me.cortex.voxy.client.vulkan;
import me.cortex.voxy.client.core.model.*;
import me.cortex.voxy.client.core.rendering.building.*;
import me.cortex.voxy.common.thread.*;
import me.cortex.voxy.common.util.Pair;
import me.cortex.voxy.common.world.*;
import me.cortex.voxy.common.Logger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** CPU selection and disk reads run in the same bounded two-worker pool as voxel conversion. */
public final class VulkanMeshService implements AutoCloseable {
    public static final class Node {
        public final long key;
        public volatile VulkanGeometry.Mesh mesh;
        public volatile boolean ready;
        public volatile int children;
        public volatile long lastVisit, retryAfter,builtVersion;
        public final AtomicLong version=new AtomicLong();
        public final AtomicBoolean queued=new AtomicBoolean();
        VulkanVoxyRenderer.DrawEntry[] drawEntries;
        boolean finishBuild(boolean published){return !published&&queued.getAndSet(false);}
        Node(long key){this.key=key;}
        // Empty and missing sections are valid results. World dirty callbacks invalidate them when data arrives.
        boolean requiresBuild(){return !ready||builtVersion!=version.get();}
    }
    public record Result(Node node,long version,BuiltSection section,long bytes,MeshClusters.Cluster[] clusters) {}
    public record View(double x,double y,double z,int radiusBlocks,float subdivision,int height,int minY,int maxY) {}
    private final WorldEngine world;
    private final ModelBakerySubsystem bakery;
    private final Service meshService,selectService;
    private final ConcurrentLinkedQueue<Node> tasks=new ConcurrentLinkedQueue<>();
    public final ConcurrentLinkedQueue<Result> completed=new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Long,Node> nodes=new ConcurrentHashMap<>();
    private final AtomicInteger pending=new AtomicInteger();
    private final AtomicLong pendingBytes=new AtomicLong();
    private final AtomicBoolean selecting=new AtomicBoolean();
    private volatile View view;
    public final AtomicLong worldChanges=new AtomicLong();
    public final AtomicLong reclamationRevision=new AtomicLong();
    private volatile boolean live=true;
    public volatile long[] selected=new long[0];
    public volatile Throwable failure;
    public final AtomicLong meshBuilds=new AtomicLong(),missingBuilds=new AtomicLong();
    public volatile double selectionMillis;
    private final int maxNodes, maxMeshes;
    private volatile boolean geometryPressure;
    private final RefinementBudget refinementBudget;
    private volatile it.unimi.dsi.fastutil.longs.LongSet retained=new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private volatile int rootCount;
    private long lastPressure,lastRecovery;
    public volatile boolean coarseSelection;
    private long lastSelect;
    private final AtomicLong selectionRequests=new AtomicLong(1);
    private volatile long handledSelection;
    public VulkanMeshService(WorldEngine world,ModelBakerySubsystem bakery,ServiceManager manager,int maxMeshes,int rootCapacity){
        this.world=world;this.bakery=bakery;this.maxMeshes=maxMeshes;refinementBudget=new RefinementBudget(maxMeshes);maxNodes=Math.max(maxMeshes,rootCapacity)*4;
        meshService=manager.createService(()->{
            var mesher=new RenderDataFactory(world,bakery.factory,false);
            return new Pair<>(()->build(mesher),mesher::free);
        },500,"Vulkan mesh build",()->pendingBytes.get()<24L*1024*1024);
        selectService=manager.createServiceNoCleanup(()->this::select,2000,"Vulkan LOD selection");
        world.setDirtyCallback((section,flags,neighbor)->{
            worldChanges.incrementAndGet();
            invalidate(section.key);
            for(int i=0;i<6;i++)if((neighbor&(1<<i))!=0){
                int dx=i>=2&&i<4?(i==2?-1:1):0,dy=i<2?(i==0?-1:1):0,dz=i>=4?(i==4?-1:1):0;
                invalidate(WorldEngine.getWorldSectionId(section.lvl,section.x+dx,section.y+dy,section.z+dz));
            }
        });
    }
    private void invalidate(long key){selectionRequests.incrementAndGet();var n=nodes.get(key);if(n!=null){n.version.incrementAndGet();n.retryAfter=0;enqueue(n);}}
    public Collection<Node> nodes(){return nodes.values();}
    public Node node(long key){return nodes.get(key);}
    public int pending(){return pending.get();}
    public long pendingBytes(){return pendingBytes.get();}
    public void setGeometryPressure(boolean pressure){geometryPressure=pressure;}
    public boolean protectedNode(Node n){return WorldEngine.getLevel(n.key)==WorldEngine.MAX_LOD_LAYER||retained.contains(n.key);}
    public void easePressure(){long now=System.nanoTime();if(now-lastRecovery>1_000_000_000L){int before=refinementBudget.limit();refinementBudget.recover();if(before!=refinementBudget.limit())selectionRequests.incrementAndGet();lastRecovery=now;}}

    public void update(View latest){
        if(!latest.equals(view)){view=latest;selectionRequests.incrementAndGet();}
        if(handledSelection==selectionRequests.get())return;
        long now=System.nanoTime();
        if(now-lastSelect<50_000_000)return;
        if(selecting.compareAndSet(false,true)){lastSelect=now;selectService.execute();}
    }
    private void enqueue(Node n){
        if(!live||n.queued.get()||System.nanoTime()<n.retryAfter||pending.get()>=128)return;
        if(n.queued.compareAndSet(false,true)){pending.incrementAndGet();tasks.add(n);meshService.execute();}
    }
    private void build(RenderDataFactory mesher){
        Node n=tasks.poll();if(n==null)return;boolean published=false;
        try {
            long version=n.version.get();
            meshBuilds.incrementAndGet();
            var section=world.acquireIfExists(n.key);
            if(section==null){missingBuilds.incrementAndGet();if(n.mesh==null)n.ready=true;n.builtVersion=version;n.children=0;selectionRequests.incrementAndGet();n.retryAfter=System.nanoTime()+1_000_000_000;return;}
            try{
                var built=mesher.generateMesh(section);
                MeshClusters.Cluster[] clusters=new MeshClusters.Cluster[0];
                long bytes=built.isEmpty()?0:built.geometryBuffer.size;
                if(bytes>0){
                    var old=built;var sorted=new me.cortex.voxy.common.util.MemoryBuffer(bytes);
                    try{clusters=MeshClusters.reorder(old.offsets,Math.toIntExact(bytes/8),i->org.lwjgl.system.MemoryUtil.memGetLong(old.geometryBuffer.address+i*8),(i,q)->org.lwjgl.system.MemoryUtil.memPutLong(sorted.address+i*8L,q),q->MeshClusters.requiresDiscard(q,bakery.factory.getVulkanFaceData((int)(q>>>26)&65535,(int)q&7)));}
                    catch(Throwable e){sorted.free();old.free();throw e;}
                    built=new BuiltSection(old.position,old.childExistence,old.aabb,sorted,old.offsets,old.occupancy);old.geometryBuffer.free();
                }
                pendingBytes.addAndGet(bytes);completed.add(new Result(n,version,built,bytes,clusters));published=true;
            }finally{section.release();}
        }catch(IdNotYetComputedException e){if(e.isIdBlockId)bakery.requestBlockBake(e.id);n.retryAfter=System.nanoTime()+25_000_000;}
        catch(Throwable e){failure=e;Logger.error("Vulkan mesh build failed for "+WorldEngine.pprintPos(n.key),e);}
        finally{if(n.finishBuild(published))pending.decrementAndGet();}
    }
    public void consumed(Result result){selectionRequests.incrementAndGet();pendingBytes.addAndGet(-result.bytes());result.section().free();if(result.node().finishBuild(false))pending.decrementAndGet();}
    private Node get(long key){
        var n=nodes.get(key);if(n!=null)return n;
        if(nodes.size()>=maxNodes)return null;
        return nodes.computeIfAbsent(key,Node::new);
    }
    private void select(){
        long started=System.nanoTime(),request=selectionRequests.get();
        try{
            var v=view;if(!live||v==null)return;
            var roots=new ArrayList<Node>();
            int top=WorldEngine.MAX_LOD_LAYER,scale=32<<top;
            int cx=(int)Math.floor(v.x()/scale),cz=(int)Math.floor(v.z()/scale),r=(v.radiusBlocks()+scale-1)/scale;
            int minY=Math.floorDiv(v.minY(),scale),maxY=Math.floorDiv(v.maxY()-1,scale);
            // Closest roots first, so the limited task queue refines useful terrain first.
            for(int ring=0;ring<=r;ring++)for(int z=cz-ring;z<=cz+ring;z++)for(int x=cx-ring;x<=cx+ring;x++){
                if(ring!=0&&Math.max(Math.abs(x-cx),Math.abs(z-cz))!=ring)continue;
                double dx=Math.max(Math.max(x*scale-v.x(),v.x()-(x+1)*scale),0),dz=Math.max(Math.max(z*scale-v.z(),v.z()-(z+1)*scale),0);
                if(dx*dx+dz*dz>(double)v.radiusBlocks()*v.radiusBlocks())continue;
                for(int y=minY;y<=maxY;y++){
                    // Coarse roots take precedence over the bounded refinement cache.
                    var n=get(WorldEngine.getWorldSectionId(top,x,y,z));if(n==null)continue;
                    touch(n);
                    roots.add(n);
                }
            }
            rootCount=roots.size();
            var out=LodCoverage.select(roots,refinementBudget.limit(),n->refinementPriority(n,v),this::readyChildren,n->Integer.bitCount(n.children));
            long[] keys=new long[out.size()];for(int i=0;i<keys.length;i++)keys[i]=out.get(i).key;
            if(!Arrays.equals(selected,keys)){
                var protectedKeys=new it.unimi.dsi.fastutil.longs.LongOpenHashSet(keys.length*2);
                for(long key:keys){
                    protectedKeys.add(key);
                    for(int level=WorldEngine.getLevel(key)+1,x=WorldEngine.getX(key),y=WorldEngine.getY(key),z=WorldEngine.getZ(key);level<=WorldEngine.MAX_LOD_LAYER;level++){
                        x>>=1;y>>=1;z>>=1;protectedKeys.add(WorldEngine.getWorldSectionId(level,x,y,z));
                    }
                }
                retained=protectedKeys;selected=keys;
            }
            coarseSelection=refinementBudget.reduced();handledSelection=request;
        }catch(Throwable e){failure=e;Logger.error("Vulkan LOD selection failed",e);}finally{selectionMillis=(System.nanoTime()-started)/1e6;selecting.set(false);}
    }
    private void touch(Node n){
        n.lastVisit=System.nanoTime();
        if(n.requiresBuild())enqueue(n);
    }
    private double refinementPriority(Node n,View v){
        if(!n.ready||n.children==0)return 0;
        return LodWorkingSet.priority(WorldEngine.getLevel(n.key),WorldEngine.getX(n.key),WorldEngine.getY(n.key),WorldEngine.getZ(n.key),v.x(),v.y(),v.z(),v.height(),v.subdivision());
    }
    private List<Node> readyChildren(Node n){
        int level=WorldEngine.getLevel(n.key),x=WorldEngine.getX(n.key),y=WorldEngine.getY(n.key),z=WorldEngine.getZ(n.key);
        var children=new ArrayList<Node>(8);boolean ready=true;
        for(int i=0;i<8;i++)if((n.children&(1<<i))!=0){
            var c=get(WorldEngine.getWorldSectionId(level-1,x*2+(i&1),y*2+((i>>>2)&1),z*2+((i>>>1)&1)));
            if(c==null){ready=false;continue;}
            touch(c);children.add(c);ready&=c.ready;
        }
        return ready?children:null;
    }
    public void reclaimRefinements(VulkanGeometry geometry){
        if(!geometryPressure)return;
        long now=System.nanoTime();
        if(now-lastPressure>250_000_000L){if(refinementBudget.pressure(rootCount))selectionRequests.incrementAndGet();lastPressure=now;}
        // Keep the published frontier and its ready parents. Retire only obsolete refinements.
        var candidates=new ArrayList<Node>();for(var n:nodes.values())if(n.mesh!=null&&!protectedNode(n)&&!n.queued.get())candidates.add(n);
        var v=view;if(v==null)return;
        candidates.sort(Comparator.<Node>comparingDouble(n->{int size=32<<WorldEngine.getLevel(n.key);double x=WorldEngine.getX(n.key)*(double)size+size*.5-v.x(),z=WorldEngine.getZ(n.key)*(double)size+size*.5-v.z();return x*x+z*z;}).reversed());
        long released=0;
        for(var n:candidates){var m=n.mesh;if(m==null)continue;geometry.retire(m);released+=m.bytes();reclamationRevision.incrementAndGet();n.mesh=null;n.drawEntries=null;n.ready=false;n.version.incrementAndGet();if(released>=VulkanGeometry.PAGE_BYTES/4)break;}
    }
    public void evictUnvisited(VulkanGeometry geometry){
        // Spare GPU budget is a cache, not a reason to discard meshes every five seconds.
        // Keeping recently viewed refinements avoids coarse placeholders on repeated turns.
        if(nodes.size()<maxNodes&&!geometryPressure&&geometry.availableBytes()>=2L*VulkanGeometry.PAGE_BYTES)return;
        long cutoff=System.nanoTime()-(nodes.size()>=maxNodes?500_000_000L:5_000_000_000L);
        for(var n:nodes.values())if(n.lastVisit<cutoff&&!n.queued.get()&&!protectedNode(n))if(nodes.remove(n.key,n)){if(n.mesh!=null){geometry.retire(n.mesh);reclamationRevision.incrementAndGet();n.mesh=null;}}
    }
    @Override public void close(){
        live=false;world.setDirtyCallback(null);selectService.shutdown();meshService.shutdown();
        Result result;while((result=completed.poll())!=null)consumed(result);tasks.clear();nodes.clear();
    }
}
