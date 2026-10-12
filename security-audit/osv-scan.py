#!/usr/bin/env python3
"""Query public package coordinates only; scan errors never become a clean result."""
import argparse
import json
from pathlib import Path
import urllib.request

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--inventory',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    packages=json.loads(args.inventory.read_text())['components']
    findings=[];errors=[];checked=0
    for start in range(0,len(packages),50):
        batch=packages[start:start+50]
        payload={'queries':[{'package':{'purl':item['purl']}} for item in batch]}
        request=urllib.request.Request('https://api.osv.dev/v1/querybatch',data=json.dumps(payload).encode(),headers={'Content-Type':'application/json'})
        try:
            with urllib.request.urlopen(request,timeout=45) as response:body=json.load(response)
            results=body['results']
            if len(results)!=len(batch):raise ValueError('incomplete result')
            for package,result in zip(batch,results):
                checked+=1
                if result.get('vulns'):findings.append({'purl':package['purl'],'advisories':result['vulns']})
        except Exception as error:errors.append({'offset':start,'count':len(batch),'category':type(error).__name__})
    report={'source':'https://api.osv.dev/v1/querybatch','checked':checked,'required':len(packages),
        'findings':findings,'errors':errors,'limitations':['Package matching is not exploitability analysis.',
            'OS packages and Java entries without resolvable Maven coordinates require separate review.']}
    args.output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({'checked':checked,'findings':len(findings),'errors':len(errors)}))
    raise SystemExit(2 if errors or checked!=len(packages) else 1 if findings else 0)

if __name__=='__main__':main()
