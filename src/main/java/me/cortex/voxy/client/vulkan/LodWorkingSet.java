package me.cortex.voxy.client.vulkan;
/** Shared selection policy; rendering visibility must not control cached geometry readiness. */
final class LodWorkingSet {
    static double priority(int level,int x,int y,int z,double cameraX,double cameraY,double cameraZ,int height,float threshold){
        if(level==0)return 0;
        int size=32<<level;
        double dx=x*(double)size+size*.5-cameraX,dy=y*(double)size+size*.5-cameraY,dz=z*(double)size+size*.5-cameraZ;
        double distance=Math.max(1,Math.sqrt(dx*dx+dy*dy+dz*dz)-size*.866);
        double score=size*height/distance;
        return score>threshold?score:0;
    }
}
