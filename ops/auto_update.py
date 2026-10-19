#!/usr/bin/env python3
"""Follow main without confusing checkout, prepared artifacts and deployed artifacts.

The first deployment and changes to Flyway migrations use the reviewed manual
release path. Subsequent application-only updates can run unattended. No data
service is rebuilt or restarted by this program.
"""
import argparse
import fcntl
import json
import ipaddress
import os
from pathlib import Path
import signal
import subprocess
import time
from types import SimpleNamespace

import release


class UpdateError(RuntimeError):
    pass


def read(path):
    return json.loads(path.read_text()) if path.exists() else None


def git(config, *args):
    return release.capture(['git', '-c', 'safe.directory='+str(config['repo']), '-C', str(config['repo']), *args])


def settings(config, manifest):
    return SimpleNamespace(manifest=manifest, compose=Path(config['compose']), env_file=Path(config['env_file']),
        project=config['project'], health_url=config['health_url'], deployed=Path(config['state'])/'deployed.json', execute=True)


def transition(config, phase, target=None, code=None):
    state = {'time':time.time(), 'phase':phase, 'target_revision':target}
    if code: state['code'] = code
    release.save(Path(config['state'])/'status.json', state)
    # No subprocess output, interpolated configuration, or exception text here.
    print(json.dumps(state), flush=True)


def synchronize(config):
    branch = git(config, 'symbolic-ref', '--short', 'HEAD')
    if branch != config.get('branch','main'): raise UpdateError('UnexpectedCheckoutBranch')
    if git(config, 'status','--porcelain','--untracked-files=no'): raise UpdateError('TrackedChangesPresent')
    for attempt in range(3):
        try:
            # Retry an alternate transport on the same official remote when the
            # host's IPv6/HTTP2 path stalls; do not change routes or use a mirror.
            transport=['-c','http.version=HTTP/1.1'] if attempt else []
            family=['--ipv4'] if attempt else []
            release.run(['git','-c','safe.directory='+str(config['repo']),*transport,'-C',str(config['repo']),
                         'fetch',*family,'--quiet',config.get('remote','origin'),
                         'refs/heads/'+config.get('branch','main')+':refs/remotes/'+config.get('remote','origin')+'/'+config.get('branch','main')],
                        timeout=60, capture_output=True)
            break
        except (subprocess.CalledProcessError,subprocess.TimeoutExpired):
            if attempt == 2: raise UpdateError('FetchFailed')
            time.sleep(2)
    # Do not substitute local HEAD when the upstream ref is missing.
    revision = git(config,'rev-parse',config.get('remote','origin')+'/'+config.get('branch','main')+'^{commit}')
    release.run(['git','-c','safe.directory='+str(config['repo']),'-C',str(config['repo']),
                 'merge','--ff-only','--quiet',revision], timeout=60,capture_output=True)
    return revision


def migration_changes(config, deployed, target):
    return bool(git(config,'diff','--name-only',deployed,target,'--',
                    'dayanfood-backend/src/main/resources/db/migration'))


def refresh_proxy(config, target):
    if not config.get('proxy_gateway'): return
    gateway=str(ipaddress.ip_address(config['proxy_gateway']))
    text=git(config,'show',target+':performance/nginx.conf')
    if text.count('    listen 80;') != 1 or 'real_ip_header' in text:
        raise UpdateError('ProxyTemplateReviewRequired')
    text=text.replace('    listen 80;','    set_real_ip_from '+gateway+';\n    real_ip_header CF-Connecting-IP;\n    real_ip_recursive on;\n    listen 80;')
    path=Path(config['proxy_config'])
    temporary=path.with_suffix('.update.tmp')
    temporary.write_text(text+'\n');temporary.chmod(0o644);temporary.replace(path)


