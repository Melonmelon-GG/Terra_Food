#!/usr/bin/env python3
"""Run outside the mini-host; emits machine-readable evidence, never sends messages."""
import argparse
import json
import time
import urllib.request

def check(base):
    samples=[]
    for path in ('/','/api/foods/catalog?page=1&pageSize=1'):
        start=time.monotonic()
        try:
            with urllib.request.urlopen(base.rstrip('/')+path,timeout=10) as response:
                body=response.read(262145)
                ok=response.status==200 and len(body)<=262144
                ok=ok and (b'<html' in body.lower() if path=='/' else isinstance(json.loads(body).get('items'),list))
                samples.append({'path':path,'ok':ok,'status':response.status,'seconds':round(time.monotonic()-start,3)})
        except Exception as error:
            samples.append({'path':path,'ok':False,'error':type(error).__name__,'seconds':round(time.monotonic()-start,3)})
    return {'time':time.time(),'ok':all(x['ok'] for x in samples),'checks':samples}

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('url');args=parser.parse_args()
    result=check(args.url);print(json.dumps(result));raise SystemExit(0 if result['ok'] else 1)
