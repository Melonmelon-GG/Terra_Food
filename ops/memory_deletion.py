#!/usr/bin/env python3
"""Manual outbox drain with all Agent writers stopped; never enables memory or installs jobs."""
import argparse
import json
import os
import re
import subprocess
import urllib.request
import uuid


def sql(container, statement):
    result=subprocess.run(['docker','exec','-i',container,'sh','-c',
        'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -h127.0.0.1 -u "$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names'],
        input=statement,text=True,capture_output=True,timeout=15)
    if result.returncode: raise RuntimeError('DATABASE_OPERATION_FAILED')
    return result.stdout.strip()


def require_stopped_writers(project):
    # A maintenance window is required. A health boolean does not fence an old in-flight writer.
    result=subprocess.run(['docker','ps','-q','--filter','label=com.docker.compose.project='+project,
        '--filter','label=com.docker.compose.service=agent-api'],text=True,capture_output=True,timeout=10,check=True)
    if result.stdout.strip(): raise RuntimeError('STOP_ALL_AGENT_WRITERS_BEFORE_DELETION')
    known=subprocess.run(['docker','ps','-a','-q','--filter','label=com.docker.compose.project='+project,
        '--filter','label=com.docker.compose.service=agent-api'],text=True,capture_output=True,timeout=10,check=True)
    if not known.stdout.strip(): raise RuntimeError('UNKNOWN_AGENT_PROJECT')


def milvus_request(base, operation, payload):
    headers={'Content-Type':'application/json','Request-Timeout':'10'}
    token=os.environ.get('MILVUS_TOKEN','')
    if token: headers['Authorization']='Bearer '+token
    request=urllib.request.Request(base.rstrip('/')+'/v2/vectordb/entities/'+operation,
        data=json.dumps(payload).encode(),headers=headers,method='POST')
    with urllib.request.urlopen(request,timeout=12) as response:
        raw=response.read(65537)
        if len(raw)>65536: raise RuntimeError('INVALID_EXTERNAL_RESPONSE')
        result=json.loads(raw)
    if result.get('code')!=0: raise RuntimeError('EXTERNAL_DELETE_FAILED')
    return result


def delete_subject(base,collection,subject):
    # Never infer ownership from reusable usernames; malformed subject cannot alter the filter.
    subject=str(uuid.UUID(subject))
    payload={'collectionName':collection,'filter':'subject_id == "'+subject+'"'}
    milvus_request(base,'delete',payload)
    result=milvus_request(base,'query',{**payload,'outputFields':['subject_id'],'limit':1,'consistencyLevel':'Strong'})
    if result.get('data')!=[]: raise RuntimeError('EXTERNAL_RECORDS_REMAIN')


def drain(container,project,base,collection,batch):
    completed=failed=0
    require_stopped_writers(project)
    sql(container,"UPDATE external_identity_deletion SET status=IF(attempts>=8,'FAILED','PENDING'),worker_owner=NULL,lease_until=NULL,last_error_code='LEASE_EXPIRED' WHERE status='PROCESSING' AND lease_until<NOW()")
    for _ in range(batch):
        require_stopped_writers(project)
        owner=str(uuid.uuid4())
        sql(container,f"UPDATE external_identity_deletion SET status='PROCESSING',worker_owner='{owner}',lease_until=DATE_ADD(NOW(),INTERVAL 90 SECOND),attempts=attempts+1 WHERE status='PENDING' AND target='AGENT_MEMORY' AND next_attempt_at<=NOW() AND attempts<8 ORDER BY id LIMIT 1")
        row=sql(container,f"SELECT id,subject_id FROM external_identity_deletion WHERE worker_owner='{owner}' AND status='PROCESSING'")
        if not row: break
        identity,subject=row.split('\t')
        identity=int(identity)
        try:
            subject=str(uuid.UUID(subject))
            if sql(container,f"SELECT COUNT(*) FROM app_user WHERE subject_id='{subject}'")!='0':
                raise RuntimeError('SUBJECT_STILL_EXISTS')
            delete_subject(base,collection,subject)
            require_stopped_writers(project)
            changed=sql(container,f"UPDATE external_identity_deletion SET status='DONE',completed_at=NOW(),worker_owner=NULL,lease_until=NULL,last_error_code=NULL WHERE id={identity} AND worker_owner='{owner}' AND lease_until>NOW(); SELECT ROW_COUNT()")
            if changed!='1': raise RuntimeError('LEASE_LOST')
            completed+=1
        except Exception as error:
            # Log category only: HTTP exceptions may contain endpoint/response details.
            category=str(error) if str(error) in {'SUBJECT_STILL_EXISTS','LEASE_LOST','EXTERNAL_RECORDS_REMAIN'} else 'EXTERNAL_UNAVAILABLE'
            sql(container,f"UPDATE external_identity_deletion SET status=IF(attempts>=8,'FAILED','PENDING'),worker_owner=NULL,lease_until=NULL,next_attempt_at=DATE_ADD(NOW(),INTERVAL 5 MINUTE),last_error_code='{category}' WHERE id={identity} AND worker_owner='{owner}'")
            failed+=1
    return {'completed':completed,'failed':failed}


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--mysql-container',required=True)
    parser.add_argument('--agent-project',required=True)
    parser.add_argument('--milvus-url',required=True)
    parser.add_argument('--collection',default='terra_agent_memory')
    parser.add_argument('--batch',type=int,default=10)
    parser.add_argument('--execute',action='store_true')
    args=parser.parse_args()
    if not 1<=args.batch<=100: raise ValueError('batch must be 1..100')
    if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]{0,127}',args.collection):raise ValueError('invalid collection')
    if not args.execute:
        print(sql(args.mysql_container,"SELECT status,COUNT(*) FROM external_identity_deletion WHERE target='AGENT_MEMORY' GROUP BY status"))
        return
    result=drain(args.mysql_container,args.agent_project,args.milvus_url,args.collection,args.batch)
    print(json.dumps(result))
    if result['failed']:raise SystemExit(1)


if __name__=='__main__':main()
