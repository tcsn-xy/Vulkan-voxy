package me.cortex.voxy.client.vulkan;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;
/** Standalone settings remain usable when Sodium disables its OpenGL renderer. */
public final class VulkanConfigScreen extends Screen {
    private final Screen parent;
    private final VoxyConfig config=VoxyConfig.CONFIG;
    public VulkanConfigScreen(Screen parent){super(Component.translatable("voxy.config.title"));this.parent=parent;}
    private static Component text(String key){return Component.translatable("voxy.config."+key);}
    private Component switchLabel(String key,boolean enabled){return Component.translatable("voxy.config.vulkan.switch",text(key),Component.translatable(enabled?"options.on":"options.off"));}
    @Override protected void init(){int x=width/2-150,y=50;
        addRenderableWidget(Button.builder(switchLabel("general.enabled",config.enabled),b->{config.enabled=!config.enabled;b.setMessage(switchLabel("general.enabled",config.enabled));}).bounds(x,y,300,20).tooltip(net.minecraft.client.gui.components.Tooltip.create(text("general.enabled.tooltip"))).build());y+=26;
        addRenderableWidget(Button.builder(switchLabel("general.ingest",config.ingestEnabled),b->{config.ingestEnabled=!config.ingestEnabled;b.setMessage(switchLabel("general.ingest",config.ingestEnabled));}).bounds(x,y,300,20).build());y+=26;
        addRenderableWidget(Button.builder(switchLabel("general.rendering",config.enableRendering),b->{config.enableRendering=!config.enableRendering;b.setMessage(switchLabel("general.rendering",config.enableRendering));}).bounds(x,y,300,20).build());y+=26;
        slider(x,y,"general.renderDistance",32,1024,1,Math.round(config.sectionRenderDistance*32),v->config.sectionRenderDistance=v/32f);y+=26;
        slider(x,y,"general.subDivisionSize",16,256,1,Math.round(config.subDivisionSize),v->config.subDivisionSize=v);y+=26;
        slider(x,y,"general.serviceThreads",1,4,1,config.serviceThreads,v->config.serviceThreads=v);y+=26;
        slider(x,y,"vulkan.gpuBudget",128,768,1,config.gpuBudgetMiB,v->config.gpuBudgetMiB=v);y+=26;
        slider(x,y,"vulkan.uploadBudget",1,16,1,config.uploadBudgetMiB,v->config.uploadBudgetMiB=v);y+=26;
        addRenderableWidget(Button.builder(switchLabel("vulkan.hiz",config.hizEnabled),b->{config.hizEnabled=!config.hizEnabled;b.setMessage(switchLabel("vulkan.hiz",config.hizEnabled));}).bounds(x,y,300,20).build());y+=30;
        addRenderableWidget(Button.builder(text("vulkan.save"),b->onClose()).bounds(x,y,300,20).build());
    }
    private void slider(int x,int y,String key,int min,int max,int step,int initial,IntConsumer apply){
        var slider=new AbstractSliderButton(x,y,300,20,Component.empty(),Math.clamp((initial-min)/(double)(max-min),0,1)){
            private int actual(){return Math.clamp(min+(int)Math.round(value*(max-min)/step)*step,min,max);}
            {updateMessage();}
            @Override protected void updateMessage(){setMessage(Component.translatable("voxy.config.vulkan.slider",text(key),actual()));}
            @Override protected void applyValue(){apply.accept(actual());}
        };
        slider.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text(key+".tooltip")));addRenderableWidget(slider);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partial){super.extractRenderState(g,mouseX,mouseY,partial);g.centeredText(font,title,width/2,22,0xffffffff);}
    @Override public void onClose(){
        config.save();
        VoxyConfig.applyVulkanSettings();
        minecraft.setScreenAndShow(parent);
    }
}
