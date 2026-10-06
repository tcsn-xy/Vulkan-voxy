#!/usr/bin/env python3
"""Create a Vulkan QA instance. The installed instance is read-only input."""
from pathlib import Path
import json, shutil, uuid, hashlib, argparse
ROOT=Path(__file__).resolve().parents[1]
SOURCE=Path.home()/'Library/Application Support/minecraft/versions/26.3-Fabric'
DEST=ROOT/'build/qa-instance'
parser=argparse.ArgumentParser();parser.add_argument('--world',default='');parser.add_argument('--mode',choices=['render','reference','baseline'],default='render');parser.add_argument('--scenario',choices=['static','features','stability','imports','budget','settings','dense'],default='static');parser.add_argument('--backend',choices=['vulkan','opengl'],default='vulkan');parser.add_argument('--distance',type=int,default=128);parser.add_argument('--seconds',type=int,default=120);parser.add_argument('--width',type=int,default=3440);parser.add_argument('--height',type=int,default=1440);parser.add_argument('--camera',type=float,nargs=5,default=[320,105,-40,-60,25]);parser.add_argument('--legacy-fog',action='store_true');parser.add_argument('--legacy-coverage',action='store_true');parser.add_argument('--subdivision',type=float,default=64);parser.add_argument('--threads',type=int,default=2);parser.add_argument('--no-import',action='store_true');parser.add_argument('--cadence',type=int,default=60);parser.add_argument('--artifact',default='');parser.add_argument('--no-raster-cull',action='store_true');parser.add_argument('--cull-probe',action='store_true');parser.add_argument('--no-subgroup',action='store_true');parser.add_argument('--fixed-commands',action='store_true');parser.add_argument('--instance-dir',default='');parser.add_argument('--gpu-budget',type=int);parser.add_argument('--source-instance',type=Path,default=SOURCE);parser.add_argument('--world-path',type=Path);a=parser.parse_args();SOURCE=a.source_instance.expanduser().resolve()
if a.instance_dir:DEST=Path(a.instance_dir).resolve()
if DEST==SOURCE or SOURCE in DEST.parents or DEST in SOURCE.parents:raise ValueError('QA must be separate from the installed instance')
DEST.mkdir(parents=True,exist_ok=True)
(DEST/'.vulkan-voxy-disposable-qa').write_text('Isolated Vulkan Voxy QA instance. No changes to the source instance.\n')
for n in ['mods','config','saves','screenshots']:(DEST/n).mkdir(exist_ok=True)
# Only the runtime and data-generation dependencies needed by this isolated world.
allowed=('fabric-api-','sodium-fabric-','lithium-fabric-','modmenu-','tectonic-','lithostitched-')
dependencies=DEST/'dependency-manifest.json'
if not dependencies.exists():
 for jar in (SOURCE/'mods').glob('*.jar'):
  if jar.name.startswith(allowed) and not (DEST/'mods'/jar.name).exists():shutil.copy2(jar,DEST/'mods'/jar.name)
 frozen={jar.name:hashlib.sha256(jar.read_bytes()).hexdigest() for jar in (DEST/'mods').glob('*.jar') if not jar.name.startswith('voxy')}
 dependencies.write_text(json.dumps(frozen,indent=2)+'\n')
else:
 frozen=json.loads(dependencies.read_text())
 actual={jar.name:hashlib.sha256(jar.read_bytes()).hexdigest() for jar in (DEST/'mods').glob('*.jar') if not jar.name.startswith('voxy')}
 if actual!=frozen:raise RuntimeError('QA dependencies changed; preserve the old evidence and explicitly prepare a separate instance')
jars=list((ROOT/'build/libs').glob('voxy-vulkan-*.jar'))
if not jars:raise FileNotFoundError('Build Voxy Vulkan first')
for jar in (DEST/'mods').glob('voxy*.jar'):jar.unlink()
jar=Path(a.artifact).resolve() if a.artifact else max(jars,key=lambda p:p.stat().st_mtime);shutil.copy2(jar,DEST/'mods'/jar.name)
keys={'renderDistance':'8','simulationDistance':'5','pauseOnLostFocus':'false','fullscreen':'false','maxFps':'260','enableVsync':'false','guiScale':'2','lang':'zh_cn','preferredGraphicsBackend':json.dumps(a.backend),'resourcePacks':'["vanilla"]','tutorialStep':'none','inactivityFpsLimit':'"minimized"','soundCategory_master':'0.0'}
options_template=DEST/'options-template.txt'
if not options_template.exists():shutil.copy2(DEST/'options.txt' if (DEST/'options.txt').exists() else SOURCE/'options.txt',options_template)
lines=[s for s in options_template.read_text().splitlines() if s.split(':',1)[0] not in keys]
(DEST/'options.txt').write_text('\n'.join(lines+[k+':'+v for k,v in keys.items()])+'\n')
(DEST/'config/voxy-config.json').write_text(json.dumps({'enabled':True,'enable_rendering':a.mode!='baseline','ingest_enabled':True,'section_render_distance':a.distance/32,'service_threads':a.threads,'sub_division_size':16 if a.scenario=='budget' else a.subdivision,'dont_use_sodium_builder_threads':True,'heap_budget_mi_b':384,'native_budget_mi_b':256,'gpu_budget_mi_b':a.gpu_budget if a.gpu_budget is not None else (128 if a.scenario=='budget' else 768),'upload_budget_mi_b':8,'max_resident_meshes':256 if a.scenario=='budget' else 8192,'hiz_enabled':a.mode=='render'},indent=2)+'\n')
world=DEST/'saves/vulkan-qa'
if a.world or a.world_path:
 src=a.world_path.expanduser().resolve() if a.world_path else SOURCE/'saves'/a.world
 if not src.is_dir():raise FileNotFoundError(src)
 if world.exists():raise RuntimeError('QA world already exists; choose a separate QA instance or preserve it before replacing')
 shutil.copytree(src,world,ignore=shutil.ignore_patterns('session.lock','voxy','DistantHorizons.sqlite','*.sqlite-wal','*.sqlite-shm'))
