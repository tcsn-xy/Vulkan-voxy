#!/usr/bin/env python3
"""Back up and remove only Voxy after the formal Minecraft client has exited."""
from pathlib import Path
import argparse,hashlib,json,re,shlex,shutil,subprocess,time,zipfile
p=argparse.ArgumentParser();p.add_argument('--instance',type=Path,required=True);p.add_argument('--wait',action='store_true');a=p.parse_args();instance=a.instance.expanduser().resolve()
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
def game_running():
 for line in subprocess.check_output(['ps','-axo','pid=,comm='],text=True,errors='replace').splitlines():
  fields=line.strip().split(None,1)
  if len(fields)!=2 or not fields[1].endswith('/java'):continue
  command=subprocess.check_output(['ps','-p',fields[0],'-o','command='],text=True,errors='replace')
  m=re.search(r'--gameDir(?:\s+|=)(.*?)(?=\s--[A-Za-z]|$)',command)
  if m and Path(m.group(1).strip().strip('\"\'')).resolve()==instance:return True
  for tail in command.split('@')[1:]:
   words=tail.strip().split()
   for end in range(len(words),0,-1):
    path=Path(' '.join(words[:end]))
    if not path.is_file():continue
    text=path.read_text(errors='replace');args=shlex.split(text)
    if any(v=='--gameDir' and Path(args[i+1]).resolve()==instance for i,v in enumerate(args[:-1])):return True
    break
  result=subprocess.run(['lsof','-a','-p',fields[0],'-d','cwd','-Fn'],capture_output=True,text=True)
  if 'n'+str(instance) in result.stdout.splitlines():return True
 return False
while game_running():
 if not a.wait:raise SystemExit('The formal Minecraft client is still running')
 time.sleep(2)
mods=instance/'mods';old=[]
for path in mods.glob('*.jar'):
 with zipfile.ZipFile(path) as jar:
  if 'fabric.mod.json' in jar.namelist() and json.loads(jar.read('fabric.mod.json'),strict=False).get('id')=='voxy':old.append(path)
assert len(old)==1,'Exactly one Voxy JAR is required'
artifact=old[0]
def inventory():
 files={str(f.relative_to(instance)):sha(f) for folder in ['config','mods'] for f in (instance/folder).rglob('*') if f.is_file() and f!=artifact}
 if (instance/'options.txt').exists():files['options.txt']=sha(instance/'options.txt')
 saves={str(f.relative_to(instance)):[f.stat().st_size,f.stat().st_mtime_ns] for f in (instance/'saves').rglob('*') if f.is_file()}
 return {'sha256':files,'saves_size_mtime_ns':saves}
before=inventory();digest=sha(artifact);backup=instance/'.voxy-metal-backups'/('voxy-remove-v4-repair-'+time.strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True)
(backup/'before-inventory.json').write_text(json.dumps(before,ensure_ascii=False,indent=2)+'\n')
assert not game_running(),'Formal client restarted; no JAR removed'
shutil.move(artifact,backup/artifact.name)
try:assert inventory()==before,'Unrelated state changed during removal'
except BaseException:
 shutil.move(backup/artifact.name,artifact);raise
manifest={'removed':artifact.name,'sha256':digest,'preservation_check':True,'configs_and_caches_retained':True,'backup':str(backup)}
(backup/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
(backup/'ROLLBACK.txt').write_text('Close Minecraft. Copy '+artifact.name+' from this backup into mods/. No configuration or cache changes were made.\n')
root=Path(__file__).resolve().parents[1];(root/'build/v4-uninstallation.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');print(json.dumps(manifest,ensure_ascii=False))
