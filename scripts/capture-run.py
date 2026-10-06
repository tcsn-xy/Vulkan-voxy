#!/usr/bin/env python3
"""Launch a disposable run and sample macOS RSS/swap without touching game configuration."""
from pathlib import Path
import subprocess,json,time,argparse,shutil,fcntl,datetime,os,ctypes,struct
root=Path(__file__).resolve().parents[1]
try:
 usage_lib=ctypes.CDLL('/usr/lib/libproc.dylib');usage_lib.proc_pid_rusage.argtypes=[ctypes.c_int,ctypes.c_int,ctypes.c_void_p];usage_lib.proc_pid_rusage.restype=ctypes.c_int
except OSError:usage_lib=None
def physical_footprint(pid):
 if usage_lib is None:return None
 buf=ctypes.create_string_buffer(4096)
 if usage_lib.proc_pid_rusage(pid,2,ctypes.byref(buf))!=0:return None
 return struct.unpack_from('Q',buf.raw,72)[0]

p=argparse.ArgumentParser();p.add_argument('--name',required=True);p.add_argument('--seconds',type=int,default=180);p.add_argument('--metal-validation',action='store_true');p.add_argument('--scenario',choices=['static','features','stability','imports','budget','settings','dense','turns','refresh','water','lifecycle'],default='static');p.add_argument('--backend',choices=['vulkan','opengl'],default='vulkan');p.add_argument('--distance',type=int,default=128);p.add_argument('--near-distance',type=int,default=8);p.add_argument('--mode',choices=['render','reference','baseline'],default='render');p.add_argument('--camera',type=float,nargs=5,default=[320,105,-40,-60,25]);p.add_argument('--legacy-fog',action='store_true');p.add_argument('--legacy-coverage',action='store_true');p.add_argument('--subdivision',type=float,default=64);p.add_argument('--threads',type=int,default=2);p.add_argument('--no-import',action='store_true');p.add_argument('--cadence',type=int,default=60);p.add_argument('--artifact',default='');p.add_argument('--no-raster-cull',action='store_true');p.add_argument('--cull-probe',action='store_true');p.add_argument('--no-subgroup',action='store_true');p.add_argument('--fixed-commands',action='store_true');p.add_argument('--gpu-budget',type=int);p.add_argument('--no-hiz',action='store_true');p.add_argument('--instance-dir',default='');a=p.parse_args()
if '/' in a.name or a.name.startswith('.'):raise ValueError('Use a plain evidence directory name')
(root/'build').mkdir(exist_ok=True)
run_lock=(root/'build/.qa-run.lock').open('w')
fcntl.flock(run_lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
extra=['--near-distance',str(a.near_distance)]+(['--gpu-budget',str(a.gpu_budget)] if a.gpu_budget is not None else [])+(['--instance-dir',a.instance_dir] if a.instance_dir else [])+([flag for flag,enabled in [('--no-raster-cull',a.no_raster_cull),('--cull-probe',a.cull_probe),('--no-subgroup',a.no_subgroup),('--fixed-commands',a.fixed_commands),('--no-hiz',a.no_hiz)] if enabled])+['--cadence',str(a.cadence)]+(['--artifact',a.artifact] if a.artifact else [])+['--subdivision',str(a.subdivision),'--threads',str(a.threads)]+(['--no-import'] if a.no_import else [])+['--camera',*(str(v) for v in a.camera)]+(['--legacy-fog'] if a.legacy_fog else [])+(['--legacy-coverage'] if a.legacy_coverage else [])
subprocess.run(['python3',str(root/'scripts/prepare-qa.py'),'--mode',a.mode,'--seconds',str(a.seconds),'--scenario',a.scenario,'--distance',str(a.distance),'--backend',a.backend,*extra],check=True,cwd=root)
for f in ((Path(a.instance_dir).resolve() if a.instance_dir else root/'build/qa-instance')/'screenshots').glob('qa-*.png'):f.unlink()
output=root/'build/evidence'/a.name
if output.exists():
 archive=root/'build/evidence/archive';archive.mkdir(parents=True,exist_ok=True)
 shutil.move(output,archive/(a.name+'-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')))
output.mkdir(parents=True,exist_ok=True)
qa=Path(a.instance_dir).resolve() if a.instance_dir else root/'build/qa-instance';java=Path(os.environ.get('JAVA_HOME',str(Path.home()/'.cache/voxy-jdk25/jdk-25.0.4.1+1/Contents/Home')))/'bin/java'
spec=json.loads((qa/'test-spec.json').read_text());spec['metal_validation']=a.metal_validation;(qa/'test-spec.json').write_text(json.dumps(spec,indent=2)+'\n')
with (output/'console.log').open('w') as log,(output/'process.jsonl').open('w') as metrics:
 import os
 env=os.environ.copy();env["SDL_MAC_BACKGROUND_APP"]="1"
 if a.metal_validation:env.update(MTL_DEBUG_LAYER='1',MTL_SHADER_VALIDATION='1',MTL_SHADER_VALIDATION_REPORT_TO_STDERR='1')
 proc=subprocess.Popen([str(java),'@'+str(qa/'launch.args')],cwd=root,stdout=log,stderr=subprocess.STDOUT,env=env,preexec_fn=lambda:os.nice(10))
 started=time.monotonic();deadline=started+a.seconds+180;watchdog=False
 while proc.poll() is None:
  if time.monotonic()>deadline:
   watchdog=True;proc.terminate()
   try:proc.wait(timeout=5)
   except subprocess.TimeoutExpired:proc.kill();proc.wait()
   break
  r=subprocess.run(['ps','-o','rss=','-p',str(proc.pid)],capture_output=True,text=True)
  swap=subprocess.run(['sysctl','-n','vm.swapusage'],capture_output=True,text=True).stdout.strip()
  vm=subprocess.run(['vm_stat'],capture_output=True,text=True).stdout
  metrics.write(json.dumps({'seconds':time.monotonic()-started,'rss_kib':int(r.stdout.strip() or 0),'phys_footprint_bytes':physical_footprint(proc.pid),'swap':swap,'vm_stat':vm})+'\n');metrics.flush();time.sleep(2)
 code=124 if watchdog else proc.returncode
for f in [qa/('frames-'+a.mode+'.csv'),qa/'tested-artifact.json',qa/'source-config-sha.json',qa/'test-spec.json',qa/'dependency-manifest.json']:
 if f.exists():shutil.copy2(f,output/f.name)
for f in (qa/'screenshots').glob('qa-*.png'):shutil.copy2(f,output/f.name)
console=(output/'console.log').read_text(errors='replace')
if code==0 and ('VULKAN_QA_COMPLETE' not in console or 'native_hidden=true native_focus=false' not in console or 'VULKAN_QA_REAL_TEXTURE' not in console):code=1
if code==0 and any(t in console for t in ['OutOfMemoryError','Invalid Metal usage','Vulkan mesh build failed','QA window unexpectedly became visible','Memory budget exceeded']):code=1
(output/'exit.json').write_text(json.dumps({'exit_code':code}))
print(output,'exit',code)
raise SystemExit(code)
