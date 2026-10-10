#!/usr/bin/env python3
"""Read-only verification of synthetic upload variants through the actual Nginx image."""
import importlib.util
import json
from pathlib import Path
import time
import urllib.request

root=Path('/home/juan/terra-audit-20260922')
spec=importlib.util.spec_from_file_location('http_fixture',root/'source/security-audit/http-regression.py')
fixture=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixture)
assert fixture.sql('SELECT DATABASE()')==fixture.DATABASE
deadline=time.monotonic()+45
while True:
    waiting=fixture.scalar("SELECT COUNT(*) FROM image_asset WHERE status IN ('PENDING','PROCESSING')")
    if not waiting:break
    if time.monotonic()>deadline:raise RuntimeError('image processing did not finish within budget')
    time.sleep(1)
rows=fixture.sql('SELECT original_url,variant_320_url,variant_640_url,variant_1280_url,status FROM image_asset ORDER BY id').splitlines()
assert rows,'no uploaded assets to verify'
requests=0
for row in rows:
    *urls,status=row.split('\t')
    assert status=='READY','valid synthetic image did not produce variants'
    for url in urls:
        assert url.startswith('/uploads/') and '?' not in url
        with urllib.request.urlopen('http://127.0.0.1:18381'+url,timeout=5) as response:
            data=response.read(100000)
            assert response.status==200 and response.headers['Content-Type'].startswith('image/')
            assert data.startswith(b'\x89PNG\r\n\x1a\n') or data.startswith(b'\xff\xd8')
            assert b'<html' not in data.lower()
        requests+=1
report={'status':'PASS','assets':len(rows),'originalAndVariantRequests':requests,'proxy':'actual isolated Nginx',
    'limitations':['Synthetic 2x2 transparent PNGs; format/timeout/export boundary cases covered by separate Java tests.']}
(root/'image-proxy-result.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
