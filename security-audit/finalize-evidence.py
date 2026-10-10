#!/usr/bin/env python3
"""Read-only final evidence for this disposable run. Never examines container env."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import urllib.request

ROOT = Path('/home/juan/terra-audit-20260922')
DOCKER = ['/snap/docker/current/bin/docker', '--host', 'unix:///var/run/docker.sock']
REVISION = '7c161d1b1086bd85b554701f1cc7e09e3e3a4abe'

def run(*args):
    return subprocess.check_output(list(args), text=True, timeout=45).strip()

def inspect(names):
    return json.loads(run(*DOCKER, 'inspect', *names))

def identity(c):
    return {'name': c['Name'], 'id': c['Id'], 'started': c['State']['StartedAt'], 'image': c['Image']}

def main():
    before = json.loads((ROOT/'live-before.json').read_text())
    after = [identity(c) for c in inspect([c['name'] for c in before])]
    assert before == after, 'live deployment identities changed; investigate before cleanup'
    containers = inspect(['terra-audit-20260922-'+n+'-1' for n in
                          ('backend','web','agent-api','agent-mcp')])
    assert all(c['State']['Running'] for c in containers)
    assert all(c['Config']['Labels']['org.opencontainers.image.revision'] == REVISION for c in containers)
    assert all(c['Config']['User'] not in ('', 'root', '0', '0:0') for c in containers)
    deadline = time.monotonic()+90
    while True:
        try:
            with urllib.request.urlopen('http://127.0.0.1:18381/api/foods/catalog', timeout=5) as r:
                assert r.status == 200
            break
        except Exception:
            if time.monotonic() >= deadline: raise
            time.sleep(1)
    with urllib.request.urlopen('http://127.0.0.1:18390/health', timeout=5) as r:
        agent = json.load(r)
    assert REVISION in json.dumps(agent), 'Agent runtime revision is not the source commit'
    from urllib.error import HTTPError
    try:
        urllib.request.urlopen('http://127.0.0.1:18391/mcp', timeout=5)
        raise AssertionError('MCP anonymous request unexpectedly accepted')
    except HTTPError as e:
        assert e.code == 401
    cgroup = Path('/sys/fs/cgroup/terra.slice/terra-audit.slice/terra-audit-20260922.slice')
    budget = {name:(cgroup/name).read_text().strip() for name in
              ('memory.max','memory.swap.max','memory.current','memory.events')}
    assert budget['memory.max'] == '2147483648' and budget['memory.swap.max'] == '0'
    logs = {}
    for name in ('java-final.log','startup-final.log','upgrade.log','agent-locked-tests.log',
                 'ops-release-final.log','nginx-nonroot-first-failure.log','web-final-retry.log'):
        p=ROOT/name
        if not p.exists(): raise RuntimeError('missing evidence: '+name)
        raw=p.read_bytes(); lines=raw.decode(errors='replace').splitlines()
        logs[name]={'sha256':hashlib.sha256(raw).hexdigest(), 'summary':[
            re.sub(r'\x1b\[[0-9;]*m','',line) for line in lines
            if re.search(r'Tests run:|BUILD SUCCESS|Ran \d+ tests|^OK$|Permission denied',line)]}
    report={'status':'PASS','sourceRevision':REVISION,'liveDeploymentUnchanged':True,
            'artifacts':[dict(identity(c),user=c['Config']['User']) for c in containers],
            'budget':budget,'logs':logs,'limitations':[
                'One earlier MySQL OOM is retained in cumulative cgroup events; budget was not raised.',
                'Application OSV scan does not cover every operating system vulnerability.',
                'No live deployment, production data migration or network change performed.']}
    (ROOT/'final-verification.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({'status':'PASS','sourceRevision':REVISION,'liveDeploymentUnchanged':True}))

if __name__=='__main__': main()
