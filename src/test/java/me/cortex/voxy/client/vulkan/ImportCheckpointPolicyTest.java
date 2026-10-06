package me.cortex.voxy.client.vulkan;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ImportCheckpointPolicyTest {
    private static ImportCheckpointPolicy.Stamp s(String n,long modified){return new ImportCheckpointPolicy.Stamp(n,4096,modified);}
    @Test void futureWorldWritesDoNotDiscardCompletedProgress(){var old=List.of(s("a",1),s("b",1),s("c",1));assertEquals(new ImportCheckpointPolicy.Cursor(1,77),ImportCheckpointPolicy.resume(old,List.of(s("a",1),s("b",1),s("c",2)),1,77));}
    @Test void changedProcessedRegionIsRevisited(){var old=List.of(s("a",1),s("b",1));assertEquals(new ImportCheckpointPolicy.Cursor(0,0),ImportCheckpointPolicy.resume(old,List.of(s("a",2),s("b",1)),1,77));}
    @Test void changedCurrentRegionRestartsOnlyThatRegion(){var old=List.of(s("a",1),s("b",1));assertEquals(new ImportCheckpointPolicy.Cursor(1,0),ImportCheckpointPolicy.resume(old,List.of(s("a",1),s("b",2)),1,77));}
    @Test void insertionAndRemovalCannotSkipUnreadRegions(){var old=List.of(s("b",1),s("c",1));assertEquals(new ImportCheckpointPolicy.Cursor(0,0),ImportCheckpointPolicy.resume(old,List.of(s("a",1),s("b",1),s("c",1)),1,77));assertEquals(new ImportCheckpointPolicy.Cursor(0,77),ImportCheckpointPolicy.resume(old,List.of(s("c",1)),1,77));}
}
