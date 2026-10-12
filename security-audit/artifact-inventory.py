#!/usr/bin/env python3
"""Mini-host artifact inventory. Never prints container environment or credentials.

Outputs CycloneDX application components and separate OS package inventories.
Unknown Java coordinates remain explicitly marked, never silently omitted.
"""
import argparse
import io
import json
from pathlib import Path
import subprocess
import tempfile
import urllib.parse
import uuid
import zipfile
import hashlib

DOCKER = ['/snap/docker/current/bin/docker', '--host', 'unix:///var/run/docker.sock'] if Path('/snap/docker/current/bin/docker').exists() else ['docker']

def run(args):
    return subprocess.check_output(args, text=True, timeout=60).strip()

def component(ecosystem, name, version, **extra):
    return {'type':'library','name':name,'version':version,
        'purl':'pkg:'+ecosystem+'/'+urllib.parse.quote(name,safe='/@')+'@'+urllib.parse.quote(version,safe=''), **extra}

def java_components(path, repository=None):
    entries=[]; unknown=[]
    candidates={}
    if repository:
        for file in repository.rglob('*.jar'):
            candidates.setdefault(file.name,[]).append(file)
    with zipfile.ZipFile(path) as boot:
        for entry in boot.namelist():
            if not entry.startswith('BOOT-INF/lib/') or not entry.endswith('.jar'):continue
            content=boot.read(entry)
            with zipfile.ZipFile(io.BytesIO(content)) as jar:
                found=False
                for name in jar.namelist():
                    if not name.startswith('META-INF/maven/') or not name.endswith('/pom.properties'):continue
                    props=dict(line.split('=',1) for line in jar.read(name).decode('utf-8').splitlines() if '=' in line and not line.startswith('#'))
                    if all(key in props for key in ('groupId','artifactId','version')):
                        entries.append(component('maven',props['groupId']+'/'+props['artifactId'],props['version']))
                        found=True
                if not found and repository:
                    for candidate in candidates.get(Path(entry).name,[]):
                        # Exact binary match, not filename/version guessing.
                        if hashlib.sha256(candidate.read_bytes()).digest()!=hashlib.sha256(content).digest():continue
                        parts=candidate.relative_to(repository).parts
                        if len(parts)<4:continue
                        group='.'.join(parts[:-3]);artifact=parts[-3];version=parts[-2]
                        entries.append(component('maven',group+'/'+artifact,version));found=True;break
                if not found:unknown.append(entry)
    return entries,unknown

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--backend-container',required=True)
    parser.add_argument('--agent-container',required=True)
    parser.add_argument('--web-lock',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--maven-repository',type=Path)
    args=parser.parse_args(); components=[]
    with tempfile.TemporaryDirectory() as temporary:
        jar=Path(temporary)/'app.jar'
        subprocess.run(DOCKER+['cp',args.backend_container+':/app/app.jar',str(jar)],check=True,timeout=60)
        java,unknown=java_components(jar,args.maven_repository);components+=java
    packages=json.loads(run(DOCKER+['exec',args.agent_container,'python','-c',
        'import importlib.metadata as m,json;print(json.dumps([[d.metadata["Name"],d.version] for d in m.distributions()]))']))
    components += [component('pypi',name.lower().replace('_','-'),version) for name,version in packages]
    lock=json.loads(args.web_lock.read_text())
    for key,package in lock.get('packages',{}).items():
        if 'node_modules/' not in key or not package.get('version'):continue
        name=package.get('name') or key.rsplit('node_modules/',1)[1]
        components.append(component('npm',name,package['version']))
    components=list({entry['purl']:entry for entry in components}.values())
    result={'bomFormat':'CycloneDX','specVersion':'1.5','serialNumber':'urn:uuid:'+str(uuid.uuid4()),'version':1,'components':components}
    args.output.mkdir(parents=True,exist_ok=True)
    (args.output/'application.cdx.json').write_text(json.dumps(result,indent=2)+'\n')
    metadata={'unresolved_java_entries':unknown,'npm_scope':'lockfile includes build and development dependencies','artifacts':{}}
    for container in (args.backend_container,args.agent_container):
        identity=run(DOCKER+['inspect',container,'--format','{{.Image}}'])
        packages=run(DOCKER+['exec',container,'dpkg-query','-W','-f=${Package}\t${Version}\n'])
        (args.output/(container+'-os.tsv')).write_text(packages+'\n')
        metadata['artifacts'][container]=identity
    (args.output/'inventory-metadata.json').write_text(json.dumps(metadata,indent=2)+'\n')
    print(json.dumps({'components':len(components),'unresolved_java_entries':len(unknown)}))

if __name__=='__main__':main()
