package me.cortex.voxy.client.vulkan;

import org.joml.Matrix4fc;

/** Immutable camera-relative side/near planes, matching cull.comp's conservative reverse-depth test. */
final class DistantFrustum {
    private final double[] planes=new double[20];
    private final boolean finite;
    DistantFrustum(Matrix4fc m){
        put(0,m.m03()+m.m00(),m.m13()+m.m10(),m.m23()+m.m20(),m.m33()+m.m30());
        put(4,m.m03()-m.m00(),m.m13()-m.m10(),m.m23()-m.m20(),m.m33()-m.m30());
        put(8,m.m03()+m.m01(),m.m13()+m.m11(),m.m23()+m.m21(),m.m33()+m.m31());
        put(12,m.m03()-m.m01(),m.m13()-m.m11(),m.m23()-m.m21(),m.m33()-m.m31());
        put(16,m.m03()-m.m02(),m.m13()-m.m12(),m.m23()-m.m22(),m.m33()-m.m32());
        boolean valid=true;for(double p:planes)valid&=Double.isFinite(p);finite=valid;
    }
    private void put(int i,double a,double b,double c,double d){planes[i]=a;planes[i+1]=b;planes[i+2]=c;planes[i+3]=d;}
    boolean intersects(double x,double y,double z,double size){return intersects(x,y,z,size,size,size);}
    boolean intersects(double x,double y,double z,double sx,double sy,double sz){
        if(!finite)return true;
        for(int i=0;i<20;i+=4){
            double a=planes[i],b=planes[i+1],c=planes[i+2];
            double maximum=a*(x+(a>=0?sx:0))+b*(y+(b>=0?sy:0))+c*(z+(c>=0?sz:0))+planes[i+3];
            if(maximum < -0.001)return false;
        }
        return true;
    }
}
