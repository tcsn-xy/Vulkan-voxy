package me.cortex.voxy.client.vulkan;
/** Conservative single-sample pixel bounds. A rejected box contains no pixel-centre sample. */
final class RasterCoverage {
    static final double GUARD=.015625;
    static boolean canCover(double minX,double minY,double maxX,double maxY){
        if(!Double.isFinite(minX+minY+maxX+maxY))return true;
        return Math.ceil(minX-.5-GUARD)<=Math.floor(maxX-.5+GUARD)&&Math.ceil(minY-.5-GUARD)<=Math.floor(maxY-.5+GUARD);
    }
}
