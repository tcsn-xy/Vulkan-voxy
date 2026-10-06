package me.cortex.voxy.client.vulkan;

import java.nio.ByteBuffer;

/** Bounded 32 x 64 x 32 near-section snapshot, with the exact shader word layout. */
public final class NearCoverageGrid {
    public final int x,y,z;
    private final int[] words=new int[2048];
    private boolean frozen;
    private int cells;
    private long signature;
    public NearCoverageGrid(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
    private static int index(int x,int y,int z){return x<0||x>=32||y<0||y>=64||z<0||z>=32?-1:x+z*32+y*1024;}
    public void mark(int sx,int sy,int sz){
        if(frozen)throw new IllegalStateException("Coverage snapshot already published");
        int at=index(sx-x,sy-y,sz-z);if(at<0)return;
        int bit=1<<(at&31);if((words[at>>>5]&bit)==0){words[at>>>5]|=bit;cells++;}
    }
    public void freeze(){
        if(frozen)return;frozen=true;long hash=0xcbf29ce484222325L;
        for(int word:words)hash=(hash^Integer.toUnsignedLong(word))*0x100000001b3L;
        signature=hash^((long)x<<32)^y^((long)z<<16);
    }
    public boolean intersectsBounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        int loX=Math.max(0,(int)Math.floor(minX/16)-x),hiX=Math.min(31,(int)Math.floor(maxX/16)-x);
        int loY=Math.max(0,(int)Math.floor(minY/16)-y),hiY=Math.min(63,(int)Math.floor(maxY/16)-y);
        int loZ=Math.max(0,(int)Math.floor(minZ/16)-z),hiZ=Math.min(31,(int)Math.floor(maxZ/16)-z);
        for(int sy=loY;sy<=hiY;sy++)for(int sz=loZ;sz<=hiZ;sz++)for(int sx=loX;sx<=hiX;sx++){int at=index(sx,sy,sz);if((words[at>>>5]&(1<<(at&31)))!=0)return true;}
        return false;
    }
    public int cells(){return cells;}
    public long signature(){if(!frozen)throw new IllegalStateException("Unpublished coverage");return signature;}
    public void writeTo(ByteBuffer target,int nx,int ny,int nz){
        if(!frozen||target.capacity()<8192)throw new IllegalArgumentException("Invalid coverage target");
        if(x==nx&&y==ny&&z==nz){target.duplicate().order(target.order()).position(0).asIntBuffer().put(words);return;}
        for(int i=0;i<2048;i++)target.putInt(i*4,0);
        for(int i=0;i<2048;i++)for(int bits=words[i];bits!=0;bits&=bits-1){
            int at=(i<<5)+Integer.numberOfTrailingZeros(bits);
            int shifted=index(x+(at&31)-nx,y+(at>>>10)-ny,z+((at>>>5)&31)-nz);
            if(shifted>=0){int byteOffset=(shifted>>>5)*4;target.putInt(byteOffset,target.getInt(byteOffset)|(1<<(shifted&31)));}
        }
    }
    public static int verticalBase(int cameraSection,int minSection,int maxSection){
        return maxSection-minSection<64?minSection:Math.clamp(cameraSection-32,minSection,maxSection-63);
    }
}
