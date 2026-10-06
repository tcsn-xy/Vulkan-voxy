package me.cortex.voxy.client.vulkan;
import me.cortex.voxy.common.world.ImportUpdatePolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ImportUpdatePolicyTest {
    @Test void currentDiskRefreshCanRepairOldProtectedCache(){assertTrue(ImportUpdatePolicy.accepts(false,true,true));assertFalse(ImportUpdatePolicy.accepts(false,true,false));}
    @Test void liveExplorationAlwaysWinsEvenDuringRefresh(){for(boolean recorded:new boolean[]{false,true})for(boolean refresh:new boolean[]{false,true})assertFalse(ImportUpdatePolicy.accepts(true,recorded,refresh));}
}
