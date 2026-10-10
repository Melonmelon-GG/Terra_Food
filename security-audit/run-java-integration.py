#!/usr/bin/env python3
"""Mini-host isolated MySQL/Redis checks; credentials stay out of command arguments."""
import os
import sys
from pathlib import Path
import subprocess

root=Path('/home/juan/terra-audit-20260922')
private=dict(line.split('=',1) for line in (root/'runtime.env').read_text().splitlines() if '=' in line)
environment={**os.environ,
    'DB_URL':'jdbc:mysql://127.0.0.1:18306/terra_audit_20260922?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai',
    'DB_USERNAME':'audit_app','DB_PASSWORD':private['AUDIT_DB_PASSWORD'],
    'REDIS_HOST':'127.0.0.1','REDIS_PORT':'18379','REDIS_PASSWORD':'',
    'AUDIT_REDIS_PORT':'18379','RUN_AUDIT_BUSINESS_MYSQL_TESTS':'true',
    'MAVEN_OPTS':'-Xmx192m '+os.environ.get('AUDIT_MAVEN_PROXY_OPTS',''),'INITIAL_ADMIN_ENABLED':'false'}
upgrade='--upgrade' in sys.argv
if upgrade:
    schema='terra_audit_upgrade_20260923'
    subprocess.run(['docker','exec','-i','terra-audit-20260922-mysql-1','sh','-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'],
        input=f"CREATE DATABASE {schema}; GRANT ALL ON {schema}.* TO 'audit_app'@'%';",text=True,check=True)
    environment['MIGRATION_DB_URL']=environment['DB_URL'].replace('terra_audit_20260922',schema)
arguments=['docker' ,'run','--rm','--name','tf-audit-integration','--network','host',
    '--cgroup-parent','terra-audit-20260922.slice','--memory','768m',
    '-v',str(root/'source/dayanfood-backend')+':/work','-v','tf-m2:/root/.m2','-w','/work']
for key in ('DB_URL','DB_USERNAME','DB_PASSWORD','REDIS_HOST','REDIS_PORT','REDIS_PASSWORD','AUDIT_REDIS_PORT',
            'RUN_AUDIT_BUSINESS_MYSQL_TESTS','MAVEN_OPTS','INITIAL_ADMIN_ENABLED','MIGRATION_DB_URL'):
    arguments+=['--env',key]
arguments+=['maven:3.9.11-eclipse-temurin-21','mvn','--batch-mode','--no-transfer-progress','-DargLine=-Xmx384m',
    '-Dtest='+('LegacyOwnershipUpgradeMysqlTests,WishlistServiceImplTests#createDoesNotAcquireSecondConnectionForVersionCache,SecurityBusinessMysqlTests#exactGridBoundaryBelongsToOneCellAndAntimeridianViewportIsConsistent' if upgrade else 'SecurityBusinessMysqlTests,AtomicChallengeRedisTests,ImageSubprocessTests,ImageExportBoundsTests,ExpectedIdentityFilterTests,AbuseBudgetServiceTests'),'test']
if '--all' in sys.argv:
    if upgrade: raise ValueError('upgrade and all are separate runs')
    arguments=[value for value in arguments if not value.startswith('-Dtest=')]
    arguments[-1]='verify'
elif '--startup' in sys.argv:
    arguments=[('-Dtest=DayanFoodApplicationTests' if value.startswith('-Dtest=') else value) for value in arguments]
raise SystemExit(subprocess.run(arguments,env=environment).returncode)
