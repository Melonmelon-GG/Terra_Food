#!/usr/bin/python3
"""Bounded, read-only probes and serialized recovery for Terra Food."""
import concurrent.futures
import datetime
import fcntl
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
import urllib.request

ROOT = Path(os.environ.get('TERRA_GUARDIAN_STATE', '/var/lib/terrafood-guardian'))
NAMES = ['mysql', 'redis', 'backend', 'web', 'agent-mcp', 'agent-api']
CATALOG = '/api/foods/catalog?page=1&pageSize=1'


def command(args, timeout=12):
    try:
        if args[0] == 'docker':
            # Snap's privileged launcher is incompatible with NoNewPrivileges.
            # Use its existing CLI directly against the local daemon socket.
            args = ['/snap/docker/current/bin/docker', '--host', 'unix:///var/run/docker.sock'] + args[1:]
        r = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
        return r.returncode, r.stdout
    except (OSError, subprocess.TimeoutExpired):
        return 124, ''


def http(url, kind='html'):
    started = time.monotonic()
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'TerraFood-Guardian/1.0'})
        with urllib.request.urlopen(req, timeout=8) as r:
            body = r.read(262145)
            ok = r.status == 200 and len(body) <= 262144
            if kind == 'catalog':
                j = json.loads(body)
                ok = ok and isinstance(j.get('items'), list) and isinstance(j.get('total'), int)
            elif kind == 'ready':
                ok = ok and json.loads(body).get('readyConnections', 0) > 0
            elif kind == 'agent':
                ok = ok and json.loads(body).get('status') == 'ok'
            else:
                ok = ok and b'<html' in body.lower()
            return {'ok': ok, 'status': r.status, 'seconds': round(time.monotonic()-started, 3)}
    except Exception as exc:
        return {'ok': False, 'error': type(exc).__name__}


def collect():
    code, raw = command(['docker', 'inspect'] + ['terrafood-'+n for n in NAMES])
    try:
        containers = {x['Name'].removeprefix('/terrafood-'): x for x in json.loads(raw)}
    except (ValueError, TypeError):
        containers = {}
    states = {}
    for name in NAMES:
        x = containers.get(name, {}).get('State', {})
        try:
            age = time.time() - datetime.datetime.fromisoformat(x['StartedAt'].replace('Z', '+00:00')).timestamp()
        except (KeyError, ValueError):
            age = 999999
        states[name] = {'exists': name in containers, 'running': bool(x.get('Running')),
                        'instance': containers.get(name, {}).get('Id', '')+':'+x.get('StartedAt',''),
                        'age': age, 'health': x.get('Health', {}).get('Status', 'none')}

    def ip(name):
        return next((v['IPAddress'] for v in containers.get(name, {}).get('NetworkSettings', {}).get('Networks', {}).values()
                     if v.get('IPAddress')), '')

    def db():
        rc, out = command(['docker', 'exec', 'terrafood-mysql', 'sh', '-c',
            'MYSQL_PWD="$MYSQL_PASSWORD" mysql --connect-timeout=4 -h 127.0.0.1 -u "$MYSQL_USER" -Nse "SELECT 1" "$MYSQL_DATABASE"'])
        return {'ok': rc == 0 and out.strip() == '1'}

    def redis():
        rc, out = command(['docker', 'exec', 'terrafood-redis', 'sh', '-c',
            'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --no-auth-warning ping'])
        return {'ok': rc == 0 and out.strip() == 'PONG'}

    def tcp(host, port):
        try:
            with socket.create_connection((host, port), timeout=4):
                return {'ok': True}
        except OSError:
            return {'ok': False}

    jobs = {
        'mysql': db, 'redis': redis,
        'backend': lambda: http('http://'+ip('backend')+':8080'+CATALOG, 'catalog'),
        'web': lambda: http('http://127.0.0.1:8180/'),
        'proxy_api': lambda: http('http://127.0.0.1:8180'+CATALOG, 'catalog'),
        'public': lambda: http('https://terrafood.liujuan.xyz/'),
        'public_api': lambda: http('https://terrafood.liujuan.xyz'+CATALOG, 'catalog'),
        'tunnel': lambda: http('http://127.0.0.1:20241/ready', 'ready'),
        'agent-api': lambda: http('http://'+ip('agent-api')+':8090/health', 'agent'),
        'agent-mcp': lambda: tcp(ip('agent-mcp'), 8091),
    }
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        results = dict(zip(jobs, pool.map(lambda fn: fn(), jobs.values())))
    disk = shutil.disk_usage('/var/snap/docker')
    fs = os.statvfs('/var/snap/docker')
    results['storage'] = {'ok': disk.free > 2*1024**3 and disk.free/disk.total > .05
                         and fs.f_favail/max(fs.f_files, 1) > .05,
                         'freeGiB': round(disk.free/1024**3, 2)}
    daemon_code, _ = command(['docker', 'info', '--format', '{{.ServerVersion}}'])
    results['docker'] = {'ok': daemon_code == 0, 'inspect_partial': code != 0}
    return {'checks': results, 'containers': states}


