package me.cortex.voxy.client.vulkan;
import me.cortex.voxy.client.mixin.vulkan.SpriteContentsAccessor;
import net.minecraft.client.renderer.texture.SpriteLoader;
import org.lwjgl.system.MemoryUtil;
/** Immutable CPU copy captured before GPU upload; never reads an OpenGL or Vulkan image. */
public final class CpuBlockAtlas {
    public record Snapshot(int width, int height, int[] pixels, long version) {}
    private static volatile Snapshot snapshot;
    public static void capture(SpriteLoader.Preparations p) {
        long bytes=(long)p.width()*p.height()*4;
        if (bytes>64L*1024*1024) throw new IllegalStateException("Voxy CPU atlas exceeds 64 MiB budget: " + bytes);
        int[] pixels=new int[Math.multiplyExact(p.width(),p.height())];
        for (var sprite:p.regions().values()) {
            var image=((SpriteContentsAccessor)sprite.contents()).voxy$image();
            int w=sprite.contents().width(), h=sprite.contents().height();
            // Static first frame is the Voxy bake contract; memory is the native ABGR byte order.
            long ptr=image.getPointer();
            int startX=Math.round(sprite.getU0()*p.width()),startY=Math.round(sprite.getV0()*p.height());
            for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
                pixels[(startY+y)*p.width()+startX+x]=MemoryUtil.memGetInt(ptr+4L*(y*image.getWidth()+x));
            }
        }
        long version=snapshot==null?1:snapshot.version()+1;
        snapshot=new Snapshot(p.width(),p.height(),pixels,version);
    }
    public static Snapshot snapshot() {
        var s=snapshot;
        if(s==null) throw new IllegalStateException("Voxy: CPU block atlas has not been loaded");
        return s;
    }
}
