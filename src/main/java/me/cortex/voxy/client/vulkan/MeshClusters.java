package me.cortex.voxy.client.vulkan;
import java.util.*;
import java.util.function.LongUnaryOperator;
/** Reorders identical packed quads into small vertical bounds. Transparent quad order is preserved. */
final class MeshClusters {
    record Cluster(int[] offsets,int end,int aabb,boolean cutout) {int count(){return end-offsets[0];}}
    interface Writer {void put(int index,long quad);}
    static int band(long quad,int group){
        if(group==0)return 4;
        int y=(int)(quad>>>16)&31,axis=((int)quad&7)>>>1;
        int span=axis==0?1:((int)(quad>>>(axis==1?7:3))&15)+1;
        return Math.min(3,(y+span/2)/8);
    }
    static int[] bounds(long q){
        int x=(int)(q>>>21)&31,y=(int)(q>>>16)&31,z=(int)(q>>>11)&31;
        int a=((int)q&7)>>>1,u=((int)(q>>>3)&15)+1,v=((int)(q>>>7)&15)+1;
        return new int[]{x,y,z,x+(a==2?1:u),y+(a==0?1:(a==1?v:u)),z+(a==1?1:v)};
    }
    static boolean requiresDiscard(long quad,int faceData){return (faceData&(1<<22))!=0||((quad&0x7F8)!=0&&(faceData&(1<<23))!=0);}
    static int bucket(long quad,int group,java.util.function.LongPredicate cutout){return group==0?8:band(quad,group)+(cutout.test(quad)?4:0);}
    static Cluster[] reorder(int[] original,int total,LongUnaryOperator reader,Writer writer){return reorder(original,total,reader,writer,q->true);}
    static Cluster[] reorder(int[] original,int total,LongUnaryOperator reader,Writer writer,java.util.function.LongPredicate cutout){
        int[][] counts=new int[9][8],starts=new int[9][8],cursors=new int[9][8],bounds=new int[9][6];
        for(int b=0;b<9;b++){Arrays.fill(bounds[b],0,3,32);}
        for(int group=0;group<8;group++)for(int i=original[group];i<(group==7?total:original[group+1]);i++){
            long q=reader.applyAsLong(i);int b=bucket(q,group,cutout);counts[b][group]++;
            int x=(int)(q>>>21)&31,y=(int)(q>>>16)&31,z=(int)(q>>>11)&31;
            int a=((int)q&7)>>>1,u=((int)(q>>>3)&15)+1,v=((int)(q>>>7)&15)+1;
            int hx=x+(a==2?1:u),hy=y+(a==0?1:(a==1?v:u)),hz=z+(a==1?1:v);
            if(hx>32||hy>32||hz>32)throw new IllegalStateException("Quad outside section bounds");
            bounds[b][0]=Math.min(bounds[b][0],x);bounds[b][1]=Math.min(bounds[b][1],y);bounds[b][2]=Math.min(bounds[b][2],z);
            bounds[b][3]=Math.max(bounds[b][3],hx);bounds[b][4]=Math.max(bounds[b][4],hy);bounds[b][5]=Math.max(bounds[b][5],hz);
        }
        var clusters=new ArrayList<Cluster>(9);int offset=0;
        for(int b:new int[]{3,2,1,0,7,6,5,4,8}){
            int begin=offset;
            for(int g=0;g<8;g++){starts[b][g]=cursors[b][g]=offset;offset+=counts[b][g];}
            if(offset==begin)continue;
            var box=bounds[b];int packed=0;
            for(int axis=0;axis<3;axis++){
                int lo=Math.min(31,box[axis]),hi=Math.min(32,box[axis+3]);
                packed|=lo<<(axis*5);packed|=Math.max(0,hi-lo-1)<<(15+axis*5);
            }
            clusters.add(new Cluster(starts[b],offset,packed,b>=4));
        }
        if(offset!=total)throw new IllegalStateException("Lost mesh quads");
        for(int group=0;group<8;group++)for(int i=original[group];i<(group==7?total:original[group+1]);i++){
            long q=reader.applyAsLong(i);writer.put(cursors[bucket(q,group,cutout)][group]++,q);
        }
        return clusters.toArray(Cluster[]::new);
    }
}
