#!/usr/bin/env python3
"""Disposable mini-host build workaround. Same hashes and TLS; no host route changes."""
import importlib.util
from pathlib import Path
import subprocess
import threading
import argparse
import os

root=Path('/home/juan/terra-audit-20260922/source')
parser=argparse.ArgumentParser();parser.add_argument('--backend',action='store_true');parser.add_argument('--java-tests',action='store_true');args=parser.parse_args()
spec=importlib.util.spec_from_file_location('relay',root/'security-audit/build-egress-proxy.py')
relay=importlib.util.module_from_spec(spec);spec.loader.exec_module(relay)
docker=['docker']  # Snap wrapper exposes its BuildKit plugin; raw CLI falls back to legacy builder.
with relay.Server(('127.0.0.1',18889),relay.Relay) as server:
    server.device='enp1s0'
    thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    try:
        if args.java_tests:
            subprocess.run(['python3',str(root/'security-audit/run-java-integration.py'),'--all'],check=True,timeout=1200,
                env={**os.environ,'AUDIT_MAVEN_PROXY_OPTS':'-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=18889'})
        elif args.backend:
            subprocess.run(docker+['build','--network','host','--build-arg',
                'MAVEN_OPTS=-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=18889',
                '-f',str(root/'ops/Dockerfile.backend'),'-t','terra-audit-20260922-backend',str(root)],check=True,timeout=1200)
        else:
            subprocess.run(docker+['build','--network','host','--build-arg','HTTPS_PROXY=http://127.0.0.1:18889',
            '--build-arg','PIP_INDEX_URL=https://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple',
            '-t','terra-audit-20260922-agent-api',str(root/'agent-service')],check=True,timeout=1200)
            subprocess.run(docker+['tag','terra-audit-20260922-agent-api','terra-audit-20260922-agent-mcp'],check=True,timeout=30)
    finally:
        server.shutdown();thread.join(timeout=5)
