#!/usr/bin/env python3
"""Actual backup/restore and maintenance entrypoints, fixed disposable audit stack only."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile
import time
import urllib.request

ROOT=Path('/home/juan/terra-audit-20260922')
SOURCE=ROOT/'source'
MYSQL='terra-audit-20260922-mysql-1'
BACKEND='terra-audit-20260922-backend-1'
DOCKER=['/snap/docker/current/bin/docker','--host','unix:///var/run/docker.sock']

def run(arguments, **kwargs):
    result=subprocess.run(arguments,capture_output=True,text=True,timeout=kwargs.pop('timeout',180),**kwargs)
    if result.returncode: raise RuntimeError('isolated operation failed: '+arguments[0]+' '+result.stderr[:500])
    return result.stdout.strip()

def sql(query, database='terra_audit_20260922'):
    assert database.startswith('terra_audit_') and database.replace('_','').isalnum()
    return run(DOCKER+['exec','-i',MYSQL,'sh','-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names '+database],input=query)

def counts(database):
    return {table:int(sql('SELECT COUNT(*) FROM '+table,database)) for table in
        ('app_user','food','food_checkin','image_asset','flyway_schema_history')}

def main():
    assert json.loads(run(DOCKER+['inspect',MYSQL,'--format','{{json .Config.Labels}}']))['com.docker.compose.project']=='terra-audit-20260922'
    assert sql('SELECT DATABASE()')=='terra_audit_20260922'
    before=sql('SELECT id,status,attempts,COALESCE(worker_owner,\'\') FROM image_asset ORDER BY id')
    flyway=sql('SELECT COUNT(*) FROM flyway_schema_history')
    output=run(DOCKER+['exec',BACKEND,'java','-Dloader.main=com.dayan.food.image.ImageMaintenance',
        '-cp','/app/app.jar','org.springframework.boot.loader.launch.PropertiesLauncher','--dry-run'])
    assert 'mode=dry-run' in output and 'committed=false' in output and 'failed=0' in output
    assert before==sql('SELECT id,status,attempts,COALESCE(worker_owner,\'\') FROM image_asset ORDER BY id')
    assert flyway==sql('SELECT COUNT(*) FROM flyway_schema_history')
    bad=subprocess.run(DOCKER+['exec','-e','UPLOAD_DIRECTORY=/does-not-exist',BACKEND,'java',
        '-Dloader.main=com.dayan.food.image.ImageMaintenance','-cp','/app/app.jar',
        'org.springframework.boot.loader.launch.PropertiesLauncher','--dry-run'],capture_output=True,timeout=30)
    assert bad.returncode!=0
    os.umask(0o077)
    directory=Path(tempfile.mkdtemp(prefix='restore-drill-',dir=ROOT))
    password=directory/'password';password.write_text(secrets.token_urlsafe(48));password.chmod(0o600)
    archive=directory/'snapshot.enc'
    original=counts('terra_audit_20260922')
    run(['python3',str(SOURCE/'ops/backup.py'),'--mysql-container',MYSQL,'--upload-container',BACKEND,
        '--passphrase-file',str(password),'--output',str(archive)],timeout=360)
    restored=directory/'restored'
    run(['python3',str(SOURCE/'ops/restore_verify.py'),'--backup',str(archive),'--passphrase-file',str(password),
        '--output-directory',str(restored)])
    manifest=json.loads((restored/'manifest.json').read_text())
    assert hashlib.sha256((restored/'database.sql').read_bytes()).hexdigest()==manifest['database_sha256']
    database='terra_audit_restore_'+secrets.token_hex(4)
    sql('CREATE DATABASE '+database)
    try:
        with (restored/'database.sql').open() as data:
            run(DOCKER+['exec','-i',MYSQL,'sh','-c',
                'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot '+database],stdin=data)
        assert counts(database)==original
        sql("GRANT ALL ON "+database+".* TO 'audit_app'@'%'")
        # Boot the actual application image against the restored schema, with all
        # values inherited privately from the disposable backend (never printed).
        inspected=json.loads(run(DOCKER+['inspect',BACKEND]))[0]
        environment={**os.environ, **dict(item.split('=',1) for item in inspected['Config']['Env'] if '=' in item)}
        environment['DB_URL']=environment['DB_URL'].replace('/terra_audit_20260922?', '/'+database+'?')
        environment['INITIAL_ADMIN_ENABLED']='false'
        environment['UPLOAD_VARIANT_PROCESSING_ENABLED']='false'
        application='tf-audit-restored-app'
        command=DOCKER+['run','-d','--name',application,'--network','terra-audit-20260922_default',
            '--cgroup-parent','terra-audit-20260922.slice','--memory','640m','--pids-limit','128','--cap-drop','ALL',
            '-p','127.0.0.1:18384:8080','-v',str(restored/'uploads')+':/data/uploads:ro']
        for key in inspected['Config']['Env']:
            command+=['--env',key.split('=',1)[0]]
        try:
            run(command+[inspected['Image']],env=environment)
            deadline=time.monotonic()+60
            while True:
                try:
                    with urllib.request.urlopen('http://127.0.0.1:18384/api/foods/catalog?page=1&pageSize=1',timeout=3) as response:
                        data=json.load(response)
                    assert isinstance(data.get('items'),list)
                    break
                except Exception:
                    if time.monotonic()>=deadline:raise RuntimeError('restored application did not become ready')
                    time.sleep(2)
        finally:
            run(DOCKER+['rm','-f',application])
        originals=list((restored/'uploads').glob('*'))
        files=[path for path in originals if path.is_file()]
        assert files, 'restore must include actual uploaded originals'
        for file in files:
            expected=run(DOCKER+['exec',BACKEND,'sha256sum','/data/uploads/'+file.name]).split()[0]
            assert hashlib.sha256(file.read_bytes()).hexdigest()==expected
        report={'maintenanceDryRun':'PASS','missingDirectoryNonzero':'PASS','authenticatedBackup':'PASS',
            'isolatedDatabaseRestore':'PASS','restoredApplicationBoot':'PASS','tableCounts':original,'originalFilesVerified':len(files),
            'limitations':['Synthetic audit data only; no live backup or off-host retention installed.']}
        (ROOT/'maintenance-result.json').write_text(json.dumps(report,indent=2)+'\n')
        print(json.dumps(report))
    finally:
        sql('DROP DATABASE '+database)
        # Private encrypted drill artifacts remain for evidence; cleanup is handled with the run directory.

if __name__=='__main__':main()
