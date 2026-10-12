#!/usr/bin/env python3
"""Close only this named disposable audit project, after evidence verification."""
import json
import os
from pathlib import Path
import subprocess

ROOT=Path('/home/juan/terra-audit-20260922')
PROJECT='terra-audit-20260922'
DOCKER=['/snap/docker/current/bin/docker','--host','unix:///var/run/docker.sock']

def call(args):
    return subprocess.check_output(args,text=True,timeout=120).strip()

def main():
    assert json.loads((ROOT/'final-verification.json').read_text())['status']=='PASS'
    prior=json.loads((ROOT/'inventory-final/application.cdx.json').read_text())
    final=json.loads((ROOT/'inventory-revision/application.cdx.json').read_text())
    assert {c['purl'] for c in prior['components']}=={c['purl'] for c in final['components']}
    # Check private run credentials have not leaked into deliverable text. Never print values.
    secrets=[line.split('=',1)[1] for line in (ROOT/'runtime.env').read_text().splitlines()
             if '=' in line and ('PASSWORD' in line or 'TOKEN' in line or 'SECRET' in line)]
    leaks=[]; checked=0
    skip={'.git','node_modules','target','dist','testkit','.mimosa','public','__pycache__'}
    for parent,dirs,files in os.walk(ROOT/'source'):
        dirs[:]=[d for d in dirs if d not in skip]
        for name in files:
            p=Path(parent)/name
            if p.stat().st_size>5_000_000:continue
            try:content=p.read_text(encoding='utf-8')
            except (UnicodeDecodeError,OSError):continue
            checked+=1
            if any(secret and secret in content for secret in secrets):leaks.append(str(p.relative_to(ROOT/'source')))
    assert not leaks,'private audit credentials found in: '+','.join(leaks)
    before=json.loads((ROOT/'live-before.json').read_text())
    def live():
        rows=json.loads(call(DOCKER+['inspect',*[c['name'] for c in before]]))
        return [{'name':c['Name'],'id':c['Id'],'started':c['State']['StartedAt'],'image':c['Image']} for c in rows]
    assert before==live(),'live state changed before teardown'
    ids=call(DOCKER+['ps','-aq','--filter','label=com.docker.compose.project='+PROJECT]).split()
    assert ids,'no owned test containers found; refuse ambiguous closeout'
    preview=json.loads(call(DOCKER+['inspect','tf-audit-preview']))[0]
    assert any(m['Source'].startswith(str(ROOT)+'/') for m in preview['Mounts'])
    subprocess.run(DOCKER+['rm','-f','tf-audit-preview'],check=True,timeout=60)
    subprocess.run(['docker','compose','--env-file',str(ROOT/'runtime.env'),'-p',PROJECT,
                    '-f',str(ROOT/'source/security-audit/compose.yml'),'down','--volumes','--remove-orphans'],
                   check=True,timeout=120,env={**os.environ,'AUDIT_CGROUP':PROJECT+'.slice','AUDIT_NO_NEW_PRIVILEGES':'false'})
    residual={kind:call(DOCKER+[kind,'ls','-q']+(['-a'] if kind=='container' else [])+['--filter','label=com.docker.compose.project='+PROJECT])
              for kind in ('container','volume','network')}
    assert not any(residual.values()),'owned resources remain'
    assert before==live(),'live state changed during teardown'
    subprocess.run(['systemctl','stop',PROJECT+'.slice'],check=True,timeout=30)
    unit=Path('/run/systemd/system')/(PROJECT+'.slice')
    assert 'Disposable Terra Food audit budget' in unit.read_text()
    unit.unlink()
    subprocess.run(['systemctl','daemon-reload'],check=True,timeout=30)
    report={'status':'PASS','liveDeploymentUnchanged':True,'ownedContainerVolumeNetworkResiduals':residual,
            'privateCredentialTextFilesChecked':checked,'applicationComponentsUnchanged':len(final['components']),
            'retained':'Private run logs, source and recovery drill artifacts remain under the owned run directory; shared build caches and live data untouched.'}
    (ROOT/'closeout-result.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report))

if __name__=='__main__':main()
