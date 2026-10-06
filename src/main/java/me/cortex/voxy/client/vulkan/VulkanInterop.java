package me.cortex.voxy.client.vulkan;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.backend.vulkan.*;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import me.cortex.voxy.client.mixin.vulkan.*;
import me.cortex.voxy.common.Logger;
import org.lwjgl.vulkan.VkCommandBuffer;
/** All version-specific engine access lives here; no second device, queue, or swapchain. */
public final class VulkanInterop {
    private static VulkanDevice device;
    private static final GpuBudgetManager budgets=new GpuBudgetManager();
    public static boolean subgroupBallot;
    public static GpuBudget budget(int mib){return budgets.forLimit(mib*1024L*1024);}
    public static boolean readyForRenderer(int mib){return budgets.readyForRenderer(mib*1024L*1024);}
    public static boolean initialize() {
        var gpu=RenderSystem.getDevice();
        if (!(gpu instanceof FrontendDeviceAccessor a) || !(a.voxy$backend() instanceof VulkanDevice vk)) return false;
        device=vk;
        var enabled=((VulkanDeviceAccessor)vk).voxy$enabledFeatures();
        if(!enabled.contains(VulkanFeatureSets.REQUIRED_FEATURESET)) throw new IllegalStateException("Voxy requires Minecraft's enabled Vulkan base feature set");
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
            var v12=org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features.calloc(stack).sType$Default();
            var f2=org.lwjgl.vulkan.VkPhysicalDeviceFeatures2.calloc(stack).sType$Default().pNext(v12.address());
            org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2(vk.vkDevice().getPhysicalDevice(),f2);
            var subgroup=org.lwjgl.vulkan.VkPhysicalDeviceSubgroupProperties.calloc(stack).sType$Default();
            var properties=org.lwjgl.vulkan.VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(subgroup.address());
            org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceProperties2(vk.vkDevice().getPhysicalDevice(),properties);
            int required=org.lwjgl.vulkan.VK11.VK_SUBGROUP_FEATURE_BASIC_BIT|org.lwjgl.vulkan.VK11.VK_SUBGROUP_FEATURE_BALLOT_BIT;
            subgroupBallot=(subgroup.supportedStages()&org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_COMPUTE_BIT)!=0&&(subgroup.supportedOperations()&required)==required;
            subgroupBallot&=!Boolean.getBoolean("voxy.qa.noSubgroup");
            Logger.info("Voxy Vulkan subgroup ballot="+subgroupBallot+" size="+subgroup.subgroupSize());
            Logger.info("Voxy Vulkan indirect count supported="+v12.drawIndirectCount()+" enabled="+enabled.features().stream().anyMatch(f->f.name().equals("drawIndirectCount")));
        }
        Logger.info("Voxy Vulkan attached to Minecraft device: " + vk.getDeviceInfo() + "; enabled features=" + enabled.name());
        return true;
    }
    public static boolean active() {return device!=null;}
    public static VulkanDevice device() {if(device==null) throw new IllegalStateException("Not Vulkan"); return device;}
    public static VulkanCommandEncoder encoder() {return device().createCommandEncoder();}
    public static long submitIndex() {return ((VulkanEncoderAccessor)encoder()).voxy$submitIndex();}
    public static VkCommandBuffer commands(RenderPass pass) {
        var backend=((FrontendPassAccessor)pass).voxy$backend();
        return ((VulkanPassAccessor)backend).voxy$commandBuffer();
    }
    public static void endAndExecute(VkCommandBuffer cb) {
        check(org.lwjgl.vulkan.VK10.vkEndCommandBuffer(cb),"end command buffer");
        encoder().execute(cb);
    }
    public static void check(int result,String action) {if(result!=0) throw new IllegalStateException(action+": Vulkan result="+result);}
}
