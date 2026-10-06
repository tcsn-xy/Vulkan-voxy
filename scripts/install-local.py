#!/usr/bin/env python3
"""Install the verified local artifact, preserving all existing configuration and saves."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,time,zipfile,shlex
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--instance',type=Path,required=True);p.add_argument('--artifact',type=Path,required=True);a=p.parse_args()
instance=a.instance.resolve();artifact=a.artifact.resolve()
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
# Read commands internally only; launcher commands can contain account credentials.
def assert_game_closed():
 processes=subprocess.check_output(['ps','-axo','pid=,comm='],text=True,errors='replace')
 for line in processes.splitlines():
  fields=line.strip().split(None,1)
  if len(fields)!=2 or not (fields[1].endswith('/java') or fields[1]=='java'):continue
  command=subprocess.check_output(['ps','-p',fields[0],'-o','command='],text=True,errors='replace')
  # macOS ps omits argument quoting. Recover existing @file paths with spaces,
  # then inspect the actual gameDir, rather than mistaking a nested QA for the formal game.
  args=shlex.split(command)
  for tail in command.split('@')[1:]:
   words=tail.strip().split()
   for end in range(len(words),0,-1):
    candidate=Path(' '.join(words[:end]))
    if candidate.is_file():
     args.extend(shlex.split(candidate.read_text(errors='replace')));break
  games=[args[i+1] for i,value in enumerate(args[:-1]) if value=='--gameDir']
  games.extend(value.split('=',1)[1] for value in args if value.startswith('--gameDir='))
  if any(Path(game).resolve()==instance for game in games):
   raise RuntimeError('Close the target Minecraft instance before installation')

assert_game_closed()
with zipfile.ZipFile(artifact) as jar:
 if jar.testzip():raise RuntimeError('Artifact ZIP CRC failed')
 mod=json.loads(jar.read('fabric.mod.json'));assert mod['id']=='voxy'
 assert 'vulkan.3' in mod['version']
digest=sha(artifact)
for name in ['release-metal','release-features-128budget','release-fixed-no-subgroup','release-checkpoint','v3-pressure-settings-fixed','release-latest-mods']:
 folder=ROOT/'build/evidence'/name
 assert json.loads((folder/'exit.json').read_text())['exit_code']==0,name
 assert json.loads((folder/'tested-artifact.json').read_text())['sha256']==digest,name
scope=json.loads((ROOT/'build/v3-user-validation-scope.json').read_text());assert scope['performance_comparison'].startswith('cancelled by user')
config=instance/'config/voxy-config.json';assert json.loads(config.read_text())['enabled'] is False
mods=instance/'mods'
old=[]
for jarpath in mods.glob('*.jar'):
 with zipfile.ZipFile(jarpath) as jar:
  if 'fabric.mod.json' in jar.namelist() and json.loads(jar.read('fabric.mod.json'),strict=False)['id']=='voxy':old.append(jarpath)
assert len(old)==1,'Exactly one installed Voxy is required'
def inventory():
 hashed={}
 for folder in ['config','mods']:
  for f in (instance/folder).rglob('*'):
   if f.is_file() and f not in old and not (folder=='mods' and f.name==artifact.name):hashed[str(f.relative_to(instance))]=sha(f)
 if (instance/'options.txt').exists():hashed['options.txt']=sha(instance/'options.txt')
 saves={str(f.relative_to(instance)):[f.stat().st_size,f.stat().st_mtime_ns] for f in (instance/'saves').rglob('*') if f.is_file()}
 return {'sha256':hashed,'saves_size_mtime_ns':saves}
before=inventory();stamp=time.strftime('%Y%m%d-%H%M%S');backup=instance/'.voxy-metal-backups'/('voxy-v3-fix-'+stamp);backup.mkdir(parents=True)
(backup/'before-inventory.json').write_text(json.dumps(before,ensure_ascii=False,indent=2)+'\n')
shutil.copytree(instance/'config',backup/'config');shutil.copy2(instance/'options.txt',backup/'options.before.txt')
shutil.copy2(old[0],backup/old[0].name)
temp=mods/('.'+artifact.name+'.pending');shutil.copy2(artifact,temp);assert sha(temp)==digest
assert_game_closed()
try:
 old[0].unlink();os.replace(temp,mods/artifact.name)
 after=inventory()
 if after!=before:
  changes={kind:[key for key in sorted(set(before[kind])|set(after[kind])) if before[kind].get(key)!=after[kind].get(key)] for kind in before}
  (backup/'preservation-failure.json').write_text(json.dumps(changes,ensure_ascii=False,indent=2)+'\n')
  raise RuntimeError('Unrelated state changed during installation: '+json.dumps(changes,ensure_ascii=False))
 assert json.loads(config.read_text())['enabled'] is False
except BaseException:
 (mods/artifact.name).unlink(missing_ok=True);shutil.copy2(backup/old[0].name,old[0]);raise
manifest={'artifact':artifact.name,'sha256':digest,'old_artifact':old[0].name,'old_sha256':sha(backup/old[0].name),'enabled':False,'preserved':before}
(backup/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
(backup/'ROLLBACK.txt').write_text('Close Minecraft. Remove mods/'+artifact.name+' and copy '+old[0].name+' from this backup into mods/. Configuration and saves were not changed.\n')
(ROOT/'build/v3-installation.json').write_text(json.dumps({'backup':str(backup),'artifact':artifact.name,'sha256':digest,'enabled':False,'preservation_check':True},indent=2)+'\n')
print(json.dumps({'installed':artifact.name,'sha256':digest,'enabled':False,'backup':str(backup)},ensure_ascii=False))
