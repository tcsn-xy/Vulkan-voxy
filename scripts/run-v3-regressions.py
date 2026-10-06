#!/usr/bin/env python3
"""Sequential hidden regressions; no formal writes, no parallel GPU clients."""
from pathlib import Path
import subprocess,json,time
r=Path(__file__).resolve().parents[1]
cases=[
 ('v3-fixed-no-subgroup',90,'static',['--fixed-commands','--no-subgroup','--no-import']),
 ('v3-settings-full',90,'settings',['--instance-dir','build/qa-full-instance','--no-import']),
 ('v3-checkpoint-full',140,'imports',['--instance-dir','build/qa-full-instance']),
 ('v3-turns-cache32k',360,'dense',['--no-import']),
 ('v3-metal-final',100,'static',['--metal-validation','--no-import']),
]
results=[]
for name,seconds,scenario,extra in cases:
 args=['python3','scripts/capture-run.py','--name',name,'--seconds',str(seconds),'--scenario',scenario,'--distance','96','--subdivision','28','--threads','2','--camera','871','166','-186','-90','25',*extra]
 with (r/'build'/f'{name}.log').open('w') as log:code=subprocess.run(args,cwd=r,stdout=log,stderr=subprocess.STDOUT,timeout=seconds+300).returncode
 results.append({'name':name,'exit':code});(r/'build/v3-regressions.json').write_text(json.dumps(results,indent=2)+'\n');print(name,code,flush=True)
 if code:raise SystemExit(code)
