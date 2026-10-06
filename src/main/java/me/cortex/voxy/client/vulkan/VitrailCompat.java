package me.cortex.voxy.client.vulkan;
import com.mojang.renderpearl.api.commands.*;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import me.cortex.voxy.common.Logger;
import net.fabricmc.loader.api.FabricLoader;
import java.lang.invoke.*;
/** Optional Vitrail 0.12 lease and depth adapter; the existing loader owns all pack effects. */
final class VitrailCompat {
    private static boolean initialized;
    private static Class<?> wrapper;
    private static MethodHandle suspend,real,descriptor,flushGeometry,drawingPack;
    private static Object solidPass;
    private static void initialize(){
        if(initialized)return;initialized=true;if(!FabricLoader.getInstance().isModLoaded("vitrail"))return;
        try{
            var lookup=MethodHandles.publicLookup();
            wrapper=Class.forName("dev.vitrail.render.LevelPass",false,VitrailCompat.class.getClassLoader());
            suspend=lookup.findStatic(wrapper,"suspendCurrent",MethodType.methodType(void.class));
            var method=wrapper.getDeclaredMethod("real");method.setAccessible(true);real=MethodHandles.lookup().unreflect(method);
            var hold=Class.forName("dev.vitrail.render.GeometryHold",false,VitrailCompat.class.getClassLoader());
            flushGeometry=lookup.findStatic(hold,"flush",MethodType.methodType(void.class,java.util.function.Supplier.class));
            var terrain=Class.forName("dev.vitrail.render.TerrainDraw",false,VitrailCompat.class.getClassLoader());
            var pass=Class.forName("dev.vitrail.pack.program.TerrainPass",false,VitrailCompat.class.getClassLoader());solidPass=pass.getField("SOLID").get(null);
            descriptor=lookup.findStatic(terrain,"descriptor",MethodType.methodType(RenderPassDescriptor.class,pass,GpuTextureView.class,GpuTextureView.class));
            var chain=Class.forName("dev.vitrail.render.PackChain",false,VitrailCompat.class.getClassLoader());drawingPack=lookup.findStatic(chain,"drawingPack",MethodType.methodType(boolean.class));
            Logger.info("Voxy Vitrail pass lease and composition adapter enabled");
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported Vitrail 0.12 pass API",e);}
    }
    static void suspendOwnedPass(){initialize();if(suspend!=null)try{flushGeometry.invokeExact((java.util.function.Supplier<String>)null);suspend.invokeExact();flushGeometry.invokeExact((java.util.function.Supplier<String>)null);}catch(Throwable e){throw new IllegalStateException("Vitrail pass suspension failed",e);}}
    static GpuTextureView sceneDepth(RenderTarget target){initialize();if(descriptor==null)return null;try{var d=(RenderPassDescriptor)descriptor.invoke(solidPass,target.getColorTextureView(),target.getDepthTextureView());return d==null||d.depthAttachment()==null?null:d.depthAttachment().textureView();}catch(Throwable e){throw new IllegalStateException("Vitrail scene depth lookup failed",e);}}
    static boolean shaderActive(){initialize();if(drawingPack==null)return false;try{return (boolean)drawingPack.invokeExact();}catch(Throwable e){throw new IllegalStateException(e);}}
    static RenderPass unwrap(RenderPass pass){initialize();if(wrapper!=null&&wrapper.isInstance(pass))try{return (RenderPass)real.invoke(pass);}catch(Throwable e){throw new IllegalStateException("Vitrail pass resumption failed",e);}return pass;}
}
