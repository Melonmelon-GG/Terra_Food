#!/usr/bin/env python3
"""Read-only evidence. Never renews DHCP, changes routes, restarts networking or reveals Wi-Fi secrets."""
import json
import subprocess
import time
from pathlib import Path

def probe(arguments):
    try:
        result=subprocess.run(arguments,capture_output=True,text=True,timeout=10)
        return {"exit":result.returncode,"output":result.stdout[:12000]}
    except (OSError,subprocess.TimeoutExpired) as error:
        return {"error":type(error).__name__}

def main():
    report={"time":time.time(),"read_only":True,"commands":{}}
    commands=[['ip','-j','address','show'],['ip','-j','-N','-4','route','show','table','all'],
              ['ip','-j','rule','show'],['ip','-j','neigh','show'],
              ['nmcli','-f','GENERAL.DEVICE,GENERAL.STATE,IP4.ADDRESS,IP4.GATEWAY,IP4.ROUTE','device','show'],
              ['systemctl','is-active','cloudflared.service','mini-link-failover.service'],
              ['resolvectl','query','terrafood.liujuan.xyz']]
    for command in commands: report['commands'][' '.join(command)]=probe(command)
    report['links']={}
    for device in ('enp2s0','enp1s0','wlp3s0'):
        values={}
        for attribute in ('carrier','speed','duplex','operstate'):
            try: values[attribute]=Path('/sys/class/net',device,attribute).read_text().strip()
            except OSError: values[attribute]='unavailable'
        values['route']=probe(['ip','-j','route','get','223.5.5.5','oif',device])
        report['links'][device]=values
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__': main()