def decide(snapshot, state, now):
    checks, containers = snapshot['checks'], snapshot['containers']
    if now-state.get('last_sample', now) > 120:
        state['strikes'] = {}
    state['last_sample'] = now
    strikes = state.setdefault('strikes', {})
    instances = state.setdefault('instances', {})
    blocked = snapshot['blocked'] = []
    for name, container in containers.items():
        instance = container.get('instance', '')
        if instances.get(name, instance) != instance or container.get('age', 0) < 180:
            strikes[name] = 0
            if name == 'web': strikes['proxy_api'] = 0
        instances[name] = instance
    for key, val in checks.items():
        grace = key in containers and containers[key].get('age', 0) < 180
        strikes[key] = 0 if val['ok'] or grace else min(strikes.get(key, 0)+1, 100)
    history = state['actions'] = [x for x in state.get('actions', []) if now-x['time'] < 3600]

    def allowed(target, verb):
        own = [x for x in history if x['target'] == target]
        cooldown = 900 if target == 'tunnel' else 600
        if len(history) >= 4 or len(own) >= 2 or (own and now-own[-1]['time'] < cooldown):
            blocked.append({'target': target, 'reason': 'recovery_budget_or_cooldown'})
            return None
        return {'target': target, 'verb': verb}

    # The shared tunnel has an independent failure domain; a broken database must
    # not suppress recovery for every other site served by this tunnel.
    if (strikes.get('tunnel', 0) >= 3 and not checks['public']['ok'] and not checks['public_api']['ok']):
        action=allowed('tunnel', 'restart')
        if action: return action
    if not checks['storage']['ok'] or not checks['docker']['ok']:
        blocked.append({'target': 'containers', 'reason': 'storage_or_daemon_unhealthy'})
        return None
    for name in NAMES:
        c = containers[name]
        if not c['exists']:
            blocked.append({'target': name, 'reason': 'missing_container_requires_release'})
            continue  # Never recreate missing containers or guess volumes/config.
        if c['running'] and c['age'] < 180:
            continue
        deps = {'backend': ['mysql', 'redis'], 'web': ['backend'],
                'agent-api': ['agent-mcp']}.get(name, [])
        if any(not checks[d]['ok'] for d in deps):
            blocked.append({'target': name, 'reason': 'dependency_unhealthy'})
            continue
        failed = strikes.get(name, 0) >= 3
        if name == 'web':
            failed = failed or (checks['backend']['ok'] and strikes.get('proxy_api', 0) >= 3)
        if failed:
            if not c['running']:
                action = allowed(name, 'start')
            elif name in ['mysql', 'redis']:
                blocked.append({'target': name, 'reason': 'running_data_service_requires_operator'})
                action = None  # Do not blindly restart a live data service.
            else:
                action = allowed(name, 'restart')
            if action:
                return action
    return None


def save(path, value):
    temp = path.with_suffix('.tmp')
    temp.write_text(json.dumps(value, ensure_ascii=False, indent=2))
    temp.chmod(0o600)
    temp.replace(path)


def selftest():
    import copy
    base = {'checks': {k: {'ok': True} for k in NAMES+['storage','docker','proxy_api','public','public_api','tunnel']},
            'containers': {k: {'exists': True, 'running': True, 'age': 500} for k in NAMES}}
    def scenario(bad, changes=None, history=None):
        s = copy.deepcopy(base)
        for k in bad: s['checks'][k]['ok'] = False
        for k, v in (changes or {}).items(): s['containers'][k].update(v)
        st = {'strikes': {k: 2 for k in bad}, 'actions': history or []}
        return decide(s, st, 10000)
    assert scenario([]) is None
    assert scenario(['public', 'public_api']) is None
    assert scenario(['mysql','backend','proxy_api']) is None
    assert scenario(['mysql'], {'mysql': {'running': False}}) == {'target':'mysql','verb':'start'}
    assert scenario(['backend','proxy_api']) == {'target':'backend','verb':'restart'}
    assert scenario(['proxy_api']) == {'target':'web','verb':'restart'}
    assert scenario(['backend'], {'backend': {'age': 30}}) is None
    assert scenario(['backend'], {'backend': {'exists': False}}) is None
    assert scenario(['storage','backend']) is None
    assert scenario(['tunnel']) is None
    assert scenario(['tunnel','public','public_api']) == {'target':'tunnel','verb':'restart'}
    assert scenario(['backend'], history=[{'target':'backend','time':9900}]) is None
    assert scenario(['backend'], history=[{'target':'backend','time':8000},{'target':'backend','time':9000}]) is None
    st={}; s=copy.deepcopy(base);s['checks']['backend']['ok']=False
    assert decide(s,st,10000) is None
    assert decide(s,st,10030) is None
    assert decide(s,st,10060) == {'target':'backend','verb':'restart'}
    assert scenario(['mysql','backend','proxy_api','tunnel','public','public_api']) == {'target':'tunnel','verb':'restart'}
    assert scenario(['storage','docker','tunnel','public','public_api']) == {'target':'tunnel','verb':'restart'}
    stale={'strikes':{'backend':3},'last_sample':9000}
    failed=copy.deepcopy(base);failed['checks']['backend']['ok']=False
    assert decide(failed,stale,10000) is None
    state={'strikes':{'backend':3},'instances':{'backend':'old'}}
    failed['containers']['backend']['instance']='new'
    assert decide(failed,state,10000) is None
    missing=copy.deepcopy(base);missing['containers']['agent-mcp']['exists']=False
    missing['checks']['backend']['ok']=False
    assert decide(missing,{'strikes':{'backend':2}},10000)=={'target':'backend','verb':'restart'}
    print('21 policy assertions passed; no real recovery commands executed')