elif not (world/'level.dat').exists():
 raise FileNotFoundError('Supply --world <installed-world-name> or --world-path <generated-world-directory> for the first QA run')
if a.scenario=='imports':
 fixture=DEST/'import-fixture'
 if not fixture.exists():shutil.copytree(world/'dimensions/minecraft/overworld/region',fixture)
base=SOURCE.parents[1]
loader=json.loads((SOURCE/'26.3-Fabric.json').read_text());parent=json.loads((SOURCE/'.parent/26.3.json').read_text())
cp=[]
for lib in loader['libraries']+parent['libraries']:
 if 'rules' in lib:
  allowed_rule=False
  for rule in lib['rules']:
   os=rule.get('os',{})
   if (not os.get('name') or os['name']=='osx') and (not os.get('arch') or os['arch'] in ['aarch64','arm64']):allowed_rule=rule['action']=='allow'
  if not allowed_rule:continue
 p=lib.get('downloads',{}).get('artifact',{}).get('path')
 if not p:
  g,n,v,*c=lib['name'].split(':');p=f'{g.replace(".","/")}/{n}/{v}/{n}-{v}'+''.join('-'+x for x in c)+'.jar'
 f=base/'libraries'/p
 if not f.exists():raise FileNotFoundError(f)
 if str(f) not in cp:cp.append(str(f))
cp.append(str(SOURCE/'26.3-Fabric.jar'))
args=['-XstartOnFirstThread','-Xms1G','-Xmx4G','-XX:StackShadowPages=32','--enable-native-access=ALL-UNNAMED','--add-exports','java.base/jdk.internal.misc=ALL-UNNAMED','-Dvoxy.qa.background=true','-Dvoxy.qa.backgroundFps='+str(a.cadence),'-Dvoxy.qa.width='+str(a.width),'-Dvoxy.qa.height='+str(a.height),'-Dvoxy.qa.seconds='+str(a.seconds),'-Dvoxy.qa.mode='+a.mode,'-Dvoxy.qa.scenario='+a.scenario,'-Djava.library.path='+str(SOURCE/'natives/lwjgl'),'-Dorg.lwjgl.system.SharedLibraryExtractPath='+str(SOURCE/'natives/lwjgl'),'-cp',':'.join(cp),loader['mainClass'],'--username','VulkanVoxyQA','--version','26.3-Fabric','--gameDir',str(DEST),'--assetsDir',str(base/'assets'),'--assetIndex',parent['assetIndex']['id'],'--uuid',uuid.uuid3(uuid.NAMESPACE_DNS,'VulkanVoxyQA').hex,'--accessToken','0','--versionType','release','--width',str(a.width),'--height',str(a.height),'--quickPlaySingleplayer','vulkan-qa']
args.insert(10,'-Dvoxy.qa.backend='+a.backend)
args.insert(10,'-Dvoxy.qa.camera='+' '.join(str(v) for v in a.camera))
for enabled,key in [(a.no_raster_cull,'noRasterCull'),(a.cull_probe,'cullProbe'),(a.no_subgroup,'noSubgroup'),(a.fixed_commands,'fixedCommands')]:
 if enabled:args.insert(10,'-Dvoxy.qa.'+key+'=true')
if a.no_import:args.insert(10,'-Dvoxy.qa.noImport=true')
if a.legacy_fog:args.insert(10,'-Dvoxy.qa.legacyFog=true')
if a.legacy_coverage:args.insert(10,'-Dvoxy.qa.legacyCoverage=true')
(DEST/'launch.args').write_text('\n'.join(json.dumps(s,ensure_ascii=False) for s in args)+'\n')
manifest={str(p.relative_to(SOURCE)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [SOURCE/'options.txt',SOURCE/'config/iris.properties'] if p.exists()}
(DEST/'source-config-sha.json').write_text(json.dumps(manifest,indent=2))
(DEST/'tested-artifact.json').write_text(json.dumps({'name':jar.name,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest()},indent=2))
(DEST/'test-spec.json').write_text(json.dumps({'mode':a.mode,'scenario':a.scenario,'backend':a.backend,'distance':a.distance,'seconds':a.seconds,'width':a.width,'height':a.height,'camera':a.camera,'legacy_fog':a.legacy_fog,'legacy_coverage':a.legacy_coverage,'subdivision':a.subdivision,'threads':a.threads,'gpu_budget_mib':a.gpu_budget if a.gpu_budget is not None else (128 if a.scenario=='budget' else 768),'auto_import':not a.no_import,'background':True,'work_cadence_fps':a.cadence,'dependencies':frozen},indent=2)+'\n')
print(DEST)
