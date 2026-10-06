package me.cortex.voxy.client.vulkan;
import java.util.*;
/** Current-world files may change while playing; rewind only the first changed processed region. */
public final class ImportCheckpointPolicy {
    public record Stamp(String name,long size,long modified){}
    public record Cursor(int region,int chunk){}
    private ImportCheckpointPolicy(){}
    public static Cursor resume(List<Stamp> previous,List<Stamp> current,int region,int chunk){
        if(region<0||region>previous.size()||chunk<0||chunk>=1024)return new Cursor(0,0);
        var positions=new HashMap<String,Integer>();for(int i=0;i<previous.size();i++)positions.put(previous.get(i).name(),i);
        for(int i=0;i<current.size();i++){
            var now=current.get(i);Integer old=positions.get(now.name());
            if(old==null)return new Cursor(i,0);
            if(old>=region)return new Cursor(i,old==region&&now.equals(previous.get(old))?chunk:0);
            if(!now.equals(previous.get(old)))return new Cursor(i,0);
        }
        return new Cursor(current.size(),0);
    }
}
