#!/usr/bin/env python3
"""Summarize completed QA runs without reading private launcher credentials."""
from pathlib import Path
from array import array
import csv,json,math,argparse,statistics
p=argparse.ArgumentParser();p.add_argument('runs',nargs='+');p.add_argument('--warmup',type=float,default=60);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[1];results=[]
fields=['frame_ms','lod_cpu_ms','lod_gpu_ms','game_render_cpu_ms','game_render_gpu_ms','heap_used','gpu_bytes','data_payload_bytes','geometry_pending_bytes','direct_buffer_bytes','mapped_buffer_bytes','database_cache_bytes','database_memtable_bytes']
for name in a.runs:
 d=r/'build/evidence'/name;spec=json.loads((d/'test-spec.json').read_text());file=d/('frames-'+spec['mode']+'.csv')
 values={f:array('d') for f in fields};windows={};first=last=None;count=focus=pressure=0
 with file.open() as stream:
  for row in csv.DictReader(stream):
   if not row.get('seconds') or not row.get('heap_used'):continue
   try:t=float(row['seconds']);heap=int(row['heap_used'])
   except (ValueError,TypeError):continue
   count+=1;focus+=row['focused']=='true';pressure+=row.get('coarse_pressure')=='true';first=first or row;last=row
   w=windows.setdefault(int(t//300),{'heap_min':heap,'heap_peak':heap,'gpu_peak':0,'samples':0})
   w['samples']+=1;w['heap_min']=min(w['heap_min'],heap);w['heap_peak']=max(w['heap_peak'],heap);w['gpu_peak']=max(w['gpu_peak'],int(row['gpu_bytes']))
   if t<a.warmup:continue
   for f in fields:
    try:v=float(row[f])
    except (KeyError,ValueError,TypeError):continue
    if math.isfinite(v):values[f].append(v)
 result={'name':name,'exit':json.loads((d/'exit.json').read_text()),'artifact':json.loads((d/'tested-artifact.json').read_text()),'spec':{k:v for k,v in spec.items() if k!='dependencies'},'samples':count,'duration':float(last['seconds']),'focused_samples':focus,'pressure_samples':pressure,'heap_windows':windows,'gc_count':int(last['gc_count'])-int(first['gc_count']),'gc_ms':int(last['gc_ms'])-int(first['gc_ms']),'fields':{}}
 for f,v in values.items():
  v=sorted(v)
  if v:result['fields'][f]={k:v[min(len(v)-1,int((len(v)-1)*q))] for k,q in [('p50',.5),('p95',.95),('p99',.99),('peak',1)]}
 rss=array('d');physical=array('d');swap_first=swap_last=None
 for line in (d/'process.jsonl').open():
  proc=json.loads(line);rss.append(proc['rss_kib']);
  if proc.get('phys_footprint_bytes') is not None:physical.append(proc['phys_footprint_bytes'])
  swap_first=swap_first or proc['swap'];swap_last=proc['swap']
 result.update(rss_peak_kib=max(rss),phys_footprint_peak_bytes=max(physical) if physical else None,swap_before=swap_first,swap_after=swap_last)
 console=(d/'console.log').read_text(errors='replace');result['diagnostics']={s:console.count(s) for s in ['OutOfMemoryError','Invalid Metal usage','Vulkan mesh build failed','Memory budget exceeded','QA window unexpectedly became visible','failed assertion']}
 results.append(result)
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(results,indent=2)+'\n');print(a.output)