def valid_state(state):
    if not isinstance(state, dict): return False
    if not isinstance(state.get('strikes', {}), dict) or not isinstance(state.get('instances', {}), dict): return False
    if any(not isinstance(v, int) or isinstance(v, bool) or v < 0 for v in state.get('strikes', {}).values()): return False
    if not isinstance(state.get('last_sample', 0), (float,int)): return False
    actions=state.get('actions', [])
    return isinstance(actions,list) and all(isinstance(a,dict) and isinstance(a.get('time'),(float,int))
        and a.get('target') in NAMES+['tunnel'] for a in actions)


def main():
    if '--self-test' in sys.argv:
        selftest();return 0
    ROOT.mkdir(mode=0o700, exist_ok=True)
    with open('/run/lock/terrafood-maintenance.lock', 'w') as lock:
        try: fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            save(ROOT/'status.json', {'time':time.time(),'status':'maintenance','reason':'maintenance_lock','expires_at':time.time()+120})
            print('Skipped: deployment/recovery owns maintenance lock');return 0
        if Path('/etc/terrafood-guardian.pause').exists():
            save(ROOT/'status.json', {'time':time.time(), 'status':'maintenance', 'reason':'explicit_pause','expires_at':time.time()+120})
            print('Paused explicitly');return 0
        try: state=json.loads((ROOT/'state.json').read_text())
        except FileNotFoundError: state={}
        except (ValueError, OSError):
            save(ROOT/'status.json', {'time':time.time(),'status':'unknown','reason':'invalid_state_preserving_recovery_budget','expires_at':time.time()+120})
            return 2
        if not valid_state(state):
            save(ROOT/'status.json', {'time':time.time(),'status':'unknown','reason':'invalid_state_preserving_recovery_budget','expires_at':time.time()+120})
            return 2
        # Corrupt state deliberately fails closed, preserving restart budgets.
        try: snapshot=collect()
        except Exception as error:
            save(ROOT/'status.json', {'time':time.time(),'status':'unknown','reason':'probe_exception','category':type(error).__name__,'expires_at':time.time()+120})
            return 2
        now=time.time()
        try: action=decide(snapshot,state,now)
        except Exception as error:
            save(ROOT/'status.json', {'time':now,'status':'unknown','reason':'decision_exception','category':type(error).__name__,'expires_at':now+120})
            return 2
        if action and '--check-only' not in sys.argv:
            state['actions'].append(dict(action,time=now))
            save(ROOT/'state.json',state)  # Persist attempt before executing.
            args=['systemctl','restart','cloudflared.service'] if action['target']=='tunnel' else ['docker',action['verb'],'--time','20','terrafood-'+action['target']] if action['verb']=='restart' else ['docker','start','terrafood-'+action['target']]
            rc,_=command(args,60)
            snapshot['action']=dict(action,exit=rc)
            print('Recovery attempt: '+json.dumps(snapshot['action']),flush=True)
        snapshot.update(time=now, expires_at=now+120, status='healthy' if all(x['ok'] for x in snapshot['checks'].values()) else 'degraded', proposed_action=action)
        if '--check-only' not in sys.argv:
            save(ROOT/'state.json',state);save(ROOT/'status.json',snapshot)
        print(json.dumps(snapshot,ensure_ascii=False),flush=True)
        return 0 if snapshot['status']=='healthy' else 1


if __name__ == '__main__':
    sys.exit(main())
