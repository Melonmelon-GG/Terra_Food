#!/usr/bin/env python3
"""Real MySQL lease/outbox with deterministic HTTP substitute; never contacts Milvus."""
import importlib.util
import json
from pathlib import Path
import subprocess
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import uuid

ROOT=Path('/home/juan/terra-audit-20260922')
spec=importlib.util.spec_from_file_location('memory_worker',ROOT/'source/ops/memory_deletion.py')
worker=importlib.util.module_from_spec(spec);spec.loader.exec_module(worker)
MYSQL='terra-audit-20260922-mysql-1'
PROJECT='terra-audit-memory-fixture'
CONTAINER='tf-audit-memory-writer-fixture'

class Handler(BaseHTTPRequestHandler):
    subjects=set()
    requests=[]
    def log_message(self,*args):pass
    def do_POST(self):
        payload=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        subject=payload['filter'].split('"')[1]
        self.requests.append(self.path)
        if self.path.endswith('/delete'):
            self.subjects.discard(subject);data={'deleteCount':1}
        elif self.path.endswith('/query'):
            assert payload['consistencyLevel']=='Strong'
            data=[{'subject_id':subject}] if subject in self.subjects else []
        else:self.send_error(404);return
        body=json.dumps({'code':0,'data':data}).encode()
        self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(body)

def main():
    assert worker.sql(MYSQL,'SELECT DATABASE()')=='terra_audit_20260922'
    subject=str(uuid.uuid4());Handler.subjects.add(subject)
    worker.sql(MYSQL,"INSERT INTO external_identity_deletion(subject_id,target) VALUES ('"+subject+"','AGENT_MEMORY')")
    # No running writer; the CLI must recognize the explicitly stopped fixture project.
    subprocess.run(['docker','create','--name',CONTAINER,'--label','com.docker.compose.project='+PROJECT,
        '--label','com.docker.compose.service=agent-api','redis:7-alpine','true'],check=True,stdout=subprocess.DEVNULL)
    server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
    thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    try:
        result=worker.drain(MYSQL,PROJECT,'http://127.0.0.1:'+str(server.server_port),'fixture_memory',100)
        assert result['failed']==0 and result['completed']>=1
        assert worker.sql(MYSQL,"SELECT status FROM external_identity_deletion WHERE subject_id='"+subject+"'")=='DONE'
        assert subject not in Handler.subjects
        assert len(Handler.requests)==result['completed']*2
        report={'status':'PASS','realMysqlLease':True,'external':'deterministic local HTTP substitute',
            'completed':result['completed'],'deleteAndStrongVerifyRequests':len(Handler.requests),
            'limitations':['Actual Milvus version and legacy username-only records not validated.']}
        (ROOT/'memory-result.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
    finally:
        server.shutdown();server.server_close();thread.join(timeout=5)
        subprocess.run(['docker','rm',CONTAINER],check=True,stdout=subprocess.DEVNULL)

if __name__=='__main__':main()
