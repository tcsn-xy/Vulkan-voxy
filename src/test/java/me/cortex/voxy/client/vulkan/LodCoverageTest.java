package me.cortex.voxy.client.vulkan;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LodCoverageTest {
    private record Tile(int start,int length) {}
    private static List<Tile> children(Tile tile){
        if(tile.length()==1)return List.of();
        int half=tile.length()/2;return List.of(new Tile(tile.start(),half),new Tile(tile.start()+half,half));
    }
    private static void assertCoverage(List<Tile> tiles,int length){
        int[] coverage=new int[length];
        for(var tile:tiles)for(int i=tile.start();i<tile.start()+tile.length();i++)coverage[i]++;
        for(int count:coverage)assertEquals(1,count,"Coverage must have neither gaps nor overlap");
    }
    @Test void budgetKeepsEveryRootAndRefinesOnlyWholeParents(){
        var roots=List.of(new Tile(0,64),new Tile(64,64),new Tile(128,64));
        for(int limit=1;limit<=192;limit++){
            var selected=LodCoverage.select(roots,limit,t->t.length(),LodCoverageTest::children);
            assertTrue(selected.size()<=Math.max(limit,roots.size()));assertCoverage(selected,192);
        }
    }
    @Test void missingChildrenKeepTheParent(){
        var root=new Tile(0,64);
        var selected=LodCoverage.select(List.of(root),100,t->t.length(),t->t.length()==16?null:children(t));
        assertCoverage(selected,64);assertTrue(selected.stream().allMatch(t->t.length()==16));
    }
    @Test void highestPriorityAreaRefinesFirst(){
        var roots=List.of(new Tile(0,16),new Tile(16,16));
        var selected=LodCoverage.select(roots,3,t->t.start()==0?100:1,LodCoverageTest::children);
        assertCoverage(selected,32);assertTrue(selected.contains(new Tile(16,16)));assertFalse(selected.contains(roots.getFirst()));
    }
    @Test void randomReadinessAndLimitsNeverDropCoverage(){
        var roots=List.of(new Tile(0,128),new Tile(128,128));
        for(int seed=0;seed<500;seed++){
            var random=new Random(seed);var ready=new HashMap<Tile,Boolean>();
            var selected=LodCoverage.select(roots,2+random.nextInt(100),t->t.length(),
                    t->ready.computeIfAbsent(t,k->random.nextBoolean())?children(t):null);
            assertCoverage(selected,256);
        }
    }
}
