#!/usr/bin/env python3
"""Compile independently usable memory primitives, without Voxy or Minecraft."""
from pathlib import Path
import os,subprocess,shutil
root=Path(__file__).resolve().parents[1];out=root/'build/primitives';out.mkdir(parents=True,exist_ok=True)
java_home=os.environ.get('JAVA_HOME');javac=str(Path(java_home)/'bin/javac') if java_home else shutil.which('javac');java=str(Path(java_home)/'bin/java') if java_home else shutil.which('java')
if not java or not javac:raise RuntimeError('Set JAVA_HOME to Java 25')
harness=out/'MemorySmoke.java';harness.write_text('''package me.cortex.voxy.client.vulkan;
import java.util.*;
import me.cortex.voxy.common.util.ByteBudget;
import me.cortex.voxy.common.world.ImportUpdatePolicy;
public final class MemorySmoke {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  var budget=new GpuBudget(128);budget.reserve(80);boolean denied=false;try{budget.reserve(49);}catch(GpuBudget.BudgetExceeded e){denied=true;}check(denied);budget.reserve(48);budget.release(80);budget.reserve(80);check(budget.used()==128&&budget.peak()==128);
  var manager=new GpuBudgetManager();var held=manager.forLimit(128);held.reserve(120);check(!manager.readyForRenderer(64));held.release(120);check(manager.readyForRenderer(64)&&manager.forLimit(64).limit()==64);
  var byteBudget=new ByteBudget(100);check(byteBudget.tryReserve(100));check(!byteBudget.tryReserve(1));byteBudget.release(100);check(byteBudget.usedBytes()==0);
  check(UniformRange.padded(12,512)==16&&UniformRange.padded(12,12)==12);
  check(!RasterCoverage.canCover(3.1,2.1,3.2,2.2)&&RasterCoverage.canCover(3.49,2.49,3.51,2.51));
  check(QuadStream.bytes(4*1024*1024)==16L*1024*1024);
  var refinement=new RefinementBudget(8192);refinement.pressure(100);check(refinement.limit()>100&&refinement.limit()<8192);for(int i=0;i<100;i++)refinement.recover();check(refinement.limit()==8192);
  check(LodWorkingSet.priority(2,0,0,-4,64,64,64,1440,104)==LodWorkingSet.priority(2,0,0,4,64,64,64,1440,104));
  var oldStamps=List.of(new ImportCheckpointPolicy.Stamp("a",4096,1),new ImportCheckpointPolicy.Stamp("b",4096,1),new ImportCheckpointPolicy.Stamp("c",4096,1));
  var newStamps=List.of(oldStamps.get(0),oldStamps.get(1),new ImportCheckpointPolicy.Stamp("c",4096,2));check(ImportCheckpointPolicy.resume(oldStamps,newStamps,1,77).equals(new ImportCheckpointPolicy.Cursor(1,77)));
  check(ImportUpdatePolicy.accepts(false,true,true)&&!ImportUpdatePolicy.accepts(true,true,true)&&!ImportUpdatePolicy.accepts(false,true,false));
  var allocator=new RangeAllocator(4096);var live=new HashMap<Integer,Integer>();var random=new Random(42);
  for(int i=0;i<10000;i++){if(live.isEmpty()||random.nextBoolean()){int size=(random.nextInt(16)+1)*8;int pos=allocator.allocate(size);if(pos>=0)live.put(pos,size);}else{int pos=live.keySet().iterator().next();allocator.release(pos,live.remove(pos));}check(4096==allocator.freeBytes()+live.values().stream().mapToInt(Integer::intValue).sum());}
  for(var entry:live.entrySet())allocator.release(entry.getKey(),entry.getValue());check(allocator.allocate(4096)==0);System.out.println("PASS standalone primitives: budget, retirement accounting, uniform padding, conservative sample bounds, 10000 range operations");
 }
}''')
src=root/'src/main/java/me/cortex/voxy'
files=[src/'client/vulkan'/f for f in ['GpuBudget.java','RangeAllocator.java','UniformRange.java','RasterCoverage.java','GpuBudgetManager.java','QuadStream.java','RefinementBudget.java','LodWorkingSet.java','ImportCheckpointPolicy.java']]+[src/'common/util/ByteBudget.java',src/'common/world/ImportUpdatePolicy.java',harness]
subprocess.run([javac,'--release','25','-d',str(out),*map(str,files)],check=True)
subprocess.run([java,'-Xmx128m','-cp',str(out),'me.cortex.voxy.client.vulkan.MemorySmoke'],check=True)
