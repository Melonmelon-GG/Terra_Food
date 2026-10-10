#!/usr/bin/env python3
"""Create only the named audit environment; invoke manually on the mini-host as root."""
import json
import secrets
import socket
import subprocess
from pathlib import Path

ROOT=Path('/home/juan/terra-audit-20260922')
SLICE='terra-audit-20260922.slice'

def main():
    ROOT.mkdir(exist_ok=True)
    env=ROOT/'runtime.env'
    if not env.exists():
        for port in (18306,18379,18325,18380,18381,18390,18391):
            with socket.socket() as probe: probe.bind(('127.0.0.1',port))
        values={key:secrets.token_urlsafe(36) for key in ('AUDIT_DB_PASSWORD','AUDIT_ADMIN_PASSWORD','AGENT_INTERNAL_TOKEN','MCP_INTERNAL_TOKEN','MCP_BACKEND_TOKEN','AGENT_CONTEXT_SECRET')}
        values['AUDIT_CGROUP']=SLICE
        env.write_text(''.join(f'{key}={value}\n' for key,value in values.items()));env.chmod(0o600)
        containers=json.loads(subprocess.check_output(['docker','inspect',*[f'terrafood-{name}' for name in ('backend','web','mysql','redis','agent-api','agent-mcp')]],text=True))
        (ROOT/'live-before.json').write_text(json.dumps([{'name':c['Name'],'id':c['Id'],'started':c['State']['StartedAt'],'image':c['Image']} for c in containers],indent=2))
    unit=Path('/run/systemd/system')/SLICE
    unit.write_text('[Unit]\nDescription=Disposable Terra Food audit budget\n[Slice]\nMemoryMax=2G\nMemorySwapMax=0\nTasksMax=768\n')
    subprocess.run(['systemctl','daemon-reload'],check=True)
    subprocess.run(['systemctl','start',SLICE],check=True)
    print(json.dumps({'project':'terra-audit-20260922','group':SLICE,'budget_bytes':2147483648,'live_snapshot':'live-before.json'}))

if __name__=='__main__':main()