def inspect(config):
    """Read-only inspection: no fetch, checkout, build, write, or service changes."""
    state = Path(config['state'])
    deployed = read(state/'deployed.json')
    result = {'checkout':git(config,'rev-parse','HEAD'), 'deployed_revision':deployed.get('revision') if deployed else None}
    try: release.validate_runtime(Path(config['env_file']))
    except (OSError,ValueError): result['runtime_ready'] = False
    else: result['runtime_ready'] = True
    result['verified'] = False
    if deployed:
        try:
            release.validate_manifest(deployed)
            release.verify_running(settings(config,state/'deployed.json'),deployed,state/'deployed.compose.json')
            result['verified'] = True
        except Exception: pass
    print(json.dumps(result),flush=True)
    return 0 if result['verified'] and result['runtime_ready'] else 1


def update(config):
    root = Path(config['state'])
    root.mkdir(parents=True,exist_ok=True,mode=0o700)
    if root.stat().st_mode & 0o077: raise UpdateError('StateDirectoryNotPrivate')
    with open(config['lock'],'a') as lock:
        try: fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError: raise UpdateError('AnotherUpdateRunning')
        target = None
        try:
            transition(config,'fetching')
            target = synchronize(config)
            transition(config,'checkout-synchronized',target)
            try: release.validate_runtime(Path(config['env_file']))
            except (OSError,ValueError): raise UpdateError('RuntimeCredentialsNotReady')
            deployed = read(root/'deployed.json')
            if not deployed:
                # Unknown old deployments may need ownership/data and credential
                # migrations. Never guess a deployed revision from checkout HEAD.
                raise UpdateError('InitialReviewedDeploymentRequired')
            if target != deployed['revision'] and migration_changes(config,deployed['revision'],target):
                raise UpdateError('MigrationReviewRequired')
            if target == deployed['revision']:
                try:
                    release.validate_manifest(deployed)
                    release.verify_running(settings(config,root/'deployed.json'),deployed,root/'deployed.compose.json')
                except Exception:
                    # A matching Git revision with missing/wrong containers is
                    # still pending, including after a failed previous apply.
                    pass
                else:
                    transition(config,'verified-current',target)
                    return 0
            prepared_path = root/'prepared.json'
            prepared = read(prepared_path)
            reusable = bool(prepared and prepared.get('revision') == target and prepared.get('web_entry_sha256'))
            if reusable:
                try: release.validate_manifest(prepared)
                except Exception: reusable = False
            if not reusable:
                transition(config,'building',target)
                # Build in a new process group so signals do not orphan our CLI.
                release.run(['python3',str(Path(__file__).with_name('release.py')),'prepare',
                             '--repo',str(config['repo']),'--revision',target,'--manifest',str(prepared_path)],timeout=6000)
                prepared = read(prepared_path)
                if not prepared or prepared.get('revision') != target or not prepared.get('web_entry_sha256'):
                    raise UpdateError('PreparedManifestMissingOrWrong')
            transition(config,'applying',target)
            refresh_proxy(config,target)
            release.apply(settings(config,prepared_path))
            recorded = read(root/'deployed.json')
            if not recorded or recorded.get('revision') != target: raise UpdateError('DeployedManifestMissing')
            transition(config,'deployed',target)
            return 0
        except BaseException as error:
            code = str(error) if isinstance(error,UpdateError) else type(error).__name__
            transition(config,'failed',target,code)
            raise


def interrupted(signum,frame):
    raise UpdateError('Interrupted')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',type=Path,required=True)
    parser.add_argument('command',choices=['check','run'])
    args=parser.parse_args()
    config=json.loads(args.config.read_text())
    if config.get('build_root'): os.environ['TERRA_RELEASE_BUILD_ROOT']=config['build_root']
    if config.get('pip_index_url'): os.environ['TERRA_PIP_INDEX_URL']=config['pip_index_url']
    signal.signal(signal.SIGTERM,interrupted)
    signal.signal(signal.SIGINT,interrupted)
    os.umask(0o077)
    if args.command=='check': return inspect(config)
    return update(config)


if __name__=='__main__':
    try: raise SystemExit(main())
    except Exception as error:
        print('update-failed '+(str(error) if isinstance(error,UpdateError) else type(error).__name__),flush=True)
        raise SystemExit(1)
