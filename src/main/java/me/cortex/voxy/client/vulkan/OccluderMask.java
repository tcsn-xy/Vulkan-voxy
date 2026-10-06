package me.cortex.voxy.client.vulkan;
import org.joml.Matrix4fc;
import java.nio.ByteBuffer;
/** Conservative screen tiles removed from terrain occlusion history, never treated as occluders. */
final class OccluderMask {
    static final int SIDE=128,WORDS=SIDE*SIDE/32;
    private final int[] words=new int[WORDS];
    private final float[] matrix=new float[16];
    OccluderMask(Matrix4fc m){m.get(matrix);}
    void cover(double x0,double y0,double z0,double x1,double y1,double z1){
        int[] reject=new int[5];double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;boolean near=false;
        for(int i=0;i<8;i++){
            double x=(i&1)==0?x0:x1,y=(i&2)==0?y0:y1,z=(i&4)==0?z0:z1;
            double px=matrix[0]*x+matrix[4]*y+matrix[8]*z+matrix[12],py=matrix[1]*x+matrix[5]*y+matrix[9]*z+matrix[13];
            double pz=matrix[2]*x+matrix[6]*y+matrix[10]*z+matrix[14],w=matrix[3]*x+matrix[7]*y+matrix[11]*z+matrix[15];
            if(!Double.isFinite(px+py+pz+w)){fill();return;}
            if(px < -w)reject[0]++;if(px>w)reject[1]++;if(py < -w)reject[2]++;if(py>w)reject[3]++;if(pz>w)reject[4]++;
            near|=w<=.05;
            if(w>.05){minX=Math.min(minX,px/w);maxX=Math.max(maxX,px/w);minY=Math.min(minY,py/w);maxY=Math.max(maxY,py/w);}
        }
        for(int n:reject)if(n==8)return;
        if(near){fill();return;}
        int loX=cell(minX)-1,hiX=cell(maxX)+1,loY=cell(minY)-1,hiY=cell(maxY)+1;
        for(int y=Math.max(0,loY);y<=Math.min(SIDE-1,hiY);y++)for(int x=Math.max(0,loX);x<=Math.min(SIDE-1,hiX);x++){int at=x+y*SIDE;words[at>>>5]|=1<<(at&31);}
    }
    private static int cell(double n){return (int)Math.floor((Math.clamp(n,-1,1)*.5+.5)*SIDE);}
    void fill(){java.util.Arrays.fill(words,-1);}
    int cells(){int n=0;for(int word:words)n+=Integer.bitCount(word);return n;}
    void write(ByteBuffer target){for(int i=0;i<WORDS;i++)target.putInt(i*4,words[i]);}
}
