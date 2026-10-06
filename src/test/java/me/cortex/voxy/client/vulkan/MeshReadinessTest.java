package me.cortex.voxy.client.vulkan;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MeshReadinessTest {
    @Test void publishedGeometryKeepsItsQueueReservationUntilUpload(){
        var node=new VulkanMeshService.Node(0);assertTrue(node.queued.compareAndSet(false,true));
        assertFalse(node.finishBuild(true),"A published CPU mesh still occupies a bounded upload slot");
        assertFalse(node.queued.compareAndSet(false,true),"Do not build duplicate geometry while its previous result awaits models/upload");
        assertTrue(node.finishBuild(false));assertTrue(node.queued.compareAndSet(false,true));
    }
    @Test void knownEmptySectionsRemainReadyUntilTheirDataChanges(){
        var node=new VulkanMeshService.Node(0);
        assertTrue(node.requiresBuild());
        node.ready=true;node.builtVersion=node.version.get();
        assertFalse(node.requiresBuild(),"An empty mesh is a valid completed result, not a request to rebuild every selection");
        node.version.incrementAndGet();
        assertTrue(node.requiresBuild(),"Newly imported or explored data must invalidate the empty result");
        node.builtVersion=node.version.get();
        assertFalse(node.requiresBuild());
    }
}
